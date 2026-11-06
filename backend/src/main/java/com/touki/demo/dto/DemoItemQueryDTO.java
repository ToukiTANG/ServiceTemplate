package com.touki.demo.dto;

import com.touki.common.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 示例列表查询条件。
 *
 * <p>所有条件都是可选的，为 null 时不参与 SQL 拼接。
 * {@code @EqualsAndHashCode(callSuper = true)} 是配合 Lombok 的：
 * 父类有分页字段，不加这个注解编译器会警告（可能导致 equals 漏掉父类字段）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class DemoItemQueryDTO extends PageQuery {

    /** 名称，模糊匹配 */
    private String name;

    /** 状态：1 启用 / 0 停用 / null 不限 */
    private Integer status;
}
