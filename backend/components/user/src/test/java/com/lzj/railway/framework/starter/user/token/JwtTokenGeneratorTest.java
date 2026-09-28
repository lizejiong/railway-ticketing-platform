package com.lzj.railway.framework.starter.user.token;

import com.lzj.railway.framework.starter.base.constant.UserConstant;
import com.lzj.railway.framework.starter.user.config.UserProperties;
import com.lzj.railway.framework.starter.user.core.UserInfoDTO;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtTokenGeneratorTest {

    private static final String SECRET = "railway-user-component-secret-32-bytes";

    @Test
    void shouldGenerateAndParseAuthenticatedUser() {
        JwtTokenGenerator generator = generator();

        String token = generator.generateToken(user());
        UserInfoDTO parsed = generator.parseToken(token);

        assertThat(parsed.getUserId()).isEqualTo("10001");
        assertThat(parsed.getUsername()).isEqualTo("traveler");
        assertThat(parsed.getRealName()).isEqualTo("Rail User");
        assertThat(parsed.getToken()).isEqualTo(token);
    }

    @Test
    void shouldStripConfiguredBearerPrefixWhenParsing() {
        JwtTokenGenerator generator = generator();
        String token = generator.generateToken(user());

        UserInfoDTO parsed = generator.parseToken("Bearer " + token);

        assertThat(parsed.getToken()).isEqualTo(token);
    }

    @Test
    void shouldRejectTamperedToken() {
        JwtTokenGenerator generator = generator();
        String token = generator.generateToken(user());
        int signatureStart = token.lastIndexOf('.') + 1;
        char replacement = token.charAt(signatureStart) == 'a' ? 'b' : 'a';
        String tampered = token.substring(0, signatureStart)
                + replacement
                + token.substring(signatureStart + 1);

        assertThatThrownBy(() -> generator.parseToken(tampered))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void shouldRejectExpiredToken() {
        JwtTokenGenerator generator = generator();
        String expiredToken = Jwts.builder()
                .setIssuer("railway-test")
                .setExpiration(new Date(System.currentTimeMillis() - 1_000))
                .claim(UserConstant.USER_ID_KEY, "10001")
                .signWith(SignatureAlgorithm.HS256, SECRET.getBytes(StandardCharsets.UTF_8))
                .compact();

        assertThatThrownBy(() -> generator.parseToken(expiredToken))
                .isInstanceOf(ExpiredJwtException.class);
    }

    @Test
    void shouldRejectSecretShorterThan256Bits() {
        UserProperties.Jwt properties = properties();
        properties.setSecret("too-short");

        assertThatThrownBy(() -> new JwtTokenGenerator(properties))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("32 bytes");
    }

    private JwtTokenGenerator generator() {
        return new JwtTokenGenerator(properties());
    }

    private UserProperties.Jwt properties() {
        UserProperties.Jwt properties = new UserProperties.Jwt();
        properties.setSecret(SECRET);
        properties.setExpiration(Duration.ofMinutes(30));
        properties.setIssuer("railway-test");
        properties.setTokenPrefix("Bearer ");
        return properties;
    }

    private UserInfoDTO user() {
        return UserInfoDTO.builder()
                .userId("10001")
                .username("traveler")
                .realName("Rail User")
                .build();
    }
}
