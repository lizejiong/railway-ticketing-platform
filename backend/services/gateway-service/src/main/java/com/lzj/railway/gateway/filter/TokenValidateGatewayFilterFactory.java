package com.lzj.railway.gateway.filter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lzj.railway.framework.convention.result.Result;
import com.lzj.railway.framework.starter.user.token.JwtTokenGenerator;
import com.lzj.railway.gateway.common.errorcode.GatewayErrorCode;
import io.jsonwebtoken.JwtException;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * 路由级 JWT 校验过滤器。
 *
 * <p>公开路径和浏览器预检请求直接放行；其余请求必须携带可解析的 Access Token。
 * 过滤器只负责校验，不注入身份请求头，也不查询用户状态或 Redis 会话。</p>
 */
@Component
public class TokenValidateGatewayFilterFactory
        extends AbstractGatewayFilterFactory<TokenValidateGatewayFilterFactory.Config> {

    private final JwtTokenGenerator tokenGenerator;
    private final ObjectMapper objectMapper;

    public TokenValidateGatewayFilterFactory(
            JwtTokenGenerator tokenGenerator,
            ObjectMapper objectMapper) {
        super(Config.class);
        this.tokenGenerator = tokenGenerator;
        this.objectMapper = objectMapper;
    }

    /**
     * 根据当前路由的公开路径配置创建 JWT 校验过滤器。
     *
     * @param config 路由过滤器配置
     * @return 可应用到 Gateway 路由的过滤器
     */
    @Override
    public GatewayFilter apply(Config config) {
        return (exchange, chain) -> {
            ServerHttpRequest request = exchange.getRequest();
            String path = request.getURI().getPath();
            if (HttpMethod.OPTIONS.equals(request.getMethod()) || config.isPublicPath(path)) {
                return chain.filter(exchange);
            }

            String authorization = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
            try {
                tokenGenerator.parseToken(authorization);
                return chain.filter(exchange);
            } catch (JwtException | IllegalArgumentException exception) {
                return unauthorized(exchange.getResponse());
            }
        };
    }

    /** 向客户端写入统一的未授权响应。 */
    private Mono<Void> unauthorized(ServerHttpResponse response) {
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        try {
            byte[] body = objectMapper.writeValueAsBytes(
                    Result.failure(GatewayErrorCode.UNAUTHORIZED));
            DataBuffer buffer = response.bufferFactory().wrap(body);
            return response.writeWith(Mono.just(buffer));
        } catch (JsonProcessingException exception) {
            return response.setComplete();
        }
    }

    /** 当前路由不需要登录即可访问的完整路径集合。 */
    public static class Config {

        private List<String> publicPaths = List.of();

        public List<String> getPublicPaths() {
            return publicPaths;
        }

        public void setPublicPaths(List<String> publicPaths) {
            this.publicPaths = publicPaths == null ? List.of() : List.copyOf(publicPaths);
        }

        boolean isPublicPath(String path) {
            return publicPaths.contains(path);
        }
    }
}
