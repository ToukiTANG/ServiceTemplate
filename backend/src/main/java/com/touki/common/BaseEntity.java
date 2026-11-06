package com.touki.common;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 实体基类：只放审计时间字段。
 *
 * <p>数据库里每张业务表都有 {@code create_time}、{@code update_time} 两列，
 * 由 MyBatis-Plus 的自动填充（见 MybatisPlusConfig 里的 MetaObjectHandler）负责写入，
 * 业务代码不需要手动 set。
 *
 * <p>为什么没有 {@code create_by} / {@code update_by}：单人项目不存在"谁改的"这个问题，
 * 加了只会让每条写操作都多一次当前用户取值。
 *
 * <p>为什么用 {@code @Getter/@Setter} 而不是 {@code @Data}：{@code @Data} 会生成
 * 基于全部字段的 equals/hashCode，实体这样做是有害的——两个不同时刻查出来的同一行数据
 * 会被判为不相等，放进 Set 或做比较时出怪问题。实体一律按主键判等，这里不生成即可。
 */
@Getter
@Setter
public class BaseEntity implements Serializable {

    /** 创建时间。仅在 INSERT 时填充，之后不再变 */
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /** 更新时间。INSERT 和 UPDATE 时都填充 */
    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
