package com.touki.transfer;

import com.touki.common.ErrorItem;
import lombok.Getter;

/**
 * 文件传输模块的错误码。
 *
 * <p>改造后与分片相关的错误码（分片缺失、会话失效、存储错误等）全部删掉了——
 * 那些逻辑已经交给脚手架的存储模块和又拍云。业务只剩三种失败：
 * 找不到、文件还没传完、有效期不合法。
 */
@Getter
public enum TransferError implements ErrorItem {

    /** 提取码无效、文件不存在、已过期或已删除，统一用这一个码 */
    NOT_FOUND(3001, "文件不存在或已过期"),

    /** 引用的上传记录还没完成上传（status 不是「可用」） */
    UPLOAD_NOT_READY(3003, "文件尚未上传完成"),

    /** 有效期超出允许范围 */
    EXPIRE_INVALID(3004, "有效期不合法");

    /** 业务状态码 */
    private final int code;

    /** 可直接展示的中文提示 */
    private final String message;

    TransferError(int code, String message) {
        this.code = code;
        this.message = message;
    }
}
