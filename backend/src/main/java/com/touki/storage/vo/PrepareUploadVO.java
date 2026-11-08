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
     * 上传接口的基础地址，形如 {@code https://v0.api.upyun.com}。
     *
     * <p>由服务端下发而不是前端写死：换存储平台或换接入点时只改配置。
     * 前端拼完整地址的方式是 {@code uploadBaseUrl + credential.path}。
     */
    private String uploadBaseUrl;

    /**
     * 分片任务标识。
     *
     * <p>分片任务的初始化和结束都由服务端代发（浏览器发不了无 body 的 PUT），
     * 但**分片数据本身仍由前端直传**——前端每传一片都要带上这个 uuid。
     */
    private String multiUuid;

    /**
     * 分片大小（字节）。
     *
     * <p>又拍云的断点续传协议把分片大小**固定为 1MB**，不能自定义。
     * 由服务端下发而不是前端写死，是为了哪天换了存储平台能一处改完。
     *
     * <p>用 {@code int} 而不是 {@code long}：全局 Jackson 配置把 Long 序列化成字符串
     * （为了雪花 ID 不丢精度），分片大小这种数值型字段会变成 "1048576"，
     * 前端拿到 string 还得手动转。这里明确用 int 保持数字语义。
     */
    private int chunkSize;

    /**
     * 单文件大小上限（字节）。
     *
     * <p>同样用 int 保持数字语义；上限 500MB 远在 int 范围内（约 2.1GB）。
     */
    private int maxFileSize;
}
