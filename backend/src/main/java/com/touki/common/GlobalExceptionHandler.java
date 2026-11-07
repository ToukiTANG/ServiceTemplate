package com.touki.common;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.stream.Collectors;

/**
 * 全局异常处理器。
 *
 * <p>三类异常分开处理，原则是：<b>能归因的给准确提示，归不了因的一律兜底</b>。
 *
 * <table border="1">
 *   <caption>异常与响应映射</caption>
 *   <tr><th>异常</th><th>HTTP</th><th>code</th><th>对外消息</th></tr>
 *   <tr><td>BizException</td><td>200</td><td>异常自带</td><td>异常自带的文案</td></tr>
 *   <tr><td>MethodArgumentNotValidException</td><td>400</td><td>400</td><td>字段错误拼接</td></tr>
 *   <tr><td>其他 Exception</td><td>500</td><td>500</td><td>固定兜底文案</td></tr>
 * </table>
 *
 * <p>为什么业务异常返回 HTTP 200：失败原因是业务本身（余额不足、重名），
 * 不是"请求有问题"。同时前端约定只看 body 里的 code，HTTP 状态码只用来区分
 * 401（跳登录）和 500（告警）。两边职责不重叠，改的时候别混。
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 业务异常：预期内的失败，HTTP 200 + 业务 code。
     *
     * <p>日志级别用 warn 而不是 error——这类失败是正常的业务分支，
     * 用 error 会让日志告警失去意义。只记 message 不记栈（本来也没有栈）。
     *
     * @param e 业务异常
     * @param request 当前请求，用于记录出错的接口路径
     */
    @ExceptionHandler(BizException.class)
    public R<Void> handleBizException(BizException e, HttpServletRequest request) {
        log.warn("业务异常 [{}] {} -> code={}, message={}",
                request.getMethod(), request.getRequestURI(), e.getCode(), e.getMessage());
        return R.fail(e.getCode(), e.getMessage());
    }

    /**
     * 参数校验失败：HTTP 400。
     *
     * <p>多个字段同时不合法时，把所有错误用中文分号拼成一条，
     * 而不是只返回第一个——用户改完一个再提交再报下一个，体验很差。
     * 拼接顺序取 getFieldErrors 的原始顺序，即 DTO 里字段的声明顺序。
     *
     * @param e 校验异常
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<R<Void>> handleValidationException(MethodArgumentNotValidException e) {
        String detail = e.getBindingResult().getFieldErrors().stream()
                // 校验消息在 DTO 注解里已写成中文，这里不再加工
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("；"));

        log.warn("参数校验失败: {}", detail);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(R.fail(ErrorCode.PARAM_INVALID.getCode(), detail));
    }

    /**
     * 请求方法不支持：HTTP 405。
     *
     * <p>典型场景是用 GET 访问只接受 POST 的接口。这类请求以前会落到下面的兜底分支、
     * 被当成 500 返回"系统异常"，让调用方误以为是服务端 bug，排查起来很费劲。
     * 单独拎出来既符合 HTTP 语义，也能直接告诉对方到底哪儿错了。
     *
     * @param e 异常，含实际用的方法和该接口支持的方法
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<R<Void>> handleMethodNotSupported(HttpRequestMethodNotSupportedException e) {
        // 支持的方法列表由 Spring 从 @RequestMapping 推断，直接打进日志便于定位
        log.warn("请求方法不支持: {}，该接口支持 {}", e.getMethod(), e.getSupportedHttpMethods());
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
                .body(R.fail(ErrorCode.METHOD_NOT_ALLOWED));
    }

    /**
     * 路径不存在：HTTP 404。
     *
     * <p>Spring 6.1 起，访问不存在的路径抛的是 {@link NoResourceFoundException}，
     * 它属于"预期内"的失败，不该被下面的兜底分支当成 500。
     *
     * @param e 异常，含请求的路径
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<R<Void>> handleNoResourceFound(NoResourceFoundException e) {
        log.debug("路径不存在: {}", e.getResourcePath());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(R.fail(ErrorCode.NOT_FOUND));
    }

    /**
     * 兜底：所有未预期异常，HTTP 500。
     *
     * <p>这里必须记录完整堆栈，否则线上出问题时无从查起；
     * 但对外只返回固定文案，不暴露类名、SQL、路径等内部信息。
     *
     * @param e 未预期异常
     * @param request 当前请求
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<R<Void>> handleException(Exception e, HttpServletRequest request) {
        log.error("未捕获异常 [{}] {}", request.getMethod(), request.getRequestURI(), e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(R.fail(ErrorCode.SYSTEM_ERROR));
    }
}
