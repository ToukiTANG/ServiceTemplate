package com.touki.storage.vo;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 上传登记出参。
 *
 * <p>前端拿到后就可以开始直传了：按 {@code chunkSize} 切片，
 * 每个分片先调 credential 接口拿签名，再 PUT 到对象存储。
 */
@Data
@AllArgsConstructor
public class PrepareUploadVO {

    /** 上传记录 ID，完成时回传 */
    private Long uploadId;

    /** 后端分配好的存储路径，所有分片的请求路径都用它 */
    private String storageKey;

    /**
     * 分片大小（字节）。
     *
     * <p>又拍云的断点续传协议把分片大小**固定为 1MB**，不能自定义。
     * 由服务端下发而不是前端写死，是为了哪天换了存储平台能一处改完。
     */
    private long chunkSize;

    /** 单文件大小上限（字节），前端可据此提前拦掉超大文件 */
    private long maxFileSize;
}
