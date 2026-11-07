package com.touki.transfer;

import com.touki.common.ErrorItem;
import lombok.Getter;

/**
 * 文件传输模块的错误码。
 *
 * <p>与 {@code common.ErrorCode} 的分工：通用码（0/4xx/1xxx）在脚手架里，
 * 3xxx 这类只有传输业务才用得上的码放在本模块内。
 * 新增一个传输相关的错误，只改这个文件，不需要动脚手架。
 */
@Getter
public enum TransferError implements ErrorItem {

    /** 提取码无效、文件不存在、已过期或已删除，统一用这一个码 */
    NOT_FOUND(3001, "文件不存在或已过期"),

    /** 文件超过大小上限 */
    TOO_LARGE(3002, "文件超过大小限制"),

    /** 分片缺失或合并后校验不一致 */
    CHUNK_MISSING(3003, "分片不完整，请重新上传"),

    /** 上传会话不存在或已失效（比如服务重启后记录被清理） */
    SESSION_INVALID(3004, "上传会话已失效，请重新开始"),

    /** 磁盘写入失败 */
    STORAGE_ERROR(3005, "文件保存失败，请稍后重试"),

    /** 路径非法（防路径穿越） */
    INVALID_PATH(3006, "文件路径非法"),

    /** 磁盘上的文件丢失（记录还在但文件被手工删了） */
    FILE_MISSING(3007, "文件已损坏或丢失");

    /** 业务状态码 */
    private final int code;

    /** 可直接展示的中文提示 */
    private final String message;

    TransferError(int code, String message) {
        this.code = code;
        this.message = message;
    }
}
