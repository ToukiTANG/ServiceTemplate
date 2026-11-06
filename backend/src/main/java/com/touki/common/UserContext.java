package com.touki.common;

/**
 * 当前登录用户上下文。
 *
 * <p>基于 {@link ThreadLocal}，生命周期与单次请求绑定：
 * <ul>
 *   <li>{@code JwtInterceptor#preHandle} 中调用 {@link #set(Long)} 写入</li>
 *   <li>{@code JwtInterceptor#afterCompletion} 中调用 {@link #clear()} 清除</li>
 * </ul>
 *
 * <p><b>务必保证 clear 一定执行</b>：Tomcat 的线程是复用的，上一个请求的用户 ID
 * 如果不清理，会被下一个请求读到，造成"看到别人的数据"这种最难排查的越权问题。
 * 放在 afterCompletion 而不是 postHandle，是因为 postHandle 在抛异常时不会执行。
 *
 * <p>局限：该方案只适用于同步的、单线程处理完的请求。当前项目没有异步接口
 * （没有 @Async、没有 CompletableFuture 返回、没有 WebSocket），所以是安全的。
 * 将来引入异步要注意把上下文显式传递过去。
 *
 * <p>为什么不用 Spring Security 的 SecurityContextHolder：本项目没引 Spring Security，
 * 用 20 行 ThreadLocal 就够了，且行为透明好调试。
 */
public final class UserContext {

    /** 当前请求的登录用户 ID。key 为 userId */
    private static final ThreadLocal<Long> CURRENT_USER_ID = new ThreadLocal<>();

    /** 工具类不允许实例化 */
    private UserContext() {
        throw new IllegalStateException("工具类不允许实例化");
    }

    /**
     * 写入当前登录用户。
     *
     * @param userId 用户 ID，由 JWT 解析得到
     */
    public static void set(Long userId) {
        CURRENT_USER_ID.set(userId);
    }

    /**
     * 读取当前登录用户，可能为 null。
     *
     * <p>用于允许匿名访问的接口（如展示端）里"有就区分、没有也照常返回"的场景。
     *
     * @return 用户 ID，未登录时返回 null
     */
    public static Long get() {
        return CURRENT_USER_ID.get();
    }

    /**
     * 读取当前登录用户，缺失时抛异常。
     *
     * <p>用于管理端接口。正常流程下拦截器已保证存在，走到这里的异常属于
     * "拦截器漏配了某条路径"这类程序错误，所以直接抛未登录异常，让它尽早暴露。
     *
     * @return 用户 ID，绝不为 null
     * @throws BizException 当前上下文没有用户时抛出
     */
    public static Long require() {
        Long userId = CURRENT_USER_ID.get();
        if (userId == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        return userId;
    }

    /**
     * 清除上下文。
     *
     * <p>必须在请求结束时调用，防止线程复用导致的串号。
     */
    public static void clear() {
        CURRENT_USER_ID.remove();
    }
}
