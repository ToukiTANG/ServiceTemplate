package com.touki.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 修改密码入参。
 *
 * <p>需要原密码是为了防止"电脑没锁屏被人顺手改掉密码"，
 * 单令牌方案下这也是唯一能让旧令牌失效的手段（改完密码后需重新登录）。
 */
@Data
public class PasswordDTO {

    /** 原密码，用于身份确认 */
    @NotBlank(message = "原密码不能为空")
    private String oldPassword;

    /**
     * 新密码。
     *
     * <p>长度上限 32 是配合 BCrypt 的特性：BCrypt 只处理前 72 字节，
     * 超长密码的部分会被静默忽略，限制长度可以避免这种"以为很长其实没用"的误解。
     */
    @NotBlank(message = "新密码不能为空")
    @Size(min = 6, max = 32, message = "新密码长度需在 6 到 32 位之间")
    private String newPassword;
}
