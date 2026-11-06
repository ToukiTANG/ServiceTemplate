package com.touki.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * JWT 签发与解析。
 *
 * <p>令牌里只放三样东西：用户 ID、用户名、过期时间。
 * 不放角色和权限——本项目没有权限体系，放了也用不上；真要加，改这里一处即可。
 *
 * <p>算法用 HMAC-SHA256（密钥长度至少 32 字节时 JJWT 会自动选择 HS256）。
 * 这是对称加密：签发和校验用同一个密钥，所以密钥不能泄露，
 * 且一旦更换，所有已签发的令牌立即失效。
 */
@Component
public class JwtUtil {

    /** JWT 签名密钥。由配置注入的门槛：至少 32 字节，否则 JJWT 会抛 WeakKeyException */
    private final SecretKey secretKey;

    /** 令牌有效期（毫秒）。由配置的 expire-hours 换算而来 */
    private final long expireMillis;

    /**
     * 构造器注入密钥与有效期。
     *
     * @param secret      签名密钥，来自配置 {@code app.jwt.secret}
     * @param expireHours 有效期小时数，来自配置 {@code app.jwt.expire-hours}
     */
    public JwtUtil(@Value("${app.jwt.secret}") String secret,
                   @Value("${app.jwt.expire-hours:168}") long expireHours) {
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expireMillis = expireHours * 60 * 60 * 1000;
    }

    /**
     * 签发令牌。
     *
     * @param userId   用户 ID，写入 subject
     * @param username 用户名，写入自定义 claim，方便排查时不用再查库
     * @return 紧凑格式的 JWT 字符串
     */
    public String generate(Long userId, String username) {
        Date now = new Date();
        return Jwts.builder()
                // subject 放用户 ID，解析时用 Long.valueOf 取回
                .subject(String.valueOf(userId))
                .claim("username", username)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + expireMillis))
                .signWith(secretKey)
                .compact();
    }

    /**
     * 解析并校验令牌。
     *
     * <p>校验内容：签名是否有效、是否已过期。任一项不通过都会抛异常，
     * 由调用方（{@link JwtInterceptor}）统一转成 401 响应。
     *
     * @param token 紧凑格式的 JWT 字符串
     * @return 载荷
     * @throws JwtException             签名错误、格式非法或已过期
     * @throws IllegalArgumentException 令牌为空
     */
    public Claims parse(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /**
     * 从载荷中取用户 ID。
     *
     * <p>单独抽出来是为了把 NumberFormatException 这类脏数据问题和 JWT 自身异常区分开，
     * 排查时能一眼看出是"令牌内容不对"还是"令牌本身无效"。
     *
     * @param claims 载荷
     * @return 用户 ID
     * @throws IllegalArgumentException subject 不是合法数字
     */
    public Long getUserId(Claims claims) {
        return Long.valueOf(claims.getSubject());
    }
}
