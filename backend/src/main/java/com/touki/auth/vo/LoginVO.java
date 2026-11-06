package com.touki.auth.vo;

import com.touki.user.vo.UserVO;
import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 登录成功出参。
 *
 * <p>前端拿到后要做三件事：把 token 存起来、把 user 放进 pinia、
 * 跳转到管理端首页（或 {@code redirect} 参数指定的页面）。
 */
@Data
@AllArgsConstructor
public class LoginVO {

    /** JWT 令牌，前端每次请求放在 {@code Authorization: Bearer {token}} 里 */
    private String token;

    /** 当前登录用户的基本信息，省掉登录后再调一次 /me */
    private UserVO user;
}
