package com.touki.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 文件传输业务配置，对应 {@code app.transfer.*}。
 *
 * <p>改造后这里只剩「有效期」两个参数——文件的存储位置、分片大小、大小上限
 * 全部归脚手架的 {@code app.storage.*} 管了。业务只管自己关心的东西。
 */
@Data
@Component
@ConfigurationProperties(prefix = "app.transfer")
public class TransferProperties {

    /** 默认有效期（小时）。前端不传时用它 */
    private int defaultExpireHours = 24;

    /** 最长有效期（小时）。默认 168 = 7 天，防止有人传长期文件占空间 */
    private int maxExpireHours = 168;
}
