package com.lzj.railway.user.session;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.springframework.util.StringUtils;

@Service
public class RefreshTokenService {

    public static final long REFRESH_TOKEN_TTL_DAYS = 30L;

    private static final String KEY_PREFIX = "railway:user:session:refresh:";
    private final SecureRandom secureRandom = new SecureRandom();
    private final StringRedisTemplate redisTemplate;

    public RefreshTokenService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * 签发不透明 Refresh Token，并仅持久化其 SHA-256 哈希对应的会话 Key。
     *
     * @param userId 已认证用户 ID
     * @param username 写入服务端会话的用户名
     * @return 仅向客户端返回一次的原始 Token 与其有效期（秒）
     */
    public IssuedRefreshToken issue(Long userId, String username) {
        byte[] tokenBytes = new byte[32];
        secureRandom.nextBytes(tokenBytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);
        // Key 使用哈希，避免 Redis 泄露时直接暴露可用的 Refresh Token。
        String key = key(token);
        RefreshSession session = new RefreshSession(userId, username, Instant.now());
        redisTemplate.opsForHash().putAll(key, Map.of(
                "userId", String.valueOf(session.userId()),
                "username", session.username(),
                "createdAt", session.createdAt().toString()));
        // 过期时间就是 Refresh Token 的有效期，登出/换发功能后续通过删除或轮换此 Key 实现。
        redisTemplate.expire(key, REFRESH_TOKEN_TTL_DAYS, TimeUnit.DAYS);
        return new IssuedRefreshToken(token, REFRESH_TOKEN_TTL_DAYS * 24 * 60 * 60);
    }

    /**
     * 一次性消费 Refresh Token 会话。
     *
     * <p>读取完成后只有成功删除 Redis Key 的调用方可以继续换发，避免旧 Token 被重复使用。</p>
     */
    public Optional<RefreshSession> consume(String token) {
        if (!StringUtils.hasText(token)) {
            return Optional.empty();
        }
        String key = key(token);
        Map<Object, Object> values = redisTemplate.opsForHash().entries(key);
        if (values.isEmpty()) {
            return Optional.empty();
        }
        RefreshSession session;
        try {
            session = new RefreshSession(
                    Long.valueOf(String.valueOf(values.get("userId"))),
                    String.valueOf(values.get("username")),
                    Instant.parse(String.valueOf(values.get("createdAt"))));
        } catch (RuntimeException exception) {
            redisTemplate.delete(key);
            return Optional.empty();
        }
        if (!Boolean.TRUE.equals(redisTemplate.delete(key))) {
            return Optional.empty();
        }
        return Optional.of(session);
    }

    /** 撤销 Refresh Token；重复撤销保持幂等。 */
    public void revoke(String token) {
        if (StringUtils.hasText(token)) {
            redisTemplate.delete(key(token));
        }
    }

    /** 将原始 Refresh Token 转换为 Redis 会话 Key。 */
    private String key(String token) {
        return KEY_PREFIX + sha256(token);
    }

    /** 计算 Refresh Token 的 SHA-256 十六进制摘要。 */
    private String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available", ex);
        }
    }

    public record IssuedRefreshToken(String token, long expiresIn) {
    }
}
