package com.touki.storage.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 上传记录出参。
 *
 * <p>业务侧拿到 {@code url} 后可以直接渲染或跳转，不必自己拼域名——
 * 这样换域名、接 CDN 都只改服务端配置。
 */
@Data
public class UploadFileVO {

    /** 记录 ID，业务表用它做外键 */
    private Long id;

    /** 原始文件名 */
    private String fileName;

    /** 文件大小（字节） */
    private Long fileSize;

    /** MIME 类型 */
    private String contentType;

    /** 存储相对路径（业务表也可以存这个，便于直接调 delete） */
    private String storageKey;

    /** 完整访问地址，域名来自配置 */
    private String url;

    /** 上传完成时间 */
    private LocalDateTime createTime;
}
