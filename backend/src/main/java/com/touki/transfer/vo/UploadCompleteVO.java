package com.touki.transfer.vo;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 上传完成出参。
 *
 * <p>前端拿它渲染"上传成功"卡片：提取码 + 复制按钮 + 有效期。
 *
 * <p>这里不返回完整的分享链接：链接里的域名应该由前端用
 * {@code window.location.origin} 拼，后端拼的话得额外维护一个 base-url 配置，
 * 而且本地调试和线上部署时容易拼错域名。
 */
@Data
@AllArgsConstructor
public class UploadCompleteVO {

    /** 6 位提取码，发给对方 */
    private String code;

    /** 文件名，展示用 */
    private String fileName;

    /** 文件大小（字节） */
    private Long fileSize;

    /** 过期时间 */
    private LocalDateTime expireTime;
}
