package com.lzj.railway.framework.starter.idempotent.config;

import com.lzj.railway.framework.starter.cache.DistributedCache;
import com.lzj.railway.framework.starter.idempotent.aspect.IdempotentAspect;
import com.lzj.railway.framework.starter.idempotent.executor.IdempotentExecutor;
import com.lzj.railway.framework.starter.idempotent.executor.MqIdempotentExecutor;
import com.lzj.railway.framework.starter.idempotent.executor.RestApiIdempotentExecutor;
import com.lzj.railway.framework.starter.idempotent.key.IdempotentKeyResolver;
import com.lzj.railway.framework.starter.idempotent.key.IdempotentTokenProvider;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import java.util.List;

/**
 * Redis 幂等组件自动配置。
 */
@AutoConfiguration(afterName = "com.lzj.railway.framework.starter.cache.config.CacheAutoConfiguration")
@ConditionalOnClass(Aspect.class)
@ConditionalOnBean(DistributedCache.class)
@ConditionalOnProperty(prefix = "railway.idempotent", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(IdempotentProperties.class)
public class IdempotentAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public IdempotentKeyResolver idempotentKeyResolver(
            IdempotentProperties properties,
            ObjectProvider<IdempotentTokenProvider> tokenProvider) {
        return new IdempotentKeyResolver(
                properties.getKeyPrefix(),
                tokenProvider.getIfAvailable()
        );
    }

    @Bean
    @ConditionalOnMissingBean
    public RestApiIdempotentExecutor restApiIdempotentExecutor(DistributedCache distributedCache) {
        return new RestApiIdempotentExecutor(distributedCache);
    }

    @Bean
    @ConditionalOnMissingBean
    public MqIdempotentExecutor mqIdempotentExecutor(DistributedCache distributedCache) {
        return new MqIdempotentExecutor(distributedCache);
    }

    @Bean
    @ConditionalOnMissingBean
    public IdempotentAspect idempotentAspect(
            List<IdempotentExecutor> executors,
            IdempotentKeyResolver keyResolver) {
        return new IdempotentAspect(executors, keyResolver);
    }
}
