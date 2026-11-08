package com.touki.transfer.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 管理端的传输记录出参。
 *
 * <p>比公开接口多 {@code id}、{@code status}、{@code createTime}，
 * 少了内部关联字段（{@code uploadFileId} 没必要暴露）。
 */
@Data
public class TransferAdminVO {

    /** 记录 ID，删除时用 */
    private Long id;

    /** 提取码 */
    private String code;

    /** 文件名（来自关联的上传记录） */
    private String fileName;

    /** 文件大小（字节） */
    private Long fileSize;

    /** 直链地址 */
    private String url;

    /** 过期时间 */
    private LocalDateTime expireTime;

    /** 下载次数 */
    private Integer downloadCount;

    /** 状态：1 可用 / 2 已失效 */
    private Integer status;

    /** 创建时间 */
    private LocalDateTime createTime;
}
