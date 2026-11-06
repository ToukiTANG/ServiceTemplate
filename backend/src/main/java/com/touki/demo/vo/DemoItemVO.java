package com.touki.demo.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 示例出参。
 *
 * <p>出参对象只暴露前端真正需要的东西，实体永远不直接返回——
 * 那样会把内部的字段（以后可能加的敏感字段）一并泄露出去。
 *
 * <p>{@code id} 是 Long，序列化时由全局配置转成字符串。
 */
@Data
public class DemoItemVO {

    /** 主键 */
    private Long id;

    /** 名称 */
    private String name;

    /** 状态：1 启用 / 0 停用 */
    private Integer status;

    /** 备注 */
    private String remark;

    /** 创建时间，已配置为 yyyy-MM-dd HH:mm:ss 格式 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;
}
