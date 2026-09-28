package com.lzj.railway.framework.starter.user.config;

import com.lzj.railway.framework.starter.user.filter.UserContextFilter;
import com.lzj.railway.framework.starter.user.token.JwtTokenGenerator;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Auto-configuration for JWT credentials and the current user context.
 */
@AutoConfiguration
@ConditionalOnProperty(prefix = "railway.user.jwt", name = "secret")
@EnableConfigurationProperties(UserProperties.class)
public class UserAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public JwtTokenGenerator jwtTokenGenerator(UserProperties properties) {
        return new JwtTokenGenerator(properties.getJwt());
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    static class ServletFilterConfiguration {

        @Bean
        @ConditionalOnMissingBean
        UserContextFilter userContextFilter(
                JwtTokenGenerator tokenGenerator,
                UserProperties properties) {
            return new UserContextFilter(tokenGenerator, properties.getJwt().getHeaderName());
        }
    }
}
