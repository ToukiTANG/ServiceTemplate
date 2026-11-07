package com.touki.transfer;

import com.touki.common.BizException;
import com.touki.common.ErrorCode;
import com.touki.config.TransferProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.stream.Stream;

/**
 * 文件存储层：所有跟磁盘打交道的事情都在这里。
 *
 * <p>单独拆出来的原因：路径拼接、分片合并、hash 计算这些东西逻辑琐碎且容易出安全漏洞
 * （路径穿越），集中在一处比散落在 Service 里好审计。Service 只关心业务流程。
 *
 * <p>目录结构：
 * <pre>
 * {storagePath}/
 *   ├── 20261107/         按天分目录，避免单目录文件过多导致 ls/del 变慢
 *   │   └── {id}.bin      合并后的完整文件
 *   └── tmp/
 *       └── {uploadId}/   分片临时目录，第 i 片叫 i.part
 * </pre>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TransferStorage {

    /** 分片文件后缀 */
    private static final String PART_SUFFIX = ".part";

    /** 合并后文件的后缀。不保留原扩展名，避免执行型文件（.jsp/.sh）被服务器误解析 */
    private static final String BLOB_SUFFIX = ".bin";

    private final TransferProperties properties;

    /** 存储根目录（绝对路径、已规范化） */
    public Path baseDir() {
        return Paths.get(properties.getStoragePath()).toAbsolutePath().normalize();
    }

    /** 某个上传会话的分片临时目录 */
    public Path tmpDir(String uploadId) {
        return baseDir().resolve("tmp").resolve(uploadId).normalize();
    }

    /**
     * 把单个分片写到临时目录。
     *
     * @param uploadId 上传会话 ID
     * @param index    分片序号，从 0 开始
     * @param file     分片内容
     * @throws BizException 写盘失败时抛出
     */
    public void saveChunk(String uploadId, int index, MultipartFile file) {
        Path dir = tmpDir(uploadId);
        Path target = dir.resolve(index + PART_SUFFIX).normalize();

        // 双保险：即使 uploadId 被构造成 ../../etc 这种，也走不出沙箱
        if (!target.startsWith(baseDir())) {
            throw new BizException(ErrorCode.TRANSFER_INVALID_PATH);
        }

        try {
            Files.createDirectories(dir);
            // 用 transferTo 而不是先读进 byte[]：5MB 的分片读进内存没必要，
            // 大文件并发上传时这样很容易把堆撑爆
            file.transferTo(target);
        } catch (IOException e) {
            log.error("写入分片失败: uploadId={}, index={}", uploadId, index, e);
            throw new BizException(ErrorCode.TRANSFER_STORAGE_ERROR);
        }
    }

    /**
     * 扫描临时目录，返回已上传的分片索引。
     *
     * <p>断点续传靠它：前端把返回的索引跳过，只传剩下的。
     *
     * @param uploadId    上传会话 ID
     * @param totalChunks 分片总数，用于过滤越界文件
     * @return 已存在的分片索引，升序
     */
    public List<Integer> uploadedChunks(String uploadId, int totalChunks) {
        Path dir = tmpDir(uploadId);
        List<Integer> result = new ArrayList<>();
        if (!Files.isDirectory(dir)) {
            return result;
        }

        try (Stream<Path> stream = Files.list(dir)) {
            stream.filter(Files::isRegularFile)
                    .map(Path::getFileName)
                    .map(Path::toString)
                    .filter(name -> name.endsWith(PART_SUFFIX))
                    .forEach(name -> {
                        String num = name.substring(0, name.length() - PART_SUFFIX.length());
                        try {
                            int idx = Integer.parseInt(num);
                            if (idx >= 0 && idx < totalChunks) {
                                result.add(idx);
                            }
                        } catch (NumberFormatException ignored) {
                            // 不是数字命名的文件，忽略（可能是残留的临时文件）
                        }
                    });
        } catch (IOException e) {
            log.warn("扫描分片目录失败: uploadId={}", uploadId, e);
        }

        result.sort(Integer::compareTo);
        return result;
    }

    /**
     * 按序合并分片，返回相对存储路径。
     *
     * <p>关键点：<b>流式追加</b>，逐个分片拷贝，任何时候内存里只有一个缓冲区。
     * 如果把所有分片读成 byte[] 再拼接，500MB 的文件会直接 OOM。
     *
     * @param uploadId    上传会话 ID
     * @param totalChunks 分片总数
     * @param fileId      文件记录 ID，用作最终文件名
     * @return 相对路径，形如 {@code 20261107/1234567890.bin}
     * @throws BizException 分片缺失或写盘失败
     */
    public String merge(String uploadId, int totalChunks, Long fileId) {
        Path tmp = tmpDir(uploadId);
        String day = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
        String relative = day + "/" + fileId + BLOB_SUFFIX;
        Path target = baseDir().resolve(relative).normalize();

        try {
            Files.createDirectories(target.getParent());

            try (OutputStream out = Files.newOutputStream(target,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE)) {

                for (int i = 0; i < totalChunks; i++) {
                    Path part = tmp.resolve(i + PART_SUFFIX);
                    if (!Files.exists(part)) {
                        log.warn("分片缺失: uploadId={}, index={}", uploadId, i);
                        throw new BizException(ErrorCode.TRANSFER_CHUNK_MISSING);
                    }
                    try (InputStream in = Files.newInputStream(part)) {
                        in.transferTo(out);
                    }
                }
            }
        } catch (BizException e) {
            // 合并失败要把半成品删掉，否则会留下一个大小不对的残缺文件
            deleteQuietly(target);
            throw e;
        } catch (IOException e) {
            deleteQuietly(target);
            log.error("合并分片失败: uploadId={}", uploadId, e);
            throw new BizException(ErrorCode.TRANSFER_STORAGE_ERROR);
        }

        return relative;
    }

    /**
     * 计算文件 SHA-256。
     *
     * @param path 文件路径
     * @return 64 位小写十六进制
     */
    public String sha256(Path path) {
        try (InputStream in = Files.newInputStream(path);
             DigestInputStream digestIn = new DigestInputStream(in, MessageDigest.getInstance("SHA-256"))) {
            byte[] buffer = new byte[8192];
            // 读空流即可，DigestInputStream 会自动更新摘要
            while (digestIn.read(buffer) != -1) {
                // 不处理内容，只为了让摘要跑完
            }
            return HexFormat.of().formatHex(digestIn.getMessageDigest().digest());
        } catch (IOException | NoSuchAlgorithmException e) {
            log.error("计算文件哈希失败: {}", path, e);
            throw new BizException(ErrorCode.TRANSFER_STORAGE_ERROR);
        }
    }

    /**
     * 把相对路径解析成绝对路径，并确认它没跑出存储根目录。
     *
     * <p>这是防路径穿越的最后一道关卡：即使数据库里的 storage_path 被人改成了
     * {@code ../../etc/passwd}，这里也会拦住。
     *
     * @param relativePath 相对路径
     * @return 绝对路径
     * @throws BizException 路径非法或文件不存在
     */
    public Path resolveSafe(String relativePath) {
        Path base = baseDir();
        Path target = base.resolve(relativePath).normalize();
        if (!target.startsWith(base)) {
            throw new BizException(ErrorCode.TRANSFER_INVALID_PATH);
        }
        if (!Files.isRegularFile(target)) {
            throw new BizException(ErrorCode.TRANSFER_FILE_MISSING);
        }
        return target;
    }

    /**
     * 删除某个上传会话的临时目录。
     *
     * @param uploadId 上传会话 ID
     */
    public void deleteTmpDir(String uploadId) {
        Path dir = tmpDir(uploadId);
        if (!Files.exists(dir)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(dir)) {
            walk.sorted((a, b) -> b.getNameCount() - a.getNameCount())
                    .forEach(p -> deleteQuietly(p));
        } catch (IOException e) {
            log.warn("清理临时目录失败: uploadId={}", uploadId, e);
        }
    }

    /**
     * 删除文件，失败只记日志不抛异常。
     *
     * <p>清理任务用它：删不掉某个文件不应该中断整批清理。
     *
     * @param path 文件路径
     */
    public void deleteQuietly(Path path) {
        try {
            if (path != null && Files.exists(path)) {
                Files.delete(path);
            }
        } catch (IOException e) {
            log.warn("删除文件失败: {}", path, e);
        }
    }
}
