package com.touki.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 文件传输相关配置，对应 {@code application.yml} 里的 {@code app.transfer.*}。
 *
 * <p>全部给了默认值，所以不配置也能跑起来——默认把文件放在
 * {@code D:/data/transfer}（Windows）下。换机器或换挂载点时只改配置，不用改代码。
 */
@Data
@Component
@ConfigurationProperties(prefix = "app.transfer")
public class TransferProperties {

    /**
     * 文件存储根目录。
     *
     * <p>目录结构由程序自动创建：
     * <pre>
     * {storagePath}/
     *   ├── 20261107/         按天分目录，避免单目录下文件过多
     *   │   └── {id}.bin      合并后的完整文件
     *   └── tmp/
     *       └── {uploadId}/   分片临时目录，合并成功后删除
     * </pre>
     */
    private String storagePath = "D:/data/transfer";

    /** 单文件大小上限（字节）。默认 500MB，与需求约定一致 */
    private long maxFileSize = 500L * 1024 * 1024;

    /**
     * 分片大小（字节），默认 5MB。
     *
     * <p>这个值要和 {@code spring.servlet.multipart.max-file-size} 配合：
     * multipart 的上限必须大于分片大小，否则单个分片都传不上来。
     * 反过来，multipart 上限也不能设成整个文件大小，那样就没有分片的意义了。
     */
    private long chunkSize = 5L * 1024 * 1024;

    /** 默认有效期（小时）。默认 24 小时，前端也可以选 7 天 */
    private int defaultExpireHours = 24;

    /** 可选的最长有效期（小时）。默认 168 小时 = 7 天，防止有人传长期文件把盘占满 */
    private int maxExpireHours = 168;
}
