package com.touki.storage;

import com.touki.common.BizException;
import com.touki.config.StorageProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;

/**
 * 又拍云存储客户端：签名、删除、拼接访问地址、生成存储路径。
 *
 * <p><b>这里不负责上传。</b>文件由前端直传又拍云，服务器不接收文件内容——
 * 在 3~4 Mbps 的公网带宽上，几百 MB 的文件走服务器中转既慢又会占满带宽。
 * 服务器只做两件事：发签名（有时效）、记账（{@link UploadFile}）。
 *
 * <p>签名算法（又拍云旧版认证，官方文档「签名认证（旧）」）：
 * <pre>
 * signature = MD5(METHOD & PATH & DATE & CONTENT_LENGTH & MD5(PASSWORD))
 * Authorization: UPYUN {operator}:{signature}
 * </pre>
 * 其中 PATH 含 bucket，形如 {@code /file-touki/2026/11/07/abc.pdf}；
 * GET/DELETE 的 CONTENT_LENGTH 传 0；签名有效期 30 分钟。
 *
 * <p>为什么用旧版而不是 V4：旧版算法简单且官方文档明确，只有 {@code upyun} 官方 JS SDK
 * 才强制走 V4。我们自己是前端直接发 PUT，用旧版即可。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StorageClient {

    /** HTTP 日期格式，等价于 RFC 1123，用固定 Locale 避免服务器语言环境影响 */
    private static final DateTimeFormatter HTTP_DATE =
            DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss 'GMT'", Locale.US);

    /** 存储路径的日期分段：2026/11/07 */
    private static final DateTimeFormatter PATH_DATE = DateTimeFormatter.ofPattern("yyyy/MM/dd");

    /** 随机文件名长度（十六进制字符数） */
    private static final int RANDOM_NAME_LENGTH = 32;

    private static final SecureRandom RANDOM = new SecureRandom();

    private final StorageProperties properties;

    /** 复用一个 HttpClient：每次上传/删除都新建连接池很浪费 */
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    // ==================== 签名 ====================

    /**
     * 为一次对象存储请求生成签名。
     *
     * <p>用的是又拍云 <b>V4</b> 认证（官方 Java SDK 的实现）：
     * <pre>
     * data      = METHOD &amp; PATH &amp; DATE [&amp; Content-MD5]
     * signature = Base64(HMAC-SHA1(key = md5(密码), data))
     * Authorization: UPYUN {operator}:{signature}
     * </pre>
     *
     * <p>两个容易踩的坑：
     * <ul>
     *   <li>V4 <b>不包含 CONTENT_LENGTH</b>。网上大量教程写的是旧版 MD5 算法
     *       （{@code md5(METHOD&PATH&DATE&LENGTH&md5(密码))}），照着写会一直 401
     *       {@code signature error}，而错误信息不会告诉你哪儿错了。</li>
     *   <li>密码要先 md5 再当 HMAC 的 key，不是直接拿原文当 key。</li>
     * </ul>
     *
     * @param method        HTTP 方法：PUT（上传）、DELETE（删除）等
     * @param storageKey    对象在存储中的相对路径
     * @param contentLength 请求体长度。V4 签名不参与计算，保留参数是为了接口语义完整，
     *                      将来若要加 Content-MD5 校验会用得上
     * @return 可直接放进请求头的签名结果
     * @throws BizException 未配置操作员信息时抛出（4006）
     */
    public SignedRequest sign(String method, String storageKey, long contentLength) {
        StorageProperties.Upyun cfg = requireConfig();

        String date = HTTP_DATE.format(ZonedDateTime.now(ZoneOffset.UTC));
        String path = "/" + cfg.getBucket() + "/" + normalizeKey(storageKey);

        // V4：不含 contentLength；不带 Content-MD5 请求头时也不追加 md5 段
        String raw = method + "&" + path + "&" + date;
        String signature = hmacSha1Base64(md5(cfg.getPassword()), raw);

        return new SignedRequest("UPYUN " + cfg.getOperator() + ":" + signature, date, path);
    }

    /**
     * 签名结果。
     *
     * @param authorization 请求头 {@code Authorization} 的值
     * @param date          请求头 {@code Date} 的值（签名的一部分，必须和签名时用的一致）
     * @param path          实际请求的路径，便于前端拼接完整 URL
     */
    public record SignedRequest(String authorization, String date, String path) {
    }

    // ==================== 删除 ====================

    /**
     * 删除对象存储上的文件。
     *
     * <p>用异步删除（{@code x-upyun-async: true}）：官方对同步删除有频率限制，
     * 而我们的清理任务会成批删除。异步删除请求返回 200 即表示已受理，实际删除会稍后执行。
     *
     * @param storageKey 存储相对路径
     * @throws BizException 删除失败（4002）
     */
    public void delete(String storageKey) {
        StorageProperties.Upyun cfg = requireConfig();
        String key = normalizeKey(storageKey);
        String url = "https://" + cfg.getApiHost() + "/" + cfg.getBucket() + "/" + key;

        try {
            SignedRequest signed = sign("DELETE", key, 0);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Authorization", signed.authorization())
                    .header("Date", signed.date())
                    // 批量清理时不受同步删除的频率限制
                    .header("x-upyun-async", "true")
                    .timeout(Duration.ofSeconds(15))
                    .DELETE()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) {
                log.warn("删除对象失败: key={}, status={}, body={}", key, response.statusCode(), response.body());
                throw new BizException(StorageError.DELETE_FAILED);
            }
            log.info("已提交异步删除: key={}", key);
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.error("删除对象异常: key={}", key, e);
            throw new BizException(StorageError.DELETE_FAILED);
        }
    }

    // ==================== URL 与路径 ====================

    /**
     * 拼接文件的公开访问地址。
     *
     * <p>协议和域名都来自配置，所以换正式域名、接入 CDN、切换 http/https 时只改配置——
     * 数据库里存的是相对路径，不需要刷数据。
     *
     * @param storageKey 存储相对路径
     * @return 完整 URL
     */
    public String publicUrl(String storageKey) {
        StorageProperties.Upyun cfg = properties.getUpyun();
        if (!StringUtils.hasText(cfg.getDomain())) {
            return "";
        }
        String scheme = StringUtils.hasText(cfg.getScheme()) ? cfg.getScheme() : "https";
        return scheme + "://" + cfg.getDomain() + "/" + normalizeKey(storageKey);
    }

    /**
     * 生成一个新的存储路径：{@code 2026/11/07/随机32位.ext}。
     *
     * <p>不用原文件名做路径，三个原因：同名文件互相覆盖、中文/特殊字符在 URL 里要编码、
     * 上传 {@code .html} 之类会被 CDN 当页面渲染（存储型 XSS）。
     * 原文件名只存数据库，下载时通过 Content-Disposition 还原。
     *
     * @param fileName 原始文件名，仅用来取扩展名
     * @return 存储相对路径
     */
    public String buildStorageKey(String fileName) {
        String datePart = LocalDate.now().format(PATH_DATE);
        String ext = safeExtension(fileName);
        String random = randomHex(RANDOM_NAME_LENGTH);
        String prefix = properties.getUpyun().getPathPrefix();
        String base = (StringUtils.hasText(prefix) ? trimSlashes(prefix) + "/" : "") + datePart + "/" + random + ext;
        return base;
    }

    /**
     * 规范化存储路径，顺带做路径穿越防护。
     *
     * @param key 原始路径
     * @return 去掉开头斜杠、拒绝 {@code ..} 的路径
     * @throws BizException 路径非法（4005）
     */
    public String normalizeKey(String key) {
        if (!StringUtils.hasText(key)) {
            throw new BizException(StorageError.INVALID_PATH);
        }
        String cleaned = trimSlashes(key.trim());
        if (cleaned.contains("..") || cleaned.contains("\\")) {
            throw new BizException(StorageError.INVALID_PATH);
        }
        return cleaned;
    }

    // ==================== 内部工具 ====================

    /**
     * 取配置，缺关键项时明确报错。
     *
     * <p>比让请求打到又拍云再返回 401 好排查得多。
     */
    private StorageProperties.Upyun requireConfig() {
        StorageProperties.Upyun cfg = properties.getUpyun();
        if (!StringUtils.hasText(cfg.getBucket())
                || !StringUtils.hasText(cfg.getOperator())
                || !StringUtils.hasText(cfg.getPassword())) {
            throw new BizException(StorageError.NOT_CONFIGURED);
        }
        return cfg;
    }

    /**
     * 从文件名里取出「安全的」扩展名。
     *
     * <p>只保留字母数字，长度不超过 10。取不到就返回空串——
     * 宁可不带扩展名，也不能让 {@code .h t m l} 这种脏东西进 URL。
     */
    private String safeExtension(String fileName) {
        if (!StringUtils.hasText(fileName)) {
            return "";
        }
        int dot = fileName.lastIndexOf('.');
        if (dot < 0 || dot == fileName.length() - 1) {
            return "";
        }
        String ext = fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
        if (!ext.matches("[a-z0-9]{1,10}")) {
            return "";
        }
        return "." + ext;
    }

    /** 生成指定长度的十六进制随机串 */
    private String randomHex(int length) {
        byte[] bytes = new byte[length / 2];
        RANDOM.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    /** 去掉首尾斜杠 */
    private String trimSlashes(String s) {
        String r = s;
        while (r.startsWith("/")) {
            r = r.substring(1);
        }
        while (r.endsWith("/")) {
            r = r.substring(0, r.length() - 1);
        }
        return r;
    }

    /** 计算 MD5 的小写十六进制 */
    private String md5(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("MD5");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            // MD5 是 JDK 必备算法，走不到这里；包一层避免到处 throws
            throw new BizException(StorageError.SIGN_FAILED);
        }
    }

    /**
     * HMAC-SHA1 后做 Base64。
     *
     * <p>又拍云 V4 的签名就是这一步：key 是 {@code md5(密码)} 的十六进制字符串，
     * data 是拼接好的请求描述。
     *
     * @param key  HMAC 密钥（md5 后的密码）
     * @param data 待签名的字符串
     * @return Base64 编码的签名
     */
    private String hmacSha1Base64(String key, String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA1"));
            return Base64.getEncoder().encodeToString(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new BizException(StorageError.SIGN_FAILED);
        }
    }
}
