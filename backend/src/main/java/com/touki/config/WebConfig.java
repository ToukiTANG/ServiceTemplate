package com.touki.config;

import com.touki.auth.JwtInterceptor;
import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import tools.jackson.databind.ext.javatime.ser.LocalDateSerializer;
import tools.jackson.databind.ext.javatime.ser.LocalDateTimeSerializer;
import tools.jackson.databind.module.SimpleModule;
import tools.jackson.databind.ser.std.ToStringSerializer;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Web 层配置：拦截器、密码编码器、JSON 序列化规则。
 *
 * <p>这三样东西都"只需要配一次，之后全项目受益"，所以集中在这里，
 * 避免在实体或 VO 上零散地加注解——那样每新增一个类都要记得加一遍，
 * 漏一个就出前后端不一致的怪问题。
 *
 * <p><b>关于 Jackson 版本（重要）</b>：Spring Boot 4 默认使用 <b>Jackson 3</b>，
 * 包名从 {@code com.fasterxml.jackson} 变成了 {@code tools.jackson}，
 * 定制入口也从 {@code Jackson2ObjectMapperBuilderCustomizer} 换成了
 * {@link JsonMapperBuilderCustomizer}。
 * 网上大量 Boot 2/3 的教程里写的 {@code Jackson2ObjectMapperBuilderCustomizer}
 * 在 Boot 4 里根本不存在，照着抄会直接编译失败。
 * 另外 Jackson 3 已内置 java.time 支持，不需要再引 {@code jackson-datatype-jsr310}。
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    /** 统一的时间格式，前后端约定死，不在各处自定义 */
    private static final DateTimeFormatter DATE_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /** 统一的日期格式 */
    private static final DateTimeFormatter DATE_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final JwtInterceptor jwtInterceptor;

    /**
     * 构造器注入。
     *
     * <p>只注入拦截器一个依赖——{@link PasswordEncoder} 和 Jackson 定制器
     * 都由本类自己产出，不能再注进来，否则会形成循环依赖。
     *
     * @param jwtInterceptor 登录校验拦截器
     */
    public WebConfig(JwtInterceptor jwtInterceptor) {
        this.jwtInterceptor = jwtInterceptor;
    }

    /**
     * 注册登录拦截器。
     *
     * <p>采用"全拦 + 白名单"而不是"只拦管理端前缀"：
     * 前者新增任何接口默认都是受保护的（安全默认）；后者一旦忘了把新接口
     * 放到约定的前缀下，接口就会裸奔，且这种问题很难在测试中发现。
     *
     * <p>白名单当前只有登录接口一项。
     * 将来要做公开接口（比如展示页需要读数据），按下面的形式追加一行即可：
     * {@code .excludePathPatterns("/api/public/**")}，
     * 并把公开接口统一放在那个前缀下，让"公开"这件事在路径上一眼可见。
     *
     * <p>Swagger 相关路径不在 {@code /api/**} 下，天然不受影响。
     *
     * @param registry 拦截器注册表
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(jwtInterceptor)
                .addPathPatterns("/api/**")
                // 登录接口本身当然不能要求登录
                .excludePathPatterns("/api/auth/login");
    }

    /**
     * 密码编码器。
     *
     * <p>用 BCrypt：自带随机盐，同一个密码每次 encode 出来的密文都不同；
     * 计算耗时约 100ms，天然抵抗暴力破解。
     * 不要用 MD5/SHA 存密码——那是哈希不是加密，没有盐，彩虹表一查就出来。
     *
     * <p>本类只用到 {@code spring-security-crypto} 这个独立小包，
     * 没有引入完整的 Spring Security 框架。
     *
     * @return 密码编码器
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * JSON 序列化规则（Jackson 3 写法）。
     *
     * <p>两条规则，每条都对应一个真实踩过的坑：
     * <ol>
     *   <li><b>Long 转字符串</b>：雪花 ID 是 19 位，超过 JS Number 的安全整数范围（2^53）。
     *       直接返回数字，前端拿到的是被截断后的值（末几位变 0），
     *       表现为"列表能显示但按 ID 操作全部失败"。
     *       全局转成字符串后，前端原样传回来即可。</li>
     *   <li><b>时间固定格式</b>：默认是 ISO 带 T 的格式（{@code 2026-11-06T10:30:00}），
     *       前端还要再格式化一次。统一在这里定死，前端直接用。</li>
     * </ol>
     *
     * <p>没有配置反序列化器：入参 DTO 里没有时间字段（时间由服务端生成），
     * 等真有接口需要接收时间时再加 {@code LocalDateTimeDeserializer}，
     * 保持配置最小化。
     *
     * @return Jackson 3 的 Mapper 定制器
     */
    @Bean
    public JsonMapperBuilderCustomizer jsonCustomizer() {
        return builder -> {
            SimpleModule module = new SimpleModule();

            // 基本类型 long 与包装类型 Long 都要处理，只处理一个会漏掉实体里的部分字段
            module.addSerializer(Long.class, ToStringSerializer.instance);
            module.addSerializer(Long.TYPE, ToStringSerializer.instance);

            // Jackson 3 内置 java.time 序列化器，无需额外依赖
            module.addSerializer(LocalDateTime.class, new LocalDateTimeSerializer(DATE_TIME_FORMATTER));
            module.addSerializer(LocalDate.class, new LocalDateSerializer(DATE_FORMATTER));

            builder.addModule(module);
        };
    }
}
