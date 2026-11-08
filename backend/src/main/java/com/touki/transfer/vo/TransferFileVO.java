package com.touki.transfer.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 文件传输出参。
 *
 * <p>创建和查询共用一个结构：创建时前端要展示提取码，查询时用户已经输过码了，
 * 多返回一个字段不影响使用，反而省掉一个几乎重复的 VO。
 *
 * <p>注意 {@code url} 是又拍云上的直链，**下载不经过我们的服务器**——
 * 服务器带宽只有几 Mbps，几百 MB 的文件经中转要二十多分钟。
 */
@Data
public class TransferFileVO {

    /** 提取码 */
    private String code;

    /** 原始文件名 */
    private String fileName;

    /** 文件大小（字节） */
    private Long fileSize;

    /** 直链地址，域名与协议来自存储配置 */
    private String url;

    /** 过期时间 */
    private LocalDateTime expireTime;

    /** 已下载次数 */
    private Integer downloadCount;
}
