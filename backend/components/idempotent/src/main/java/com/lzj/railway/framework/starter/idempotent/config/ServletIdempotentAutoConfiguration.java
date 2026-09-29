package com.lzj.railway.framework.starter.idempotent.config;

import com.lzj.railway.framework.starter.idempotent.key.HttpHeaderIdempotentTokenProvider;
import com.lzj.railway.framework.starter.idempotent.key.IdempotentTokenProvider;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.web.context.request.RequestContextHolder;

/**
 * Servlet Web 应用中的幂等 Token 自动配置。
 */
@AutoConfiguration(before = IdempotentAutoConfiguration.class)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass({HttpServletRequest.class, RequestContextHolder.class})
@ConditionalOnProperty(prefix = "railway.idempotent", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(IdempotentProperties.class)
public class ServletIdempotentAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public IdempotentTokenProvider idempotentTokenProvider(IdempotentProperties properties) {
        return new HttpHeaderIdempotentTokenProvider(properties.getTokenHeader());
    }
}
