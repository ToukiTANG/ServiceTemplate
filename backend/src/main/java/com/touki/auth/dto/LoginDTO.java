package com.touki.auth.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 登录入参。
 *
 * <p>注意这里没有加"密码长度不小于 N"之类的校验：登录时不校验密码格式，
 * 只校验非空。原因是格式校验属于注册/改密场景，登录时校验格式会泄露
 * "密码规则"这一信息，也没意义——密码对不对由 {@code passwordEncoder.matches} 判断。
 */
@Data
public class LoginDTO {

    /** 用户名 */
    @NotBlank(message = "用户名不能为空")
    private String username;

    /** 明文密码，服务端用 BCrypt 比对，不落库、不打日志 */
    @NotBlank(message = "密码不能为空")
    private String password;
}
