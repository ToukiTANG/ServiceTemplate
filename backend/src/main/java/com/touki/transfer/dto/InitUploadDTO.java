package com.touki.transfer.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 上传初始化入参。
 *
 * <p>前端在真正传分片之前先调这个接口，把文件元信息报给服务端。
 * 服务端据此判断：文件多大、能不能收、之前是否已经传过一部分（断点续传）。
 */
@Data
public class InitUploadDTO {

    /** 原始文件名，用于生成下载时的文件名 */
    @NotBlank(message = "文件名不能为空")
    @Size(max = 255, message = "文件名不能超过 255 个字符")
    private String fileName;

    /** 文件字节数，服务端用它校验是否超过上限、计算分片总数 */
    @NotNull(message = "文件大小不能为空")
    @Min(value = 1, message = "文件大小必须大于 0")
    private Long fileSize;

    /**
     * 客户端生成的弱指纹（<b>不是</b> SHA-256）。
     *
     * <p>唯一用途是断点续传：同一个文件重传时，服务端靠它找回之前传了一半的分片。
     * 前端用「文件名 + 大小 + 最后修改时间」拼出来，不读文件内容，
     * 因为几百 MB 的文件在浏览器里算真实摘要会把内存打爆。
     *
     * <p>可以不传，代价是失去续传能力。
     */
    @Size(max = 64, message = "指纹长度不合法")
    private String fileHash;

    /** MIME 类型，仅记录，不参与校验 */
    @Size(max = 128, message = "MIME 类型过长")
    private String contentType;

    /**
     * 期望的有效期（小时）。
     *
     * <p>不传则用默认值（24 小时）；超过上限会被服务端截断到 168 小时（7 天），
     * 不会报错——正常用户不会手改这个值，真改了也没必要为难他。
     */
    @Min(value = 1, message = "有效期至少 1 小时")
    @Max(value = 720, message = "有效期过长")
    private Integer expireHours;
}
