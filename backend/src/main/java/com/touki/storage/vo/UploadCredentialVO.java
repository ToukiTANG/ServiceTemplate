package com.touki.storage.vo;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 上传签名出参。
 *
 * <p>前端把这两个头原样加到请求上即可，不需要理解签名算法：
 * <pre>
 * PUT https://v0.api.upyun.com/{bucket}/{path}
 * Authorization: {authorization}
 * Date: {date}
 * Content-Length: {contentLength}
 * </pre>
 */
@Data
@AllArgsConstructor
public class UploadCredentialVO {

    /** 请求头 Authorization 的值 */
    private String authorization;

    /**
     * 请求头 Date 的值。
     *
     * <p>必须原样带上：它是签名的输入之一，前端如果自己生成一个新的日期，
     * 签名就对不上了（表现为 401 signature error，且很难看出原因）。
     */
    private String date;

    /** 实际请求路径，含 bucket，形如 /file-touki/2026/11/07/abc.pdf */
    private String path;
}
