package com.touki.tool;

import com.touki.common.ErrorItem;
import lombok.Getter;

/**
 * 工具模块的错误码。
 *
 * <p>业务错误码放在业务包里，而不是塞进 {@code common.ErrorCode}：
 * 那样脚手架的公共枚举在 main 和业务分支上会不一样，改它就得动两个分支，merge 必冲突。
 *
 * <p>码段约定：2xxx 给工具模块，3xxx 给文件传输，后面的业务往后顺延。
 */
@Getter
public enum ToolError implements ErrorItem {

    /** 按 ID 查不到工具，或被并发删除 */
    NOT_FOUND(2001, "工具不存在或已被删除"),

    /** 同一分类下工具名称重复。跨分类允许同名 */
    NAME_DUPLICATE(2002, "同一分类下已存在同名工具");

    /** 业务状态码 */
    private final int code;

    /** 可直接展示的中文提示 */
    private final String message;

    ToolError(int code, String message) {
        this.code = code;
        this.message = message;
    }
}
