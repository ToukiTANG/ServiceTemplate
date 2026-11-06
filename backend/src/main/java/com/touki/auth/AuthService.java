package com.touki.auth;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.touki.auth.dto.LoginDTO;
import com.touki.auth.dto.PasswordDTO;
import com.touki.auth.vo.LoginVO;
import com.touki.common.BizException;
import com.touki.common.ErrorCode;
import com.touki.user.SysUser;
import com.touki.user.SysUserMapper;
import com.touki.user.vo.UserVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 认证业务。
 *
 * <p>放在 auth 包而不是 user 包，是为了保持依赖单向：auth → user。
 * 如果反过来把登录逻辑塞进 user 包，user 就要依赖 auth 的 JwtUtil 和 LoginVO，
 * 两个包互相依赖，以后想单独抽出 user 就抽不动了。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final SysUserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    /**
     * 登录。
     *
     * <p>校验顺序是刻意的：
     * <ol>
     *   <li>先查用户 + 校验密码，失败一律返回"用户名或密码错误"</li>
     *   <li>密码正确后才检查账号状态</li>
     * </ol>
     * 如果把状态检查提前，未登录的人就能靠不同提示判断出"这个用户名存在但被禁用了"，
     * 等于给暴力破解提供了一个用户名枚举接口。
     *
     * @param dto 登录入参
     * @return 令牌与用户信息
     * @throws BizException 用户名密码错误（1001）或账号被禁用（1002）
     */
    public LoginVO login(LoginDTO dto) {
        SysUser user = userMapper.selectOne(
                Wrappers.<SysUser>lambdaQuery().eq(SysUser::getUsername, dto.getUsername()));

        // 用户不存在与密码错误合并为同一个提示，避免被用来枚举用户名
        if (user == null || !passwordEncoder.matches(dto.getPassword(), user.getPassword())) {
            throw new BizException(ErrorCode.LOGIN_FAILED);
        }

        // 走到这里说明密码是对的，可以安全地告知账号状态
        if (user.getStatus() == null || user.getStatus() != 1) {
            throw new BizException(ErrorCode.ACCOUNT_DISABLED);
        }

        String token = jwtUtil.generate(user.getId(), user.getUsername());
        log.info("用户登录成功: {}", user.getUsername());
        return new LoginVO(token, new UserVO(user.getId(), user.getUsername(), user.getNickname()));
    }

    /**
     * 查询当前登录用户信息。
     *
     * @param userId 用户 ID，由拦截器从令牌解析后放入上下文
     * @return 用户信息
     * @throws BizException 用户不存在（例如令牌签发后被删号）
     */
    public UserVO currentUser(Long userId) {
        SysUser user = userMapper.selectById(userId);
        if (user == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        return new UserVO(user.getId(), user.getUsername(), user.getNickname());
    }

    /**
     * 修改密码。
     *
     * <p>先验证原密码、再校验新密码规则。这个顺序不能反：
     * 原密码是"你有没有资格改"，新密码规则是"改成什么"，资格没确认就谈内容不合逻辑；
     * 而且这样用户输错原密码时得到的是准确提示，不会误以为是新密码格式问题。
     *
     * <p>改密后旧令牌不会立即失效（单令牌方案没有黑名单），
     * 这是已知取舍——单人项目不存在"别人拿着我的旧令牌"的场景。
     *
     * @param userId 用户 ID
     * @param dto    原密码与新密码
     * @throws BizException 原密码错误（1003）或新旧密码相同（400）
     */
    @Transactional(rollbackFor = Exception.class)
    public void changePassword(Long userId, PasswordDTO dto) {
        SysUser user = userMapper.selectById(userId);
        if (user == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }

        if (!passwordEncoder.matches(dto.getOldPassword(), user.getPassword())) {
            throw new BizException(ErrorCode.OLD_PASSWORD_WRONG);
        }

        if (dto.getOldPassword().equals(dto.getNewPassword())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "新密码不能与原密码相同");
        }

        // 只更新密码字段：用新对象而不是改 user，避免把其他字段一起写回去
        SysUser update = new SysUser();
        update.setId(userId);
        update.setPassword(passwordEncoder.encode(dto.getNewPassword()));
        userMapper.updateById(update);

        log.info("用户 {} 修改了密码", user.getUsername());
    }
}
