package com.lzj.railway.framework.starter.user.token;

import com.lzj.railway.framework.starter.base.constant.UserConstant;
import com.lzj.railway.framework.starter.user.config.UserProperties;
import com.lzj.railway.framework.starter.user.core.UserInfoDTO;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtBuilder;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Date;
import java.util.Objects;
import java.util.UUID;

/**
 * Creates and verifies signed JWT credentials for railway users.
 */
public class JwtTokenGenerator {

    private static final int MINIMUM_SECRET_BYTES = 32;

    private final byte[] signingKey;
    private final long expirationMillis;
    private final String issuer;
    private final String tokenPrefix;

    public JwtTokenGenerator(UserProperties.Jwt properties) {
        Objects.requireNonNull(properties, "properties must not be null");
        this.signingKey = validatedSecret(properties.getSecret());
        this.expirationMillis = validatedExpiration(properties.getExpiration()).toMillis();
        this.issuer = requireText(properties.getIssuer(), "issuer must not be blank");
        this.tokenPrefix = properties.getTokenPrefix() == null ? "" : properties.getTokenPrefix();
    }

    public String generateToken(UserInfoDTO user) {
        Objects.requireNonNull(user, "user must not be null");
        String userId = requireText(user.getUserId(), "userId must not be blank");
        Date issuedAt = new Date();
        Date expiresAt = new Date(Math.addExact(issuedAt.getTime(), expirationMillis));

        JwtBuilder builder = Jwts.builder()
                .setId(UUID.randomUUID().toString())
                .setSubject(userId)
                .setIssuer(issuer)
                .setIssuedAt(issuedAt)
                .setExpiration(expiresAt)
                .claim(UserConstant.USER_ID_KEY, userId);
        addClaim(builder, UserConstant.USER_NAME_KEY, user.getUsername());
        addClaim(builder, UserConstant.REAL_NAME_KEY, user.getRealName());

        return builder
                .signWith(SignatureAlgorithm.HS256, signingKey)
                .compact();
    }

    public UserInfoDTO parseToken(String tokenValue) {
        String token = extractToken(tokenValue);
        JwtParser parser = Jwts.parser()
                .setSigningKey(signingKey)
                .requireIssuer(issuer);
        Claims claims = parser.parseClaimsJws(token).getBody();

        return UserInfoDTO.builder()
                .userId(claims.get(UserConstant.USER_ID_KEY, String.class))
                .username(claims.get(UserConstant.USER_NAME_KEY, String.class))
                .realName(claims.get(UserConstant.REAL_NAME_KEY, String.class))
                .token(token)
                .build();
    }

    private String extractToken(String tokenValue) {
        String token = requireText(tokenValue, "token must not be blank").trim();
        if (StringUtils.hasText(tokenPrefix)
                && token.regionMatches(true, 0, tokenPrefix, 0, tokenPrefix.length())) {
            token = token.substring(tokenPrefix.length()).trim();
        }
        return requireText(token, "token must not be blank");
    }

    private void addClaim(JwtBuilder builder, String key, String value) {
        if (StringUtils.hasText(value)) {
            builder.claim(key, value);
        }
    }

    private byte[] validatedSecret(String secret) {
        byte[] secretBytes = requireText(secret, "secret must not be blank")
                .getBytes(StandardCharsets.UTF_8);
        if (secretBytes.length < MINIMUM_SECRET_BYTES) {
            throw new IllegalArgumentException("secret must contain at least 32 bytes");
        }
        return secretBytes;
    }

    private Duration validatedExpiration(Duration expiration) {
        if (expiration == null || expiration.isZero() || expiration.isNegative()) {
            throw new IllegalArgumentException("expiration must be positive");
        }
        return expiration;
    }

    private String requireText(String value, String message) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException(message);
        }
        return value;
    }
}
