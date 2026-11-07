package com.touki.transfer.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 管理端的传输记录出参。
 *
 * <p>比公开的 {@link TransferFileVO} 多几个字段，但仍然**不返回 storagePath**：
 * 服务器内部路径即使对管理员也没必要暴露在接口里。
 */
@Data
public class TransferAdminVO {

    /** 主键，删除时用 */
    private Long id;

    /** 原始文件名 */
    private String fileName;

    /** 文件大小（字节） */
    private Long fileSize;

    /** 提取码 */
    private String code;

    /** 过期时间 */
    private LocalDateTime expireTime;

    /** 下载次数 */
    private Integer downloadCount;

    /** 状态：0 上传中 / 1 可用 / 2 已删除 */
    private Integer status;

    /** 上传时间 */
    private LocalDateTime createTime;
}
