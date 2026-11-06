package com.touki.demo;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.touki.common.BaseEntity;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/**
 * 示例实体，对应表 {@code demo_item}。
 *
 * <p><b>这是一个可以整包删除的示例模块。</b>
 * 保留它的目的只有一个：演示一个完整功能需要哪些文件、按什么顺序写，
 * 以及分页、校验、唯一性检查这些通用能力怎么配合。
 * 开始写真正业务时，把这 7 个类（含 dto/vo 与 Controller）删掉，照着结构建自己的模块。
 *
 * <p>字段刻意做得很少：只有名称、状态和备注。
 * 示例越简单，模仿的成本越低；复杂的字段组合由具体业务决定。
 *
 * <p>用 {@code @Getter/@Setter} 而不是 {@code @Data}：{@code @Data} 会生成
 * 基于全部字段的 equals/hashCode，实体这样做容易出问题（详见 BaseEntity 的注释）。
 */
@Getter
@Setter
@ToString(callSuper = true)
@TableName("demo_item")
public class DemoItem extends BaseEntity {

    /**
     * 主键。
     *
     * <p>雪花 ID（19 位长整型），序列化时由 WebConfig 的全局配置转成字符串，
     * 否则前端 JS 的 Number 存不下，末位会被抹成 0。
     */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 名称，全表唯一 */
    private String name;

    /** 状态：1 启用 / 0 停用 */
    private Integer status;

    /** 备注，可为空 */
    private String remark;
}
