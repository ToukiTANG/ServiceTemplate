package com.touki.tool.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 工具新增/修改入参。
 *
 * <p>新增和修改共用一个 DTO：两者字段完全一致，拆成两个类只会产生
 * "改了一个忘了改另一个"的风险。如果将来修改时有不许改的字段
 * （比如编码不允许变更），再用分组校验（{@code @Validated(Update.class)}）区分。
 *
 * <p>注意这里没有 {@code id} 字段——修改时 ID 从路径参数取，
 * 不放在 body 里，避免"路径里的 ID 和 body 里的 ID 不一致"这种歧义。
 */
@Data
public class ToolSaveDTO {

    /** 工具名称。同一分类下不允许重名（由 Service 校验） */
    @NotBlank(message = "工具名称不能为空")
    @Size(max = 50, message = "工具名称不能超过 50 个字符")
    private String name;

    /** 一句话简介，展示在卡片上 */
    @Size(max = 200, message = "简介不能超过 200 个字符")
    private String description;

    /** 图标：emoji 或 Element Plus 图标名，可以为空 */
    @Size(max = 50, message = "图标不能超过 50 个字符")
    private String icon;

    /**
     * 跳转地址。
     *
     * <p>只允许两种形态：
     * <ul>
     *   <li>{@code http://} 或 {@code https://} 开头：外链</li>
     *   <li>{@code /} 开头：站内路由路径</li>
     * </ul>
     * 用正则卡死，是为了让前端的跳转逻辑只需判断两种情况。
     * 如果不校验，用户填一个 {@code javascript:alert(1)} 就能在展示页执行脚本。
     */
    @NotBlank(message = "跳转地址不能为空")
    @Size(max = 500, message = "跳转地址不能超过 500 个字符")
    @Pattern(regexp = "^(https?://|/).*", message = "跳转地址必须以 http://、https:// 或 / 开头")
    private String link;

    /** 分类名。为空时由 Service 填默认值"默认" */
    @Size(max = 50, message = "分类名不能超过 50 个字符")
    private String category;

    /** 排序值，升序。为空时按 0 处理 */
    private Integer sort;

    /** 状态：1 上架 / 0 下架。为空时按上架处理 */
    private Integer status;
}
