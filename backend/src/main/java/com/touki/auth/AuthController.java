package com.touki.auth;

import com.touki.auth.dto.LoginDTO;
import com.touki.auth.dto.PasswordDTO;
import com.touki.auth.vo.LoginVO;
import com.touki.common.R;
import com.touki.common.UserContext;
import com.touki.user.vo.UserVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 认证接口。
 *
 * <p>其中 {@code /login} 是唯一不需要令牌的接口（在 WebConfig 的白名单里），
 * 其余两个都走拦截器校验。
 *
 * <p>Controller 只做三件事：接参数、调 Service、包 {@code R}。
 * 这里没有 if 判断、没有 try-catch——业务规则全在 {@link AuthService} 里。
 */
@Tag(name = "认证", description = "登录、当前用户、修改密码")
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    /**
     * 登录。
     *
     * @param dto 用户名与密码
     * @return 令牌与用户信息
     */
    @Operation(summary = "登录", description = "成功后返回 JWT，后续管理端接口都需带上")
    @PostMapping("/login")
    public R<LoginVO> login(@Valid @RequestBody LoginDTO dto) {
        return R.ok(authService.login(dto));
    }

    /**
     * 查询当前登录用户。
     *
     * <p>前端刷新页面后用它确认令牌是否仍然有效，同时补齐 pinia 里的用户信息。
     *
     * @return 当前用户信息
     */
    @Operation(summary = "当前登录用户", security = @SecurityRequirement(name = "bearerAuth"))
    @GetMapping("/me")
    public R<UserVO> me() {
        return R.ok(authService.currentUser(UserContext.require()));
    }

    /**
     * 修改密码。
     *
     * @param dto 原密码与新密码
     * @return 空响应
     */
    @Operation(summary = "修改密码", security = @SecurityRequirement(name = "bearerAuth"))
    @PutMapping("/password")
    public R<Void> changePassword(@Valid @RequestBody PasswordDTO dto) {
        authService.changePassword(UserContext.require(), dto);
        return R.ok();
    }
}
