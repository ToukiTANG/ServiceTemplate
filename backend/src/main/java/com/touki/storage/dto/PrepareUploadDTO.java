package com.touki.storage.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 上传前登记入参。
 *
 * <p>前端在开始直传之前调一次，拿到后端分配好的存储路径。
 * **路径必须由后端生成**：如果让前端自己定，就可能覆盖别人的文件，或者写到奇怪的位置。
 */
@Data
public class PrepareUploadDTO {

    /** 原始文件名，只用来取扩展名和展示 */
    @NotBlank(message = "文件名不能为空")
    @Size(max = 255, message = "文件名不能超过 255 个字符")
    private String fileName;

    /** 文件字节数，服务端据此判断是否超限 */
    @NotNull(message = "文件大小不能为空")
    @Min(value = 1, message = "文件大小必须大于 0")
    private Long fileSize;

    /** MIME 类型，仅记录 */
    @Size(max = 128, message = "MIME 类型过长")
    private String contentType;

    /**
     * 客户端指纹（不是 SHA-256）。
     *
     * <p>可选。存下来方便排查"同一个文件重复上传"的情况，
     * 也留给业务做秒传之类的优化。
     */
    @Size(max = 64, message = "指纹长度不合法")
    private String fileHash;
}
