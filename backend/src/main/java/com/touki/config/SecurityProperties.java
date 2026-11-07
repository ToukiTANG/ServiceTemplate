package com.touki.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 安全相关的配置，对应 {@code application.yml} 里的 {@code app.security.*}。
 *
 * <p>把「免登录白名单」从代码挪到配置，是为了让 {@code WebConfig} 这个脚手架文件
 * 在 main 和业务分支上保持完全一致——业务分支多几个公开接口时，只改自己的 yml，
 * 不用动代码，也就不会在 merge 时和脚手架打架。
 */
@Data
@Component
@ConfigurationProperties(prefix = "app.security")
public class SecurityProperties {

    /**
     * 免登录接口的路径匹配规则（Ant 风格）。
     *
     * <p>默认只放登录接口。业务分支可以追加自己的公开接口，例如：
     * <pre>
     * app:
     *   security:
     *     public-paths:
     *       - /api/auth/login
     *       - /api/portal/**          # 展示端公开数据
     *       - /api/transfer/info/**   # 文件提取页
     * </pre>
     *
     * <p>默认值给了 {@code /api/auth/login}，即使 yml 里漏配也不会把登录接口挡住。
     */
    private List<String> publicPaths = new ArrayList<>(List.of("/api/auth/login"));
}
