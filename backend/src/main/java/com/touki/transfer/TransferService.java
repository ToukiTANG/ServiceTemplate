package com.touki.transfer;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.touki.common.BizException;
import com.touki.common.ErrorCode;
import com.touki.common.PageResult;
import com.touki.config.TransferProperties;
import com.touki.transfer.dto.InitUploadDTO;
import com.touki.transfer.dto.TransferQueryDTO;
import com.touki.transfer.vo.TransferAdminVO;
import com.touki.transfer.vo.TransferFileVO;
import com.touki.transfer.vo.UploadCompleteVO;
import com.touki.transfer.vo.UploadInitVO;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 文件传输业务。
 *
 * <p>整个流程分四步，对应四个方法：
 * <pre>
 * init()        前端报文件信息 → 返回 uploadId + 已上传分片（断点续传）
 * uploadChunk() 逐个传分片
 * complete()    校验分片齐全 → 合并 → 计算哈希 → 标记可用 → 返回提取码
 * getInfo() / download()  对方凭提取码查看与下载
 * </pre>
 *
 * <p>核心约束：单文件 500MB、分片 5MB、提取码 6 位、有效期 24h（可选 7 天）。
 * 这些值都来自 {@link TransferProperties}，不写死在代码里。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TransferService {

    /**
     * 提取码字符集。
     *
     * <p>去掉了容易混淆的 0 O 1 l I——提取码是要人念给人听、手输到另一台设备上的，
     * 分不清 0 和 O 会造成大量"码不对"的误报。
     */
    private static final String CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789";

    /** 提取码长度 */
    private static final int CODE_LENGTH = 6;

    /** 生成提取码用。用 SecureRandom 而非 Random：提取码是唯一的访问凭证，可预测等于没有 */
    private static final SecureRandom RANDOM = new SecureRandom();

    /** 清理任务每批最多处理多少条，避免一次扫太多把数据库压住 */
    private static final int CLEANUP_BATCH = 200;

    private final TransferFileMapper fileMapper;
    private final TransferStorage storage;
    private final TransferProperties properties;

    // ==================== 上传 ====================

    /**
     * 初始化上传。
     *
     * <p>做三件事：校验大小、算分片数、判断能不能续传。
     *
     * @param dto 文件元信息
     * @return uploadId、已上传分片、分片大小
     * @throws BizException 文件超过上限（3002）
     */
    @Transactional(rollbackFor = Exception.class)
    public UploadInitVO init(InitUploadDTO dto) {
        if (dto.getFileSize() > properties.getMaxFileSize()) {
            throw new BizException(ErrorCode.TRANSFER_TOO_LARGE,
                    "文件大小超过上限 " + formatSize(properties.getMaxFileSize()));
        }

        long chunkSize = properties.getChunkSize();
        // 向上取整算分片数：500MB / 5MB = 100 片
        int totalChunks = (int) ((dto.getFileSize() + chunkSize - 1) / chunkSize);

        // 断点续传：同一个文件（hash + 大小都相同）之前传过一半，就复用那条记录
        if (StringUtils.hasText(dto.getFileHash())) {
            TransferFile existing = fileMapper.selectOne(Wrappers.<TransferFile>lambdaQuery()
                    .eq(TransferFile::getFileHash, dto.getFileHash())
                    .eq(TransferFile::getFileSize, dto.getFileSize())
                    .eq(TransferFile::getStatus, TransferFile.STATUS_UPLOADING)
                    .orderByDesc(TransferFile::getCreateTime)
                    .last("LIMIT 1"));

            if (existing != null) {
                String uploadId = String.valueOf(existing.getId());
                List<Integer> uploaded = storage.uploadedChunks(uploadId, totalChunks);
                log.info("断点续传: uploadId={}, 已完成 {}/{} 片", uploadId, uploaded.size(), totalChunks);
                return new UploadInitVO(uploadId, uploaded, chunkSize, totalChunks);
            }
        }

        // 新建记录。先预生成 ID，是为了让 storagePath 能一次写对，
        // 不用 insert 之后再 update 一遍（那样多一次无谓的写库）
        long id = IdWorker.getId();
        TransferFile file = new TransferFile();
        file.setId(id);
        file.setFileName(sanitizeFileName(dto.getFileName()));
        file.setFileSize(dto.getFileSize());
        file.setFileHash(dto.getFileHash());
        file.setContentType(dto.getContentType());
        file.setCode(generateCode());
        file.setExpireTime(LocalDateTime.now().plusHours(resolveExpireHours(dto.getExpireHours())));
        file.setDownloadCount(0);
        file.setStatus(TransferFile.STATUS_UPLOADING);
        // 上传期间先指向临时目录，合并完成后改成正式路径
        file.setStoragePath("tmp/" + id);
        fileMapper.insert(file);

        log.info("开始上传: uploadId={}, name={}, size={}, chunks={}",
                id, file.getFileName(), dto.getFileSize(), totalChunks);
        return new UploadInitVO(String.valueOf(id), List.of(), chunkSize, totalChunks);
    }

    /**
     * 接收单个分片。
     *
     * @param uploadId 上传会话 ID
     * @param index    分片序号，从 0 开始
     * @param file     分片内容
     * @throws BizException 会话失效（3004）或序号越界（400）
     */
    public void uploadChunk(String uploadId, int index, MultipartFile file) {
        TransferFile record = getUploading(uploadId);

        long chunkSize = properties.getChunkSize();
        int totalChunks = (int) ((record.getFileSize() + chunkSize - 1) / chunkSize);
        if (index < 0 || index >= totalChunks) {
            throw new BizException(ErrorCode.PARAM_INVALID, "分片序号越界");
        }

        storage.saveChunk(uploadId, index, file);
    }

    /**
     * 完成上传：合并分片、校验、生成提取码。
     *
     * @param uploadId 上传会话 ID
     * @return 提取码与有效期
     * @throws BizException 分片不齐（3003）或会话失效（3004）
     */
    @Transactional(rollbackFor = Exception.class)
    public UploadCompleteVO complete(String uploadId) {
        TransferFile record = getUploading(uploadId);

        long chunkSize = properties.getChunkSize();
        int totalChunks = (int) ((record.getFileSize() + chunkSize - 1) / chunkSize);

        // 合并前先确认分片齐全，别等合并到一半才发现少一片
        List<Integer> uploaded = storage.uploadedChunks(uploadId, totalChunks);
        if (uploaded.size() != totalChunks) {
            throw new BizException(ErrorCode.TRANSFER_CHUNK_MISSING,
                    "分片不完整（" + uploaded.size() + "/" + totalChunks + "），请继续上传");
        }

        String relative = storage.merge(uploadId, totalChunks, chunkSize, record.getId());
        Path merged = storage.baseDir().resolve(relative);

        try {
            // 校验合并后的字节数。这是本流程唯一的内容完整性检查，原因见类注释：
            // 前端无法为几百 MB 的文件计算真实 SHA-256（Web Crypto 要求整个文件进内存），
            // 所以 fileHash 存的是"弱指纹"，只能用于断点续传识别，不能当摘要用。
            long actualSize = Files.size(merged);
            if (actualSize != record.getFileSize()) {
                storage.deleteQuietly(merged);
                log.warn("合并后大小不一致: uploadId={}, 期望={}, 实际={}",
                        uploadId, record.getFileSize(), actualSize);
                throw new BizException(ErrorCode.TRANSFER_CHUNK_MISSING);
            }
        } catch (IOException e) {
            storage.deleteQuietly(merged);
            throw new BizException(ErrorCode.TRANSFER_STORAGE_ERROR);
        }

        TransferFile update = new TransferFile();
        update.setId(record.getId());
        update.setStoragePath(relative);
        update.setStatus(TransferFile.STATUS_READY);
        fileMapper.updateById(update);

        // 合并成功后临时分片就没用了，及时删掉释放磁盘
        storage.deleteTmpDir(uploadId);

        log.info("上传完成: code={}, name={}, size={}", record.getCode(), record.getFileName(), record.getFileSize());
        return new UploadCompleteVO(record.getCode(), record.getFileName(),
                record.getFileSize(), record.getExpireTime());
    }

    // ==================== 下载 ====================

    /**
     * 查询文件信息（公开接口，凭提取码）。
     *
     * @param code 提取码
     * @return 文件名、大小、过期时间
     * @throws BizException 文件不存在或已过期（3001）
     */
    public TransferFileVO getInfo(String code) {
        TransferFile file = requireAvailable(code);
        TransferFileVO vo = new TransferFileVO();
        vo.setFileName(file.getFileName());
        vo.setFileSize(file.getFileSize());
        vo.setExpireTime(file.getExpireTime());
        vo.setDownloadCount(file.getDownloadCount());
        return vo;
    }

    /**
     * 下载文件。
     *
     * <p>直接往响应流里写，不经过 {@code R<T>} 包装——文件流套 JSON 包装没有任何意义。
     *
     * @param code     提取码
     * @param response HTTP 响应
     * @throws BizException 文件不存在或已过期（3001）、磁盘文件丢失（3007）
     */
    public void download(String code, HttpServletResponse response) {
        TransferFile file = requireAvailable(code);
        Path path = storage.resolveSafe(file.getStoragePath());

        try {
            long size = Files.size(path);

            // 下载一律用 application/octet-stream，**不信任**上传时客户端声明的 MIME。
            // 原因：如果对方把文件声明成 text/html 或 image/svg+xml，浏览器有可能会
            // 就地渲染而不是下载——HTML 里的脚本就在本站域名下执行了（存储型 XSS）。
            // 虽然 Content-Disposition: attachment 通常能拦住，但那只是"指望浏览器守规矩"，
            // 换成 octet-stream 是从源头掐掉这种可能。
            response.setContentType(MediaType.APPLICATION_OCTET_STREAM_VALUE);
            response.setContentLengthLong(size);
            response.setHeader("Content-Disposition", buildContentDisposition(file.getFileName()));
            // 另外禁掉内容嗅探：部分浏览器会忽略 Content-Type、按内容猜成 HTML。
            // 加上这个头之后，猜也不猜，直接当二进制下载。
            response.setHeader("X-Content-Type-Options", "nosniff");

            try (InputStream in = Files.newInputStream(path)) {
                in.transferTo(response.getOutputStream());
                response.flushBuffer();
            }

            // 下载次数 +1：用 SQL 自增而不是"查出来 +1 再写回"，
            // 避免两个人同时下载时互相覆盖计数
            fileMapper.update(null, Wrappers.<TransferFile>lambdaUpdate()
                    .eq(TransferFile::getId, file.getId())
                    .setSql("download_count = download_count + 1"));
        } catch (IOException e) {
            // 客户端中途取消下载也会抛 IOException，此时响应已提交，
            // 不能再抛业务异常（会变成"响应已提交"的二次错误），只记日志
            if (response.isCommitted()) {
                log.warn("下载中断: code={}, {}", code, e.getMessage());
            } else {
                log.error("下载失败: code={}", code, e);
                throw new BizException(ErrorCode.TRANSFER_STORAGE_ERROR);
            }
        }
    }

    // ==================== 管理端 ====================

    /**
     * 管理端分页查询。
     *
     * @param query 文件名模糊 + 状态筛选
     * @return 分页结果
     */
    public PageResult<TransferAdminVO> page(TransferQueryDTO query) {
        LambdaQueryWrapper<TransferFile> wrapper = Wrappers.<TransferFile>lambdaQuery()
                .like(StringUtils.hasText(query.getFileName()), TransferFile::getFileName, query.getFileName())
                .eq(query.getStatus() != null, TransferFile::getStatus, query.getStatus())
                .orderByDesc(TransferFile::getCreateTime);

        Page<TransferFile> page = new Page<>(query.getPageNum(), query.getPageSize());
        Page<TransferFile> result = fileMapper.selectPage(page, wrapper);

        List<TransferAdminVO> list = result.getRecords().stream().map(this::toAdminVO).toList();
        return PageResult.of(list, result.getTotal(), query);
    }

    /**
     * 管理端删除：文件与记录一起删。
     *
     * @param id 记录 ID
     * @throws BizException 记录不存在（3001）
     */
    @Transactional(rollbackFor = Exception.class)
    public void remove(Long id) {
        TransferFile file = fileMapper.selectById(id);
        if (file == null) {
            throw new BizException(ErrorCode.TRANSFER_NOT_FOUND);
        }

        if (file.getStatus() == TransferFile.STATUS_READY) {
            try {
                storage.deleteQuietly(storage.resolveSafe(file.getStoragePath()));
            } catch (BizException e) {
                // 磁盘上文件已经不在了（比如手工删过），不影响删记录
                log.warn("删除时文件已不存在: id={}", id);
            }
        }
        // 上传中的记录还要清理临时分片
        storage.deleteTmpDir(String.valueOf(id));

        fileMapper.deleteById(id);
        log.info("删除传输记录: id={}, code={}, name={}", id, file.getCode(), file.getFileName());
    }

    // ==================== 定时清理 ====================

    /**
     * 定时清理过期文件与僵尸上传。
     *
     * <p>每 10 分钟跑一次，首次延迟 1 分钟（避免和应用启动抢资源）。
     * 分两类：
     * <ol>
     *   <li>已过期的可用文件：删文件 + 删记录</li>
     *   <li>超过 24 小时还停在上传中的记录：只删记录和临时分片（文件从没合并过，不存在）</li>
     * </ol>
     *
     * <p>每批最多处理 200 条：盘上可能积压几千个文件，
     * 一次全删会让 IO 长时间占满，拖慢正常请求。
     */
    @Scheduled(fixedDelay = 10 * 60 * 1000, initialDelay = 60 * 1000)
    public void cleanup() {
        LocalDateTime now = LocalDateTime.now();

        // 1. 过期文件
        List<TransferFile> expired = fileMapper.selectList(Wrappers.<TransferFile>lambdaQuery()
                .eq(TransferFile::getStatus, TransferFile.STATUS_READY)
                .lt(TransferFile::getExpireTime, now)
                .last("LIMIT " + CLEANUP_BATCH));

        for (TransferFile file : expired) {
            try {
                storage.deleteQuietly(storage.baseDir().resolve(file.getStoragePath()).normalize());
                fileMapper.deleteById(file.getId());
            } catch (Exception e) {
                log.warn("清理过期文件失败: id={}, path={}", file.getId(), file.getStoragePath(), e);
            }
        }
        if (!expired.isEmpty()) {
            log.info("清理过期文件: {} 个", expired.size());
        }

        // 2. 僵尸上传（超过 24h 没传完）
        List<TransferFile> stale = fileMapper.selectList(Wrappers.<TransferFile>lambdaQuery()
                .eq(TransferFile::getStatus, TransferFile.STATUS_UPLOADING)
                .lt(TransferFile::getCreateTime, now.minusHours(24))
                .last("LIMIT " + CLEANUP_BATCH));

        for (TransferFile file : stale) {
            try {
                storage.deleteTmpDir(String.valueOf(file.getId()));
                fileMapper.deleteById(file.getId());
            } catch (Exception e) {
                log.warn("清理僵尸上传失败: id={}", file.getId(), e);
            }
        }
        if (!stale.isEmpty()) {
            log.info("清理僵尸上传: {} 个", stale.size());
        }
    }

    // ==================== 私有方法 ====================

    /**
     * 按 uploadId 取"上传中"的记录。
     *
     * @param uploadId 上传会话 ID（就是记录 ID 的字符串）
     * @return 记录
     * @throws BizException 会话不存在或已完成（3004）
     */
    private TransferFile getUploading(String uploadId) {
        Long id;
        try {
            id = Long.valueOf(uploadId);
        } catch (NumberFormatException e) {
            throw new BizException(ErrorCode.TRANSFER_SESSION_INVALID);
        }

        TransferFile file = fileMapper.selectById(id);
        if (file == null || file.getStatus() != TransferFile.STATUS_UPLOADING) {
            throw new BizException(ErrorCode.TRANSFER_SESSION_INVALID);
        }
        return file;
    }

    /**
     * 按提取码取"可下载"的记录。
     *
     * <p>三种失败（不存在 / 未完成 / 已过期）统一返回同一个错误码，
     * 对外只说"文件不存在或已过期"——区分开没有好处，反而方便别人试探。
     *
     * @param code 提取码
     * @return 记录
     * @throws BizException 3001
     */
    private TransferFile requireAvailable(String code) {
        TransferFile file = fileMapper.selectOne(Wrappers.<TransferFile>lambdaQuery()
                .eq(TransferFile::getCode, code));

        if (file == null
                || file.getStatus() != TransferFile.STATUS_READY
                || file.getExpireTime() == null
                || file.getExpireTime().isBefore(LocalDateTime.now())) {
            throw new BizException(ErrorCode.TRANSFER_NOT_FOUND);
        }
        return file;
    }

    /**
     * 生成不重复的提取码。
     *
     * <p>6 位、字符集 57 个字符，空间约 3.4×10^10，撞码概率极低；
     * 仍然查库确认，最多重试 10 次，避免极端情况下死循环。
     *
     * @return 提取码
     * @throws BizException 连续 10 次都撞码（几乎不可能）
     */
    private String generateCode() {
        for (int attempt = 0; attempt < 10; attempt++) {
            StringBuilder sb = new StringBuilder(CODE_LENGTH);
            for (int i = 0; i < CODE_LENGTH; i++) {
                sb.append(CODE_CHARS.charAt(RANDOM.nextInt(CODE_CHARS.length())));
            }
            String code = sb.toString();

            Long exists = fileMapper.selectCount(Wrappers.<TransferFile>lambdaQuery()
                    .eq(TransferFile::getCode, code));
            if (exists == null || exists == 0) {
                return code;
            }
        }
        throw new BizException(ErrorCode.SYSTEM_ERROR, "生成提取码失败，请重试");
    }

    /**
     * 解析有效期：为空用默认值，超过上限截断。
     *
     * @param hours 前端传的小时数
     * @return 实际生效的小时数
     */
    private int resolveExpireHours(Integer hours) {
        if (hours == null || hours <= 0) {
            return properties.getDefaultExpireHours();
        }
        return Math.min(hours, properties.getMaxExpireHours());
    }

    /**
     * 清洗文件名：去掉路径分隔符与控制字符。
     *
     * <p>文件名最终会写进 {@code Content-Disposition} 响应头，
     * 带换行的文件名会造成响应头注入，必须清掉。
     *
     * @param name 原始文件名
     * @return 安全文件名
     */
    private String sanitizeFileName(String name) {
        String cleaned = name.replaceAll("[\\\\/:*?\"<>|\\r\\n]", "_").trim();
        if (!StringUtils.hasText(cleaned)) {
            cleaned = "file";
        }
        if (cleaned.length() > 200) {
            // 保留尾部（通常是扩展名），截掉前面多余的部分
            cleaned = cleaned.substring(cleaned.length() - 200);
        }
        return cleaned;
    }

    /**
     * 构造 Content-Disposition，支持中文文件名。
     *
     * <p>用 RFC 5987 的 {@code filename*} 形式，并把空格转成 %20
     * （URLEncoder 会把空格变成 +，那是表单编码的规则，放在这里会导致文件名带加号）。
     *
     * @param fileName 文件名
     * @return 响应头值
     */
    private String buildContentDisposition(String fileName) {
        String encoded = URLEncoder.encode(fileName, StandardCharsets.UTF_8).replace("+", "%20");
        return "attachment; filename=\"" + encoded + "\"; filename*=UTF-8''" + encoded;
    }

    /**
     * 把字节数格式化成人类可读的大小，用于错误提示。
     *
     * @param bytes 字节数
     * @return 如 "500 MB"
     */
    private String formatSize(long bytes) {
        if (bytes >= 1024L * 1024 * 1024) {
            return (bytes / (1024L * 1024 * 1024)) + " GB";
        }
        return (bytes / (1024L * 1024)) + " MB";
    }

    /**
     * 实体转管理端 VO。
     *
     * @param file 实体
     * @return 出参
     */
    private TransferAdminVO toAdminVO(TransferFile file) {
        TransferAdminVO vo = new TransferAdminVO();
        vo.setId(file.getId());
        vo.setFileName(file.getFileName());
        vo.setFileSize(file.getFileSize());
        vo.setCode(file.getCode());
        vo.setExpireTime(file.getExpireTime());
        vo.setDownloadCount(file.getDownloadCount());
        vo.setStatus(file.getStatus());
        vo.setCreateTime(file.getCreateTime());
        return vo;
    }
}
