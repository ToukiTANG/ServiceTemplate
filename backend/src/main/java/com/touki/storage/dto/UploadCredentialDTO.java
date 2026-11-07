package com.touki.storage.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 请求上传签名的入参。
 *
 * <p>这是整个直传流程里调用最频繁的接口：又拍云的签名包含请求方法、路径和长度，
 * 所以**每一个分片都要单独签名**。500MB 文件按 1MB 分片就是 500 次调用，
 * 但每次只交换几 KB 的 JSON，占不了多少带宽。
 *
 * <p>签名接口因此要保持轻量：不查大表、不做业务判断，只校验路径归属。
 */
@Data
public class UploadCredentialDTO {

    /**
     * HTTP 方法。
     *
     * <p>又拍云的上传、分片、合并都用 PUT，只是请求头不同；
     * 删除用 DELETE。传进来是为了让签名和实际请求一致——不一致会被拒。
     */
    @NotBlank(message = "请求方法不能为空")
    private String method;

    /**
     * 存储路径（后端在 prepare 时分配的 storageKey）。
     *
     * <p>服务端会校验它属于一条"上传中"的记录，防止有人拿别人的路径去签名。
     */
    @NotBlank(message = "存储路径不能为空")
    private String storageKey;

    /**
     * 本次请求体的字节数。
     *
     * <p>单次上传是整个文件大小；分片上传是该分片的大小（最后一片通常不足 1MB）；
     * 删除、初始化、合并这类没有请求体的操作传 0。
     */
    @NotNull(message = "请求内容长度不能为空")
    @Min(value = 0, message = "请求内容长度不能为负")
    private Long contentLength;
}
