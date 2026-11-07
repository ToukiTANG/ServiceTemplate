package com.touki.demo;

import com.touki.common.ErrorItem;
import lombok.Getter;

/**
 * 示例模块的错误码。
 *
 * <p>业务错误码放在业务包里，而不是塞进 {@code common.ErrorCode}：
 * 那样脚手架的公共枚举在 main 和业务分支上会不一样，改它就得动两个分支，merge 必冲突。
 *
 * <p>码段约定：2xxx 起给业务模块自由分配，脚手架不占用。
 * 删除示例模块时，这个文件一并删除。
 */
@Getter
public enum DemoError implements ErrorItem {

    /** 按 ID 查不到数据，或被并发删除 */
    NOT_FOUND(2001, "数据不存在或已被删除"),

    /** 名称重复 */
    NAME_DUPLICATE(2002, "名称已存在");

    /** 业务状态码 */
    private final int code;

    /** 可直接展示的中文提示 */
    private final String message;

    DemoError(int code, String message) {
        this.code = code;
        this.message = message;
    }
}
