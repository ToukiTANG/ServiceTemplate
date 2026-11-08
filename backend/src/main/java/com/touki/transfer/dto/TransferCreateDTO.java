package com.touki.transfer.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 创建传输记录的入参。
 *
 * <p>调用前提：文件已经通过脚手架的 {@code /api/upload/*} 接口直传完成。
 * 这里只负责"把这个已存在的文件包装成一次可分享的传输"。
 */
@Data
public class TransferCreateDTO {

    /**
     * 文件上传记录 ID。
     *
     * <p>前端在 {@code /api/upload/complete} 的响应里能拿到它。
     */
    @NotNull(message = "文件 ID 不能为空")
    private Long uploadFileId;

    /**
     * 期望的有效期（小时）。
     *
     * <p>不传用默认值（24 小时）；超过上限会被截断到 168 小时，不报错——
     * 正常用户不会改这个值，真改了也没必要为难他。
     */
    @Min(value = 1, message = "有效期至少 1 小时")
    @Max(value = 720, message = "有效期过长")
    private Integer expireHours;
}
