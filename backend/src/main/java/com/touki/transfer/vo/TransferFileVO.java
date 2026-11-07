package com.touki.transfer.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 文件信息出参（提取页用，公开接口）。
 *
 * <p><b>这是公开接口的返回值，任何知道提取码的人都能看到。</b>
 * 因此只包含展示与下载必需的信息：
 * <ul>
 *   <li>不返回 {@code storagePath}——那是服务器内部路径，泄露会有安全隐患</li>
 *   <li>不返回 {@code fileHash}——没有用处，反而暴露文件内容特征</li>
 *   <li>不返回下载次数以外的统计信息</li>
 * </ul>
 */
@Data
public class TransferFileVO {

    /** 原始文件名 */
    private String fileName;

    /** 文件大小（字节），前端负责格式化成 MB/GB */
    private Long fileSize;

    /** 过期时间，前端可以显示"还有 12 小时过期" */
    private LocalDateTime expireTime;

    /** 已下载次数 */
    private Integer downloadCount;
}
