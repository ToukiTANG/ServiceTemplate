package com.touki.tool;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.touki.common.BaseEntity;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/**
 * 工具入口实体，对应表 {@code tool}。
 *
 * <p>这是本项目的核心业务对象：管理端维护它，展示端读取它渲染卡片墙。
 *
 * <p>{@code link} 字段有两种形态，前端据此决定跳转方式：
 * <ul>
 *   <li>{@code http://} 或 {@code https://} 开头 → 外链，新窗口打开</li>
 *   <li>{@code /} 开头 → 站内路径，走前端路由跳转</li>
 * </ul>
 * 校验规则见 {@link com.touki.tool.dto.ToolSaveDTO}。
 *
 * <p>用 {@code @Getter/@Setter} 而不是 {@code @Data}：{@code @Data} 生成的
 * equals/hashCode 基于全部字段，实体这样做容易出问题（详见 BaseEntity 的说明）。
 * {@code @ToString} 单独加上，日志里打印时有用。
 */
@Getter
@Setter
@ToString(callSuper = true)
@TableName("tool")
public class Tool extends BaseEntity {

    /**
     * 主键。
     *
     * <p>{@code ASSIGN_ID} 表示由 MP 生成雪花 ID（19 位长整型）。
     * 前端拿到的会是字符串——序列化时统一把 Long 转成 String，
     * 否则 JS 的 Number 存不下 19 位，末位会被抹成 0。
     */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 工具名称，同一分类下唯一 */
    private String name;

    /** 一句话简介，展示在卡片标题下方 */
    private String description;

    /** 图标：直接存 emoji（如 🧰）或 Element Plus 图标名 */
    private String icon;

    /** 跳转地址，外链或站内路径 */
    private String link;

    /** 分类名。用字符串而非外键，省掉分类表的增删改查 */
    private String category;

    /** 排序值，升序，越小越靠前 */
    private Integer sort;

    /** 状态：1 上架（展示页可见）/ 0 下架 */
    private Integer status;
}
