package com.touki.common;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.Collections;
import java.util.List;

/**
 * 分页查询出参。
 *
 * <p>与前端约定的结构固定为这四个字段，不做嵌套：
 * <pre>{@code
 * { "list": [], "total": 128, "pageNum": 1, "pageSize": 10 }
 * }</pre>
 *
 * @param <T> 列表元素类型（通常是 VO，不是实体）
 */
@Data
@AllArgsConstructor
public class PageResult<T> {

    /** 当前页数据。无数据时是空列表，不是 null */
    private List<T> list;

    /** 满足条件的总记录数，用于前端算总页数 */
    private long total;

    /** 当前页码 */
    private long pageNum;

    /** 每页条数（已按 {@link PageQuery#MAX_PAGE_SIZE} 截断后的实际值） */
    private long pageSize;

    /**
     * 组装分页结果。
     *
     * @param list     当前页数据，传 null 会被转成空列表
     * @param total    总记录数
     * @param query    原始分页参数，用于回填页码与条数
     */
    public static <T> PageResult<T> of(List<T> list, long total, PageQuery query) {
        return new PageResult<>(
                list == null ? Collections.emptyList() : list,
                total,
                query.getPageNum(),
                query.getPageSize());
    }

    /**
     * 空结果。用于查询条件不命中、或上游提前返回的场景。
     *
     * @param query 原始分页参数
     */
    public static <T> PageResult<T> empty(PageQuery query) {
        return of(Collections.emptyList(), 0L, query);
    }
}
