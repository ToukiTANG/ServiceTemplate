package com.touki.tool.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 工具出参（管理端）。
 *
 * <p>比展示端多两个字段：{@code status}（管理端要看到下架的）和两个时间戳。
 * 分开两个 VO 而不是共用一个，是为了让"展示端不返回什么"这件事体现在类型上——
 * 如果共用一个 VO，就得靠 service 里手动清字段，迟早会漏。
 *
 * <p>{@code id} 是 Long，序列化时由 WebConfig 里的全局配置转成字符串，
 * 前端拿到的是 {@code "1234567890123456789"} 这样的字符串。
 */
@Data
public class ToolVO {

    /** 工具 ID */
    private Long id;

    /** 名称 */
    private String name;

    /** 简介 */
    private String description;

    /** 图标 */
    private String icon;

    /** 跳转地址 */
    private String link;

    /** 分类 */
    private String category;

    /** 排序值 */
    private Integer sort;

    /** 状态：1 上架 / 0 下架 */
    private Integer status;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;
}
