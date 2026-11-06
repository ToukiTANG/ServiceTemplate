package com.touki.tool.dto;

import com.touki.common.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 工具管理端列表查询条件。
 *
 * <p>所有条件都是可选的，为 null 时不参与 SQL 拼接。
 * {@code @EqualsAndHashCode(callSuper = true)} 是配合 Lombok 使用的：
 * 父类有分页字段，不加这个注解编译器会给出警告（可能导致 equals 漏掉父类字段）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ToolQueryDTO extends PageQuery {

    /** 名称，模糊匹配 */
    private String name;

    /** 分类，精确匹配 */
    private String category;

    /** 状态：1 上架 / 0 下架 / null 不限 */
    private Integer status;
}
