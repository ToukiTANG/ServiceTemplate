package com.touki.user.vo;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 用户信息出参。
 *
 * <p>刻意不包含 {@code password}、{@code createTime} 等字段：
 * 出参对象只暴露前端真正需要的东西，实体类永远不直接返回。
 *
 * <p>{@code id} 是 Long，序列化时会被全局配置转成字符串，
 * 前端拿到的类型是 {@code string}——这是有意为之，见 WebConfig 里的说明。
 */
@Data
@AllArgsConstructor
public class UserVO {

    /** 用户 ID */
    private Long id;

    /** 登录名 */
    private String username;

    /** 显示名 */
    private String nickname;
}
