package com.touki.demo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 示例新增/修改入参。
 *
 * <p>新增和修改共用一个 DTO：两者字段完全一致，拆开只会产生
 * "改了一个忘了改另一个"的风险。真出现"某些字段不许改"的需求时，
 * 再用分组校验（{@code @Validated(Update.class)}）区分。
 *
 * <p>没有 {@code id} 字段——修改时 ID 从路径参数取，
 * 避免"路径里的 ID 和 body 里的 ID 不一致"这种歧义。
 */
@Data
public class DemoItemSaveDTO {

    /** 名称，全表唯一（唯一性由 Service 校验，数据库没有加唯一索引以便演示业务校验） */
    @NotBlank(message = "名称不能为空")
    @Size(max = 50, message = "名称不能超过 50 个字符")
    private String name;

    /** 状态：1 启用 / 0 停用。为空时由 Service 按启用处理 */
    private Integer status;

    /** 备注 */
    @Size(max = 200, message = "备注不能超过 200 个字符")
    private String remark;
}
