package com.touki.common;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 统一响应体。
 *
 * <p>所有接口（除文件下载外）都返回这个结构，前端只需要判 {@code code} 一个字段：
 * <ul>
 *   <li>{@code code == 0}：成功，业务数据在 {@code data}</li>
 *   <li>{@code code != 0}：失败，{@code message} 是可以直接展示给用户的中文文案</li>
 * </ul>
 *
 * <p>为什么不带 traceId 和时间戳：单人项目排查问题看日志时间足够，
 * 少两个字段前端少两行判断。真需要的话在全局异常处理器里加即可。
 *
 * @param <T> 业务数据类型
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class R<T> {

    /** 业务状态码，0 表示成功，其余见 {@link ErrorCode} */
    private int code;

    /** 提示信息，成功时固定为 "ok"；失败时是可以直接展示的中文 */
    private String message;

    /** 业务数据，失败时为 null */
    private T data;

    /**
     * 成功，无返回数据。用于只关心成败的接口，如删除、上下架切换。
     */
    public static <T> R<T> ok() {
        return new R<>(0, "ok", null);
    }

    /**
     * 成功，带返回数据。
     *
     * @param data 业务数据
     */
    public static <T> R<T> ok(T data) {
        return new R<>(0, "ok", data);
    }

    /**
     * 失败，使用预定义错误码及其标准文案。
     *
     * @param errorCode 错误码枚举，见 {@link ErrorCode}
     */
    public static <T> R<T> fail(ErrorCode errorCode) {
        return new R<>(errorCode.getCode(), errorCode.getMessage(), null);
    }

    /**
     * 失败，自定义文案。
     *
     * <p>只在需要携带动态信息时使用（例如"工具 xxx 不存在"），
     * 能用 {@link #fail(ErrorCode)} 就用它，避免文案散落在各处。
     *
     * @param code    错误码
     * @param message 自定义提示
     */
    public static <T> R<T> fail(int code, String message) {
        return new R<>(code, message, null);
    }
}
