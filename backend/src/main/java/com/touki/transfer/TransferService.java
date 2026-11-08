package com.touki.transfer;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.touki.common.BizException;
import com.touki.common.PageResult;
import com.touki.config.TransferProperties;
import com.touki.storage.StorageClient;
import com.touki.storage.UploadFile;
import com.touki.storage.UploadFileMapper;
import com.touki.transfer.dto.TransferCreateDTO;
import com.touki.transfer.dto.TransferQueryDTO;
import com.touki.transfer.vo.TransferAdminVO;
import com.touki.transfer.vo.TransferFileVO;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 文件传输业务：把"已上传的文件"包装成一次可分享的传输。
 *
 * <p>改造后的职责边界很清楚：
 * <ul>
 *   <li><b>上传</b>交给脚手架的 {@code /api/upload/*}（前端直传又拍云）</li>
 *   <li><b>本类</b>只做三件事：生成提取码、记录有效期、按码查文件</li>
 *   <li><b>下载</b>返回 302 重定向到对象存储，文件流不经过服务器</li>
 * </ul>
 *
 * <p>之所以这么拆：服务器公网带宽只有几 Mbps，几百 MB 的文件经中转，
 * 上传下载各要二十多分钟，期间其他请求全部排队。直传 + 直链是唯一可行方案。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TransferService {

    /**
     * 提取码字符集。
     *
     * <p>去掉容易混淆的 0 O 1 l I——提取码是要人念给人听、手输到另一台设备上的。
     */
    private static final String CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789";

    /** 提取码长度 */
    private static final int CODE_LENGTH = 6;

    /** 生成提取码用。SecureRandom 而非 Random：提取码是唯一的访问凭证 */
    private static final SecureRandom RANDOM = new SecureRandom();

    /** 清理任务每批最多处理多少条 */
    private static final int CLEANUP_BATCH = 100;

    private final TransferFileMapper transferMapper;
    private final UploadFileMapper uploadFileMapper;
    private final StorageClient storageClient;
    private final TransferProperties properties;

    // ==================== 创建与查询 ====================

    /**
     * 创建传输记录。
     *
     * <p>前提是文件已经通过 {@code /api/upload/complete} 标记为可用。
     *
     * @param dto 文件 ID 与期望有效期
     * @return 提取码与直链
     * @throws BizException 文件不存在或尚未上传完成（3003）
     */
    @Transactional(rollbackFor = Exception.class)
    public TransferFileVO create(TransferCreateDTO dto) {
        UploadFile file = uploadFileMapper.selectById(dto.getUploadFileId());
        if (file == null || file.getStatus() != UploadFile.STATUS_READY) {
            throw new BizException(TransferError.UPLOAD_NOT_READY);
        }

        // 同一个文件只允许挂一条传输记录（数据库有唯一键）。已经分享过就直接复用，
        // 而不是报错——用户重复点"创建"不该看到红色提示。
        TransferFile existing = transferMapper.selectOne(Wrappers.<TransferFile>lambdaQuery()
                .eq(TransferFile::getUploadFileId, file.getId())
                .last("LIMIT 1"));

        if (existing != null && isAvailable(existing)) {
            log.info("复用已有传输记录: code={}, uploadFileId={}", existing.getCode(), file.getId());
            return toVO(existing, file);
        }

        TransferFile transfer = new TransferFile();
        transfer.setUploadFileId(file.getId());
        transfer.setCode(generateCode());
        transfer.setExpireTime(LocalDateTime.now().plusHours(resolveExpireHours(dto.getExpireHours())));
        transfer.setDownloadCount(0);
        transfer.setStatus(TransferFile.STATUS_READY);
        transferMapper.insert(transfer);

        log.info("创建传输记录: code={}, name={}, size={}, expire={}",
                transfer.getCode(), file.getFileName(), file.getFileSize(), transfer.getExpireTime());
        return toVO(transfer, file);
    }

    /**
     * 按提取码查询文件信息（公开接口）。
     *
     * @param code 提取码
     * @return 文件名、大小、直链、过期时间
     * @throws BizException 不存在或已过期（3001）
     */
    public TransferFileVO getInfo(String code) {
        TransferPair pair = loadAvailable(code);
        return toVO(pair.transfer(), pair.file());
    }

    /**
     * 下载：302 重定向到对象存储的直链。
     *
     * <p>不代理文件流——那会把几百 MB 流量压回服务器。重定向之后，
     * 浏览器直接从又拍云拉数据，服务器的带宽和 CPU 都不参与。
     *
     * <p>顺带把下载次数 +1：用 SQL 自增而不是"查出来加一再写回"，
     * 避免两个人同时下载时互相覆盖计数。
     *
     * @param code     提取码
     * @param response HTTP 响应
     * @throws BizException 不存在或已过期（3001）
     */
    public void download(String code, HttpServletResponse response) {
        TransferPair pair = loadAvailable(code);
        String url = storageClient.publicUrl(pair.file().getStorageKey());

        transferMapper.update(null, Wrappers.<TransferFile>lambdaUpdate()
                .eq(TransferFile::getId, pair.transfer().getId())
                .setSql("download_count = download_count + 1"));

        response.setStatus(HttpServletResponse.SC_FOUND);
        response.setHeader("Location", url);
        log.info("重定向下载: code={} -> {}", code, url);
    }

    // ==================== 管理端 ====================

    /**
     * 管理端分页查询。
     *
     * <p>文件名在关联表里，没法直接用 SQL 的 like 过滤（这里不走 join），
     * 所以先按状态筛选分页，再把当前页的文件信息一次性批量查出来填充——
     * 避免 N+1 查询。
     *
     * @param query 状态筛选与分页参数
     * @return 分页结果
     */
    public PageResult<TransferAdminVO> page(TransferQueryDTO query) {
        LambdaQueryWrapper<TransferFile> wrapper = Wrappers.<TransferFile>lambdaQuery()
                .eq(query.getStatus() != null, TransferFile::getStatus, query.getStatus())
                .orderByDesc(TransferFile::getCreateTime);

        Page<TransferFile> page = new Page<>(query.getPageNum(), query.getPageSize());
        Page<TransferFile> result = transferMapper.selectPage(page, wrapper);
        List<TransferFile> records = result.getRecords();

        if (records.isEmpty()) {
            return PageResult.of(new ArrayList<>(), result.getTotal(), query);
        }

        // 批量取文件信息，避免逐条查库
        List<Long> fileIds = records.stream().map(TransferFile::getUploadFileId).toList();
        Map<Long, UploadFile> fileMap = uploadFileMapper.selectByIds(fileIds).stream()
                .collect(Collectors.toMap(UploadFile::getId, Function.identity()));

        List<TransferAdminVO> list = records.stream()
                .map(t -> toAdminVO(t, fileMap.get(t.getUploadFileId())))
                .toList();
        return PageResult.of(list, result.getTotal(), query);
    }

    /**
     * 删除传输记录，同时删除对象存储上的文件。
     *
     * <p>对象删除失败不阻断记录删除：对象可能已经被手工删掉，
     * 卡着记录不删只会让列表越来越脏。
     *
     * @param id 记录 ID
     * @throws BizException 记录不存在（3001）
     */
    @Transactional(rollbackFor = Exception.class)
    public void remove(Long id) {
        TransferFile transfer = transferMapper.selectById(id);
        if (transfer == null) {
            throw new BizException(TransferError.NOT_FOUND);
        }

        UploadFile file = uploadFileMapper.selectById(transfer.getUploadFileId());
        if (file != null) {
            try {
                storageClient.delete(file.getStorageKey());
            } catch (Exception e) {
                log.warn("删除对象失败，继续删记录: key={}", file.getStorageKey(), e);
            }
            uploadFileMapper.deleteById(file.getId());
        }

        transferMapper.deleteById(id);
        log.info("删除传输记录: id={}, code={}", id, transfer.getCode());
    }

    // ==================== 清理 ====================

    /**
     * 定时清理过期传输。
     *
     * <p>每天凌晨 3 点跑一次：过期是低频事件，没必要更频繁地空转。
     * 每次最多处理 100 条，积压多的话第二天继续。
     *
     * <p>清理顺序是先删对象、再删记录——反过来的话，删对象失败就再也找不到这个 key 了。
     */
    @Scheduled(cron = "0 0 3 * * ?", zone = "Asia/Shanghai")
    public void cleanup() {
        List<TransferFile> expired = transferMapper.selectList(Wrappers.<TransferFile>lambdaQuery()
                .eq(TransferFile::getStatus, TransferFile.STATUS_READY)
                .lt(TransferFile::getExpireTime, LocalDateTime.now())
                .last("LIMIT " + CLEANUP_BATCH));

        if (expired.isEmpty()) {
            return;
        }

        for (TransferFile transfer : expired) {
            try {
                UploadFile file = uploadFileMapper.selectById(transfer.getUploadFileId());
                if (file != null) {
                    storageClient.delete(file.getStorageKey());
                    uploadFileMapper.deleteById(file.getId());
                }
                transferMapper.deleteById(transfer.getId());
            } catch (Exception e) {
                log.warn("清理过期传输失败: id={}, code={}", transfer.getId(), transfer.getCode(), e);
            }
        }

        log.info("清理过期传输: {} 条", expired.size());
    }

    // ==================== 内部方法 ====================

    /**
     * 传输记录 + 它引用的文件，两个对象一起用。
     */
    private record TransferPair(TransferFile transfer, UploadFile file) {
    }

    /**
     * 按提取码取"可下载"的记录及其文件。
     *
     * @param code 提取码
     * @return 记录对
     * @throws BizException 3001
     */
    private TransferPair loadAvailable(String code) {
        TransferFile transfer = transferMapper.selectOne(Wrappers.<TransferFile>lambdaQuery()
                .eq(TransferFile::getCode, code));

        if (transfer == null || !isAvailable(transfer)) {
            throw new BizException(TransferError.NOT_FOUND);
        }

        UploadFile file = uploadFileMapper.selectById(transfer.getUploadFileId());
        if (file == null || file.getStatus() != UploadFile.STATUS_READY) {
            // 文件被清理了但传输记录还在——对外统一说"不存在"
            throw new BizException(TransferError.NOT_FOUND);
        }

        return new TransferPair(transfer, file);
    }

    /**
     * 判断记录是否仍可用：状态正常且未过期。
     */
    private boolean isAvailable(TransferFile transfer) {
        return transfer.getStatus() != null
                && transfer.getStatus() == TransferFile.STATUS_READY
                && transfer.getExpireTime() != null
                && transfer.getExpireTime().isAfter(LocalDateTime.now());
    }

    /**
     * 生成不重复的提取码。
     *
     * <p>6 位、57 个字符，空间约 3.4×10^10；仍然查库确认，最多重试 10 次。
     */
    private String generateCode() {
        for (int attempt = 0; attempt < 10; attempt++) {
            StringBuilder sb = new StringBuilder(CODE_LENGTH);
            for (int i = 0; i < CODE_LENGTH; i++) {
                sb.append(CODE_CHARS.charAt(RANDOM.nextInt(CODE_CHARS.length())));
            }
            String code = sb.toString();

            Long exists = transferMapper.selectCount(Wrappers.<TransferFile>lambdaQuery()
                    .eq(TransferFile::getCode, code));
            if (exists == null || exists == 0) {
                return code;
            }
        }
        throw new BizException(TransferError.NOT_FOUND, "生成提取码失败，请重试");
    }

    /**
     * 解析有效期：为空用默认值，超过上限截断。
     */
    private int resolveExpireHours(Integer hours) {
        if (hours == null || hours <= 0) {
            return properties.getDefaultExpireHours();
        }
        return Math.min(hours, properties.getMaxExpireHours());
    }

    /**
     * 组装公开出参。
     */
    private TransferFileVO toVO(TransferFile transfer, UploadFile file) {
        TransferFileVO vo = new TransferFileVO();
        vo.setCode(transfer.getCode());
        vo.setFileName(file.getFileName());
        vo.setFileSize(file.getFileSize());
        vo.setUrl(storageClient.publicUrl(file.getStorageKey()));
        vo.setExpireTime(transfer.getExpireTime());
        vo.setDownloadCount(transfer.getDownloadCount());
        return vo;
    }

    /**
     * 组装管理端出参。
     */
    private TransferAdminVO toAdminVO(TransferFile transfer, UploadFile file) {
        TransferAdminVO vo = new TransferAdminVO();
        vo.setId(transfer.getId());
        vo.setCode(transfer.getCode());
        vo.setFileName(file != null ? file.getFileName() : "（文件已删除）");
        vo.setFileSize(file != null ? file.getFileSize() : 0L);
        vo.setUrl(file != null ? storageClient.publicUrl(file.getStorageKey()) : "");
        vo.setExpireTime(transfer.getExpireTime());
        vo.setDownloadCount(transfer.getDownloadCount());
        vo.setStatus(transfer.getStatus());
        vo.setCreateTime(transfer.getCreateTime());
        return vo;
    }
}
