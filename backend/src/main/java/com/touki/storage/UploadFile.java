package com.touki.storage;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.touki.common.BaseEntity;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/**
 * 文件上传记录，对应表 {@code upload_file}。
 *
 * <p>前端直传对象存储，服务器不碰文件内容，所以这张表是「传了什么」的唯一来源。
 * 没有它，对象存储里会堆积无法追溯的孤儿文件。
 *
 * <p>与业务的关系：业务表保存本表的 {@code id}（比如 {@code transfer_file.upload_file_id}），
 * 本表不认识任何业务。
 */
@Getter
@Setter
@ToString(callSuper = true, exclude = "storageKey")
@TableName("upload_file")
public class UploadFile extends BaseEntity {

    /** 状态：已登记但还没传完，业务侧不应该引用 */
    public static final int STATUS_UPLOADING = 0;

    /** 状态：上传完成，可以使用 */
    public static final int STATUS_READY = 1;

    /** 状态：记录被清理（对象通常也已删除） */
    public static final int STATUS_DELETED = 2;

    /** 主键，雪花 ID */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 原始文件名，仅用于展示和下载时还原 */
    private String fileName;

    /** 文件字节数 */
    private Long fileSize;

    /** MIME 类型 */
    private String contentType;

    /**
     * 对象存储中的相对路径，如 {@code 2026/11/07/ab12cd34.pdf}。
     *
     * <p>不存完整 URL：换域名或接入 CDN 时只用改配置，不用刷数据。
     */
    private String storageKey;

    /** 客户端指纹（非 SHA-256），用于断点续传识别，可为空 */
    private String fileHash;

    /** 状态：0 上传中 / 1 可用 / 2 已删除 */
    private Integer status;
}
