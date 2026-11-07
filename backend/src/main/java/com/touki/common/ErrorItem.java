package com.touki.common;

/**
 * 错误项的抽象。
 *
 * <p>存在的意义：让通用代码（{@link R}、{@link BizException}）只依赖这个接口，
 * 而不是依赖某个具体的枚举。
 *
 * <p>这样拆的好处是——<b>通用错误码和业务错误码可以分开放</b>：
 * <ul>
 *   <li>{@link ErrorCode}：0、400~500、1xxx 这些所有项目都用的码，放在脚手架里</li>
 *   <li>业务枚举：如 {@code ToolError}、{@code TransferError}，放在各自的业务包下</li>
 * </ul>
 * 于是脚手架的 {@code ErrorCode} 在 main 和业务分支上内容完全相同，
 * 改它只需要动 main 一个分支，merge 时不会冲突。
 *
 * <p>实现类用 Lombok 的 {@code @Getter} 就能满足这两个方法，不用手写。
 */
public interface ErrorItem {

    /** 业务状态码，0 保留给成功 */
    int getCode();

    /** 可直接展示给用户的中文提示 */
    String getMessage();
}
