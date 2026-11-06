package com.touki.common;

import lombok.Getter;

/**
 * 业务错误码。
 *
 * <p>不分段、不建索引表——个人项目十几个码足够用，新模块往后顺延即可：
 * <ul>
 *   <li>0、400~500：与 HTTP 语义对齐的通用错误</li>
 *   <li>1001~1099：账号与登录</li>
 *   <li>2001~2099：工具业务</li>
 *   <li>3001 起：下一个业务模块</li>
 * </ul>
 *
 * <p>约定：{@code message} 必须是可直接展示给用户的中文，
 * 不要写成 "TOOL_NOT_FOUND" 这类给开发看的东西——前端会原样弹出来。
 */
@Getter
public enum ErrorCode {

    /** 成功，前端只认这个值 */
    SUCCESS(0, "ok"),

    // ---------- 通用 ----------
    /** 参数校验不通过，由 GlobalExceptionHandler 汇总字段错误后返回 */
    PARAM_INVALID(400, "参数校验失败"),
    /** 未登录或令牌无效/过期，前端收到后跳登录页 */
    UNAUTHORIZED(401, "未登录或登录已过期"),
    /** 已登录但无权限，当前项目暂无角色体系，保留给后续扩展 */
    FORBIDDEN(403, "无权限访问"),
    /** 资源不存在 */
    NOT_FOUND(404, "请求的资源不存在"),
    /** 未预期的异常，具体堆栈只写日志，不返回给前端 */
    SYSTEM_ERROR(500, "系统异常，请稍后重试"),

    // ---------- 账号与登录 ----------
    /** 用户名不存在或密码错误。故意不区分两者，避免被用来枚举用户名 */
    LOGIN_FAILED(1001, "用户名或密码错误"),
    /** 账号被禁用 */
    ACCOUNT_DISABLED(1002, "账号已被禁用"),
    /** 修改密码时原密码校验失败 */
    OLD_PASSWORD_WRONG(1003, "原密码不正确"),

    // ---------- 工具业务 ----------
    /** 按 ID 查不到工具，或被并发删除 */
    TOOL_NOT_FOUND(2001, "工具不存在或已被删除"),
    /** 同一分类下工具名称重复。跨分类允许同名 */
    TOOL_NAME_DUPLICATE(2002, "同一分类下已存在同名工具");

    /** 业务状态码 */
    private final int code;

    /** 可直接展示的中文提示 */
    private final String message;

    ErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }
}
