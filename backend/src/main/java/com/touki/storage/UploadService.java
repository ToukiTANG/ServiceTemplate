package com.touki.storage;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.touki.common.BizException;
import com.touki.common.PageResult;
import com.touki.config.StorageProperties;
import com.touki.storage.dto.PrepareUploadDTO;
import com.touki.storage.dto.UploadCredentialDTO;
import com.touki.storage.dto.UploadQueryDTO;
import com.touki.storage.vo.PrepareUploadVO;
import com.touki.storage.vo.UploadCredentialVO;
import com.touki.storage.vo.UploadFileVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

/**
 * 文件上传业务：登记、发签名、确认、清理。
 *
 * <p><b>服务器不接收文件内容。</b>完整流程：
 * <pre>
 * 1. POST /api/upload/prepare     登记文件信息，拿到 storageKey
 * 2. POST /api/upload/credential  每个分片调一次，拿签名
 * 3. 前端 PUT 到对象存储          ← 文件不经过服务器
 * 4. POST /api/upload/complete    标记完成，业务侧才能引用
 * </pre>
 *
 * <p>这个设计的前提是服务器带宽很窄（几 Mbps）：几百 MB 的文件经服务器中转，
 * 上传和下载各要二十多分钟，期间 CPU 空闲但带宽被吃满，其他请求全部排队。
 * 直传把这段压力转移给了对象存储和客户端之间的链路。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UploadService {

    /**
     * 分片大小，固定 1MB。
     *
     * <p>这不是我们的选择——又拍云的断点续传协议规定分片大小固定 1MB。
     * 放在常量里是为了在接口返回时告诉前端，避免两端各写一份。
     */
    public static final long CHUNK_SIZE = 1024L * 1024;

    /** 「上传中」状态超过这个小时数就认为是孤儿，由清理任务回收 */
    private static final int ORPHAN_HOURS = 24;

    private final UploadFileMapper fileMapper;
    private final StorageClient storageClient;
    private final StorageProperties properties;

    // ==================== 上传三步 ====================

    /**
     * 登记一次上传，分配存储路径。
     *
     * @param dto 文件信息
     * @return 上传 ID、存储路径、分片大小
     * @throws BizException 文件超过上限（4004）
     */
    public PrepareUploadVO prepare(PrepareUploadDTO dto) {
        long maxSize = properties.getUpyun().getMaxFileSize();
        if (dto.getFileSize() > maxSize) {
            throw new BizException(StorageError.TOO_LARGE, "文件大小超过上限 " + formatSize(maxSize));
        }

        // 路径由服务端生成，前端无从干预——避免覆盖已有文件或写到意外位置
        String storageKey = storageClient.buildStorageKey(dto.getFileName());

        UploadFile record = new UploadFile();
        record.setFileName(sanitizeFileName(dto.getFileName()));
        record.setFileSize(dto.getFileSize());
        record.setContentType(dto.getContentType());
        record.setStorageKey(storageKey);
        record.setFileHash(dto.getFileHash());
        record.setStatus(UploadFile.STATUS_UPLOADING);
        fileMapper.insert(record);

        log.info("登记上传: id={}, name={}, size={}, key={}",
                record.getId(), record.getFileName(), record.getFileSize(), storageKey);

        // 分片任务的初始化由服务端代发：浏览器不允许 JS 设置 Content-Length，
        // 而又拍云的 initiate 要求带 Content-Length: 0，前端直接发会得到 404（很难排查）。
        // 这个请求只有几十字节，不占带宽；真正吃带宽的分片数据仍由前端直传。
        String multiUuid = storageClient.initMultipart(storageKey, dto.getFileSize(), dto.getContentType());

        String uploadBaseUrl = "https://" + properties.getUpyun().getApiHost();
        return new PrepareUploadVO(record.getId(), storageKey, uploadBaseUrl, multiUuid,
                (int) CHUNK_SIZE, (int) maxSize);
    }

    /**
     * 为一次对象存储请求签名。
     *
     * <p>调用频率最高：每个分片都要签一次。所以这里只做两件事——校验路径归属、算签名，
     * 不碰业务逻辑。
     *
     * @param dto 请求方法、路径、内容长度
     * @return 可直接放进请求头的签名
     * @throws BizException 路径不属于任何"上传中"的记录（4003）
     */
    public UploadCredentialVO credential(UploadCredentialDTO dto) {
        // 校验归属：防止有人拿别人的 storageKey 去签名，往别人文件上写数据
        UploadFile record = requireUploading(dto.getStorageKey());

        StorageClient.SignedRequest signed = storageClient.sign(
                dto.getMethod().toUpperCase(Locale.ROOT),
                record.getStorageKey(),
                dto.getContentLength());

        return new UploadCredentialVO(signed.authorization(), signed.date(), signed.path());
    }

    /**
     * 标记上传完成。
     *
     * <p>两件事：让又拍云把分片合并成完整文件，然后把记录标成可用。
     * 合并同样是服务端代发（无请求体的 PUT，浏览器发不出去）。
     *
     * @param uploadId  上传记录 ID
     * @param multiUuid 分片任务标识，prepare 时已下发
     * @return 文件信息，含可直接访问的 URL
     * @throws BizException 记录不存在（4003）或合并失败（4007）
     */
    @Transactional(rollbackFor = Exception.class)
    public UploadFileVO complete(Long uploadId, String multiUuid) {
        UploadFile record = fileMapper.selectById(uploadId);
        if (record == null) {
            throw new BizException(StorageError.FILE_NOT_FOUND);
        }

        if (record.getStatus() == UploadFile.STATUS_UPLOADING) {
            // 先合并，合并成功才标记可用——顺序反了会出现"记录可用但文件是半成品"
            if (StringUtils.hasText(multiUuid)) {
                storageClient.completeMultipart(record.getStorageKey(), multiUuid);
            }

            UploadFile update = new UploadFile();
            update.setId(uploadId);
            update.setStatus(UploadFile.STATUS_READY);
            fileMapper.updateById(update);
            record.setStatus(UploadFile.STATUS_READY);
            log.info("上传完成: id={}, key={}", uploadId, record.getStorageKey());
        }
        return toVO(record);
    }

    // ==================== 管理端 ====================

    /**
     * 分页查询上传记录。
     *
     * @param query 文件名模糊 + 状态筛选
     * @return 分页结果
     */
    public PageResult<UploadFileVO> page(UploadQueryDTO query) {
        LambdaQueryWrapper<UploadFile> wrapper = Wrappers.<UploadFile>lambdaQuery()
                .like(StringUtils.hasText(query.getFileName()), UploadFile::getFileName, query.getFileName())
                .eq(query.getStatus() != null, UploadFile::getStatus, query.getStatus())
                .orderByDesc(UploadFile::getCreateTime);

        Page<UploadFile> page = new Page<>(query.getPageNum(), query.getPageSize());
        Page<UploadFile> result = fileMapper.selectPage(page, wrapper);

        List<UploadFileVO> list = result.getRecords().stream().map(this::toVO).toList();
        return PageResult.of(list, result.getTotal(), query);
    }

    /**
     * 删除一条上传记录，同时删除对象存储上的文件。
     *
     * <p>对象删除失败不阻断记录删除：对象存储那边可能已经被手工删掉了，
     * 卡着记录不删只会让列表越来越脏。
     *
     * @param id 记录 ID
     * @throws BizException 记录不存在（4003）
     */
    @Transactional(rollbackFor = Exception.class)
    public void remove(Long id) {
        UploadFile record = fileMapper.selectById(id);
        if (record == null) {
            throw new BizException(StorageError.FILE_NOT_FOUND);
        }

        try {
            storageClient.delete(record.getStorageKey());
        } catch (Exception e) {
            log.warn("删除对象失败，继续删除记录: id={}, key={}", id, record.getStorageKey(), e);
        }

        fileMapper.deleteById(id);
        log.info("删除上传记录: id={}, key={}", id, record.getStorageKey());
    }

    // ==================== 清理 ====================

    /**
     * 定时清理孤儿记录。
     *
     * <p>「孤儿」= 登记了但一直没传完的记录（前端关了页面、网络断了、用户跑了）。
     * 它们占着对象存储上可能存在的半个文件，也让列表变脏。
     *
     * <p><b>每天凌晨 3 点跑一次就够了</b>，不必更频繁：
     * <ul>
     *   <li>清理条件是「上传中且已过 24 小时」，本来就是低频事件</li>
     *   <li>每半小时扫一次，绝大多数时候是空查询——走索引成本很低，但没有意义</li>
     *   <li>最坏情况只是孤儿多留一天，不影响任何功能</li>
     * </ul>
     *
     * <p>每次最多处理 100 条：积压多的话第二天继续，避免一次性打满对象存储的删除接口。
     */
    @Scheduled(cron = "0 0 3 * * ?", zone = "Asia/Shanghai")
    public void cleanupOrphans() {
        List<UploadFile> orphans = fileMapper.selectList(Wrappers.<UploadFile>lambdaQuery()
                .eq(UploadFile::getStatus, UploadFile.STATUS_UPLOADING)
                .lt(UploadFile::getCreateTime, LocalDateTime.now().minusHours(ORPHAN_HOURS))
                .last("LIMIT 100"));

        for (UploadFile orphan : orphans) {
            try {
                storageClient.delete(orphan.getStorageKey());
            } catch (Exception e) {
                // 没传完的文件在对象存储上通常不存在，删除返回 404 很正常，忽略即可
                log.debug("清理孤儿对象失败（可能本就不存在）: key={}", orphan.getStorageKey());
            }
            fileMapper.deleteById(orphan.getId());
        }

        if (!orphans.isEmpty()) {
            log.info("清理孤儿上传记录: {} 条", orphans.size());
        }
    }

    // ==================== 内部方法 ====================

    /**
     * 按 storageKey 找一条「上传中」的记录。
     *
     * @param storageKey 存储路径
     * @return 记录
     * @throws BizException 找不到（4003）
     */
    private UploadFile requireUploading(String storageKey) {
        UploadFile record = fileMapper.selectOne(Wrappers.<UploadFile>lambdaQuery()
                .eq(UploadFile::getStorageKey, storageClient.normalizeKey(storageKey)));

        if (record == null) {
            throw new BizException(StorageError.FILE_NOT_FOUND);
        }
        return record;
    }

    /**
     * 清洗文件名：去掉路径分隔符与控制字符。
     *
     * <p>文件名会被写进 {@code Content-Disposition} 响应头（下载时），
     * 带换行的名字能伪造 HTTP 头，必须清掉。
     */
    private String sanitizeFileName(String name) {
        String cleaned = name.replaceAll("[\\\\/:*?\"<>|\\r\\n]", "_").trim();
        if (!StringUtils.hasText(cleaned)) {
            return "file";
        }
        return cleaned.length() > 200 ? cleaned.substring(cleaned.length() - 200) : cleaned;
    }

    /**
     * 实体转 VO。
     *
     * @param file 实体
     * @return 出参
     */
    private UploadFileVO toVO(UploadFile file) {
        UploadFileVO vo = new UploadFileVO();
        vo.setId(file.getId());
        vo.setFileName(file.getFileName());
        vo.setFileSize(file.getFileSize());
        vo.setContentType(file.getContentType());
        vo.setStorageKey(file.getStorageKey());
        vo.setUrl(storageClient.publicUrl(file.getStorageKey()));
        vo.setCreateTime(file.getCreateTime());
        return vo;
    }

    /**
     * 把字节数格式化成人类可读的大小，用于错误提示。
     */
    private String formatSize(long bytes) {
        if (bytes >= 1024L * 1024 * 1024) {
            return (bytes / (1024L * 1024 * 1024)) + " GB";
        }
        return (bytes / (1024L * 1024)) + " MB";
    }
}
