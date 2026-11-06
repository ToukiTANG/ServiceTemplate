package com.touki.config;

import com.touki.user.SysUser;
import com.touki.user.SysUserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * 首次启动时初始化管理员账号。
 *
 * <p>为什么不在 Flyway 脚本里直接插 admin：密码要存 BCrypt 密文，
 * 而 BCrypt 每次加密结果都不同（自带随机盐），没法在 SQL 里写死一个"正确"的密文；
 * 写死也不是不行，但换密码规则时就得改历史迁移脚本，很别扭。
 * 放在这里用同一套 {@link PasswordEncoder} 生成，改规则只需改代码。
 *
 * <p>判断条件用"表里一个用户都没有"，而不是"admin 不存在"：
 * 这样即使你手动删了 admin 又建了别的账号，重启也不会又被塞一个 admin 回来。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements ApplicationRunner {

    /** 初始账号与密码。仅首次创建时使用，登录后请立即修改 */
    private static final String DEFAULT_USERNAME = "admin";
    private static final String DEFAULT_PASSWORD = "admin123";

    private final SysUserMapper userMapper;
    private final PasswordEncoder passwordEncoder;

    /**
     * 应用启动完成后执行。
     *
     * @param args 启动参数，本类不使用
     */
    @Override
    public void run(ApplicationArguments args) {
        Long count = userMapper.selectCount(null);
        if (count != null && count > 0) {
            return;
        }

        SysUser admin = new SysUser();
        admin.setUsername(DEFAULT_USERNAME);
        // encode 内部会生成随机盐并做 10 轮哈希，耗时约 100ms，只在首次启动时执行一次
        admin.setPassword(passwordEncoder.encode(DEFAULT_PASSWORD));
        admin.setNickname("管理员");
        admin.setStatus(1);
        userMapper.insert(admin);

        // 用 warn 级别输出：这行日志应该在每次"首次启动"时被看见，
        // 而不是淹没在 info 堆里
        log.warn("未检测到任何用户，已创建默认账号 {} / {}，请登录后立即修改密码",
                DEFAULT_USERNAME, DEFAULT_PASSWORD);
    }
}
