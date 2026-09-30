package com.lzj.railway.gateway.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lzj.railway.framework.starter.user.config.UserProperties;
import com.lzj.railway.framework.starter.user.core.UserInfoDTO;
import com.lzj.railway.framework.starter.user.token.JwtTokenGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TokenValidateGatewayFilterFactoryTest {

    private JwtTokenGenerator tokenGenerator;
    private TokenValidateGatewayFilterFactory factory;

    @BeforeEach
    void setUp() {
        UserProperties.Jwt jwtProperties = new UserProperties.Jwt();
        jwtProperties.setSecret("gateway-test-secret-at-least-32-bytes-long");
        jwtProperties.setExpiration(Duration.ofMinutes(15));
        jwtProperties.setIssuer("railway-platform");
        jwtProperties.setTokenPrefix("Bearer ");
        tokenGenerator = new JwtTokenGenerator(jwtProperties);
        factory = new TokenValidateGatewayFilterFactory(tokenGenerator, new ObjectMapper());
    }

    @Test
    void publicPathShouldPassWithoutToken() {
        MockServerWebExchange exchange = exchange(HttpMethod.POST, "/api/user/login");
        GatewayFilterChain chain = mock(GatewayFilterChain.class);
        when(chain.filter(exchange)).thenReturn(Mono.empty());

        factory.apply(config()).filter(exchange, chain).block();

        verify(chain).filter(exchange);
    }

    @Test
    void protectedPathShouldRejectMissingToken() {
        MockServerWebExchange exchange = exchange(HttpMethod.GET, "/api/user/profile");

        factory.apply(config())
                .filter(exchange, mock(GatewayFilterChain.class))
                .block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(exchange.getResponse().getBodyAsString().block()).contains("G000001");
    }

    @Test
    void validTokenShouldPassProtectedPath() {
        String token = tokenGenerator.generateToken(UserInfoDTO.builder()
                .userId("1001")
                .username("lisi")
                .build());
        MockServerWebExchange exchange = exchangeWithAuthorization(
                HttpMethod.GET, "/api/user/profile", "Bearer " + token);
        GatewayFilterChain chain = mock(GatewayFilterChain.class);
        when(chain.filter(exchange)).thenReturn(Mono.empty());

        factory.apply(config()).filter(exchange, chain).block();

        verify(chain).filter(exchange);
    }

    @Test
    void invalidTokenShouldReturnUnauthorized() {
        MockServerWebExchange exchange = exchangeWithAuthorization(
                HttpMethod.GET, "/api/user/profile", "Bearer invalid-token");

        factory.apply(config())
                .filter(exchange, mock(GatewayFilterChain.class))
                .block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(exchange.getResponse().getBodyAsString().block()).contains("G000001");
    }

    @Test
    void optionsRequestShouldPassWithoutToken() {
        MockServerWebExchange exchange = exchange(HttpMethod.OPTIONS, "/api/user/profile");
        GatewayFilterChain chain = mock(GatewayFilterChain.class);
        when(chain.filter(exchange)).thenReturn(Mono.empty());

        factory.apply(config()).filter(exchange, chain).block();

        verify(chain).filter(exchange);
    }

    private TokenValidateGatewayFilterFactory.Config config() {
        TokenValidateGatewayFilterFactory.Config config =
                new TokenValidateGatewayFilterFactory.Config();
        config.setPublicPaths(List.of("/api/user/register", "/api/user/login"));
        return config;
    }

    private MockServerWebExchange exchange(HttpMethod method, String path) {
        return MockServerWebExchange.from(MockServerHttpRequest.method(method, path).build());
    }

    private MockServerWebExchange exchangeWithAuthorization(
            HttpMethod method,
            String path,
            String authorization) {
        return MockServerWebExchange.from(MockServerHttpRequest.method(method, path)
                .header(HttpHeaders.AUTHORIZATION, authorization)
                .build());
    }
}
