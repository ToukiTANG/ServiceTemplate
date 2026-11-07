package com.touki.storage;

import com.touki.common.ErrorItem;
import lombok.Getter;

/**
 * 文件存储模块的错误码。
 *
 * <p>码段用 4xxx：2xxx/3xxx 留给业务模块（如工具、文件传输），
 * 4xxx 表示「基础设施层」的失败，任何业务接文件上传时都可能碰到。
 */
@Getter
public enum StorageError implements ErrorItem {

    /** 生成上传凭证时失败（配置缺失、密码为空的常见症状） */
    SIGN_FAILED(4001, "生成上传凭证失败"),

    /** 调用对象存储的删除接口失败 */
    DELETE_FAILED(4002, "删除文件失败"),

    /** 记录不存在，或已被清理 */
    FILE_NOT_FOUND(4003, "文件不存在"),

    /** 超过配置的单文件大小上限 */
    TOO_LARGE(4004, "文件超过大小限制"),

    /** 存储路径非法（防路径穿越） */
    INVALID_PATH(4005, "文件路径非法"),

    /** 存储服务未配置（比如忘了填操作员密码） */
    NOT_CONFIGURED(4006, "对象存储未配置");

    /** 业务状态码 */
    private final int code;

    /** 可直接展示的中文提示 */
    private final String message;

    StorageError(int code, String message) {
        this.code = code;
        this.message = message;
    }
}
