package com.touki.auth;

import com.touki.common.ErrorCode;
import com.touki.common.UserContext;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.nio.charset.StandardCharsets;

/**
 * 登录校验拦截器。
 *
 * <p>拦截范围由 WebConfig 配置：{@code /api/**} 全部拦截，白名单放行
 * {@code /api/auth/login} 与 {@code /api/portal/**}。
 *
 * <p>为什么用拦截器而不是 Spring Security：本项目的鉴权需求只有"有没有登录"这一个判断，
 * Security 要配过滤器链、AuthenticationEntryPoint、AccessDeniedHandler 才能达到同样效果，
 * 代码量和心智负担都远超收益。将来真需要角色权限，再上 Security 或 Sa-Token，
 * 本类可以被整体替换，业务代码不受影响。
 *
 * <p>失败时直接写 401 响应，不抛异常：前端 axios 拦截器靠 HTTP 401 触发跳登录页，
 * 如果走全局异常处理器（返回 HTTP 200 + body code=401），前端就得多写一套判断。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtInterceptor implements HandlerInterceptor {

    /** 请求头名称 */
    private static final String AUTH_HEADER = "Authorization";

    /** 令牌前缀，注意后面有一个空格 */
    private static final String BEARER_PREFIX = "Bearer ";

    /**
     * 401 响应体模板。
     *
     * <p>字段与 {@code R} 保持一致（code / message / data），保证前端只有一套解析逻辑。
     * 这里手写 JSON 而不注入 ObjectMapper，原因是 Spring Boot 4 默认使用 Jackson 3，
     * 其 {@code ObjectMapper} 类型为 {@code tools.jackson.databind.ObjectMapper}，
     * 而 classpath 上同时存在 Jackson 2（由 jjwt 引入），注入时容易产生类型歧义。
     * 这里只有一个固定结构，手写字符串反而最可控。
     *
     * <p><b>改动 {@code R} 的字段结构时，务必同步修改这里。</b>
     */
    private static final String UNAUTHORIZED_JSON_TEMPLATE =
            "{\"code\":%d,\"message\":\"%s\",\"data\":null}";

    private final JwtUtil jwtUtil;

    /**
     * 请求进入 Controller 之前校验令牌。
     *
     * @param request  当前请求
     * @param response 当前响应
     * @param handler  即将执行的处理器
     * @return true 放行；false 中断请求（响应已在方法内写完）
     * @throws Exception 写响应流失败时抛出
     */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        // 浏览器的跨域预检请求不带令牌，直接放行，否则预检失败会导致正式请求根本发不出去。
        // 本开发环境走 Vite 代理不产生预检，但部署后前后端分开域名时就需要了。
        if (HttpMethod.OPTIONS.matches(request.getMethod())) {
            return true;
        }

        String header = request.getHeader(AUTH_HEADER);
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            writeUnauthorized(response, "缺少令牌");
            return false;
        }

        // 去掉 "Bearer " 前缀（7 个字符）
        String token = header.substring(BEARER_PREFIX.length());

        try {
            Claims claims = jwtUtil.parse(token);
            UserContext.set(jwtUtil.getUserId(claims));
            return true;
        } catch (Exception e) {
            // 不区分"过期"和"签名错误"对外提示：对用户都是"重新登录"，
            // 区分开只会给攻击者提供信息。日志里保留异常类型便于排查。
            log.debug("令牌校验失败: {} - {}", e.getClass().getSimpleName(), e.getMessage());
            writeUnauthorized(response, "令牌无效或已过期");
            return false;
        }
    }

    /**
     * 请求结束后清理上下文。
     *
     * <p><b>必须放在 afterCompletion 而不是 postHandle</b>：postHandle 在 Controller
     * 抛异常时不会执行，而 afterCompletion 一定会执行。Tomcat 线程是复用的，
     * 漏清理会导致下一个请求读到上一个请求的用户 ID——这是最难排查的一类越权问题。
     *
     * @param request  当前请求
     * @param response 当前响应
     * @param handler  已执行的处理器
     * @param ex       处理过程中抛出的异常，正常结束时为 null
     */
    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        UserContext.clear();
    }

    /**
     * 写出 401 响应。
     *
     * @param response 当前响应
     * @param reason   失败原因，仅写日志用，不返回给前端
     * @throws Exception 写流失败
     */
    private void writeUnauthorized(HttpServletResponse response, String reason) throws Exception {
        log.debug("拒绝访问 {} {}：{}", response.getStatus(), reason);
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        // 必须显式指定 UTF-8，否则中文提示在浏览器里是乱码
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(String.format(UNAUTHORIZED_JSON_TEMPLATE,
                ErrorCode.UNAUTHORIZED.getCode(), ErrorCode.UNAUTHORIZED.getMessage()));
    }
}
