package com.touki.common;

import lombok.Data;

/**
 * 分页查询入参基类。
 *
 * <p>业务查询 DTO 继承本类即可获得分页参数，例如：
 * <pre>{@code
 * public class ToolQueryDTO extends PageQuery {
 *     private String name;
 *     private String category;
 * }
 * }</pre>
 *
 * <p>前端统一传 {@code pageNum}、{@code pageSize}，后端不接收其他分页写法。
 */
@Data
public class PageQuery {

    /** 单页最大条数。超过这个值会被静默截断，而不是报错 */
    public static final int MAX_PAGE_SIZE = 200;

    /** 页码，从 1 开始。默认 1 */
    private long pageNum = 1L;

    /** 每页条数，默认 10 */
    private long pageSize = 10L;

    /**
     * 获取经过上限截断的每页条数。
     *
     * <p>这里覆写了 Lombok 生成的 getter（@Data 遇到已存在的方法不会重复生成）。
     * 截断放在 getter 而不是 setter，是为了让"最终生效的值"只有一个出口——
     * 无论前端传 1 还是 100000，取分页参数的地方拿到的都是安全值。
     *
     * <p>为什么静默截断而不是抛异常：前端调整分页大小是常有的事，
     * 传大了直接报错会让用户莫名其妙；截断到上限，功能照常可用。
     *
     * @return 实际生效的每页条数，范围 [1, 200]
     */
    public long getPageSize() {
        if (pageSize < 1) {
            // 传 0 或负数时回退到默认值，避免查出空列表或产生非法 SQL
            return 10L;
        }
        return Math.min(pageSize, MAX_PAGE_SIZE);
    }
}
