package com.lzj.railway.framework.starter.user.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.http.HttpHeaders;

import java.time.Duration;

/**
 * Configuration for user identity propagation.
 */
@Data
@ConfigurationProperties(prefix = "railway.user")
public class UserProperties {

    private Jwt jwt = new Jwt();

    @Data
    public static class Jwt {

        private String secret;
        private Duration expiration = Duration.ofHours(2);
        private String issuer = "railway-platform";
        private String headerName = HttpHeaders.AUTHORIZATION;
        private String tokenPrefix = "Bearer ";
    }
}
