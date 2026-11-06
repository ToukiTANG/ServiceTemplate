package com.touki.tool.vo;

import lombok.Data;

/**
 * 工具出参（展示端）。
 *
 * <p>只包含渲染卡片需要的字段。刻意不返回：
 * <ul>
 *   <li>{@code status}：展示端只会拿到上架数据，返回了也没意义</li>
 *   <li>{@code createTime}/{@code updateTime}：展示页不显示，少两个字段前端少两处判断</li>
 * </ul>
 *
 * <p>这个 VO 是公开接口的返回值，任何字段都会暴露给所有访问者，
 * 加字段前先确认它不涉及内部信息。
 */
@Data
public class PortalToolVO {

    /** 工具 ID */
    private Long id;

    /** 名称 */
    private String name;

    /** 简介 */
    private String description;

    /** 图标 */
    private String icon;

    /** 跳转地址：http(s) 开头为外链，/ 开头为站内路径 */
    private String link;

    /** 分类，前端按这个字段分组 */
    private String category;

    /** 排序值，同分类内升序 */
    private Integer sort;
}
