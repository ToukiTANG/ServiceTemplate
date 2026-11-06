package com.touki.common;

import lombok.Getter;

/**
 * 业务异常。
 *
 * <p>凡是可以预期、需要告知用户的失败（"工具不存在"、"原密码不正确"），都抛这个异常，
 * 由 {@link GlobalExceptionHandler} 统一转成响应体。Controller 里不要 try-catch 后返回
 * {@code R.fail(...)}——那样会把失败路径和成功路径混在一起，也容易漏掉日志。
 *
 * <p>注意：本异常对应 HTTP 200 + 业务 code。未登录（401）不走这里，
 * 而是由 JwtInterceptor 直接写响应，因为前端需要靠 HTTP 401 触发跳登录页。
 */
@Getter
public class BizException extends RuntimeException {

    /** 业务状态码 */
    private final int code;

    /**
     * 使用错误码枚举，文案取自枚举。
     *
     * @param errorCode 错误码
     */
    public BizException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.code = errorCode.getCode();
    }

    /**
     * 使用错误码枚举，但覆盖文案。
     *
     * <p>用于需要补充动态信息的场景，如"工具【xxx】不存在"。
     *
     * @param errorCode 错误码
     * @param message   自定义提示
     */
    public BizException(ErrorCode errorCode, String message) {
        super(message);
        this.code = errorCode.getCode();
    }

    /**
     * 完全自定义。
     *
     * @param code    业务状态码
     * @param message 提示信息
     */
    public BizException(int code, String message) {
        super(message);
        this.code = code;
    }

    /**
     * 不采集调用栈。
     *
     * <p>业务异常是"预期内的失败"，每秒可能发生几十次，采集栈帧纯属浪费。
     * 真正需要栈的是未预期异常，那些走 {@link GlobalExceptionHandler} 的兜底分支。
     * 排查业务异常时靠日志里的业务信息，不靠栈。
     */
    @Override
    public synchronized Throwable fillInStackTrace() {
        return this;
    }
}
