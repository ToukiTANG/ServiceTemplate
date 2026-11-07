package com.touki.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 对象存储配置，对应 {@code app.storage.*}。
 *
 * <p>脚手架提供「文件存储能力」，业务分支决定文件怎么用。存储凭据只存在后端，
 * 前端永远拿不到操作员密码——它只会拿到一份有时效的签名。
 *
 * <p>换存储平台（又拍云 → 阿里云 OSS）时，改这里的配置和 {@code StorageClient} 的实现即可，
 * 业务代码不受影响。
 */
@Data
@Component
@ConfigurationProperties(prefix = "app.storage")
public class StorageProperties {

    /**
     * 存储类型。
     *
     * <p>目前只有 {@code upyun} 一种实现。留这个字段是为了将来接入其他平台时
     * 能按类型装配不同的 Client，而不必改调用方。
     */
    private String type = "upyun";

    /** 又拍云相关配置 */
    private Upyun upyun = new Upyun();

    @Data
    public static class Upyun {

        /** 服务名（又拍云控制台里的「服务名称」） */
        private String bucket = "";

        /** 操作员名 */
        private String operator = "";

        /**
         * 操作员密码。
         *
         * <p>从环境变量注入（{@code ${UPYUN_PASSWORD:}}），不要写进 yml 提交到仓库。
         * 本机开发填在 {@code application-local.yml} 里。
         */
        private String password = "";

        /**
         * 访问域名，用于拼接文件的公开 URL。
         *
         * <p>现在是又拍云的测试域名，换成正式域名或 CDN 后只改这一行，
         * 数据库里存的是相对路径，不用刷数据。
         */
        private String domain = "";

        /**
         * 访问协议：http 或 https。
         *
         * <p>又拍云的测试域名 {@code *.test.upcdn.net} <b>只支持 HTTP</b>，
         * 走 HTTPS 会 TLS 握手失败（Windows 上报 SEC_E_ILLEGAL_MESSAGE）。
         * 换成正式域名、接入 CDN 并配好证书后，把这里改成 https。
         */
        private String scheme = "https";

        /** REST API 地址，智能选路用 v0 */
        private String apiHost = "v0.api.upyun.com";

        /** 存储路径前缀，如 {@code files/}。为空表示放在根目录下 */
        private String pathPrefix = "";

        /** 单个文件大小上限（字节），默认 500MB */
        private long maxFileSize = 500L * 1024 * 1024;

        /** 上传凭证有效期（秒）。又拍云签名本身有 30 分钟上限，不要超过 */
        private long signExpireSeconds = 600;
    }
}
