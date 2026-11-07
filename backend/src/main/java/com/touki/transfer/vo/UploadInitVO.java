package com.touki.transfer.vo;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

/**
 * 上传初始化出参。
 *
 * <p>前端拿到后要做三件事：按 {@code chunkSize} 切片、跳过 {@code uploadedChunks} 里已有的分片、
 * 按顺序把剩余分片 POST 到 {@code /api/transfer/chunk}。
 */
@Data
@AllArgsConstructor
public class UploadInitVO {

    /**
     * 本次上传的标识。
     *
     * <p>后续每个分片请求都要带上它。同一个文件在断点续传时会复用同一个值，
     * 所以服务端能靠它找回已上传的分片。
     */
    private String uploadId;

    /**
     * 已经上传成功的分片索引。
     *
     * <p>首次上传是空数组；断点续传时会返回之前传完的部分。
     * 前端必须跳过这些索引，否则会重复上传、浪费流量。
     */
    private List<Integer> uploadedChunks;

    /** 分片大小（字节）。由服务端下发，避免前后端各写一份常量导致不一致 */
    private long chunkSize;

    /** 分片总数，前端可用来算进度 */
    private int totalChunks;
}
