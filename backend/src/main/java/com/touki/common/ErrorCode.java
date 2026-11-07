package com.touki.common;

import lombok.Getter;

/**
 * 通用错误码。
 *
 * <p><b>这里只放所有项目都会用到的码</b>：成功、HTTP 语义对齐的通用错误、账号相关。
 * 具体业务的错误码不放这里，各业务包下自己定义枚举（实现 {@link ErrorItem}），
 * 例如 {@code com.touki.transfer.TransferError}。
 *
 * <p>为什么这么拆：脚手架分支和业务分支的 {@code ErrorCode} 内容保持一致，
 * 改它只需要动 main 一个分支；新增业务码时也不用来改这个公共文件。
 *
 * <p>码段约定：
 * <ul>
 *   <li>0、400~500：与 HTTP 语义对齐</li>
 *   <li>1xxx：账号与登录</li>
 *   <li>2xxx 及以上：留给各业务模块自行分配（脚手架不占用）</li>
 * </ul>
 *
 * <p>{@code message} 必须是可直接展示给用户的中文，不要写成 {@code TOOL_NOT_FOUND}
 * 这类给开发看的东西——前端会原样弹出来。
 */
@Getter
public enum ErrorCode implements ErrorItem {

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
    /** 请求方法不支持，例如用 GET 访问只接受 POST 的接口 */
    METHOD_NOT_ALLOWED(405, "请求方法不支持"),
    /** 未预期的异常，具体堆栈只写日志，不返回给前端 */
    SYSTEM_ERROR(500, "系统异常，请稍后重试"),

    // ---------- 账号与登录 ----------
    /** 用户名不存在或密码错误。故意不区分两者，避免被用来枚举用户名 */
    LOGIN_FAILED(1001, "用户名或密码错误"),
    /** 账号被禁用 */
    ACCOUNT_DISABLED(1002, "账号已被禁用"),
    /** 修改密码时原密码校验失败 */
    OLD_PASSWORD_WRONG(1003, "原密码不正确");

    /** 业务状态码 */
    private final int code;

    /** 可直接展示的中文提示 */
    private final String message;

    ErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }
}
