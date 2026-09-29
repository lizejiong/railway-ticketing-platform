package com.lzj.railway.framework.starter.idempotent.config;

import com.lzj.railway.framework.starter.cache.DistributedCache;
import com.lzj.railway.framework.starter.idempotent.aspect.IdempotentAspect;
import com.lzj.railway.framework.starter.idempotent.executor.MqIdempotentExecutor;
import com.lzj.railway.framework.starter.idempotent.executor.RestApiIdempotentExecutor;
import com.lzj.railway.framework.starter.idempotent.key.HttpHeaderIdempotentTokenProvider;
import com.lzj.railway.framework.starter.idempotent.key.IdempotentKeyResolver;
import com.lzj.railway.framework.starter.idempotent.key.IdempotentTokenProvider;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class IdempotentAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(IdempotentAutoConfiguration.class));
    private final WebApplicationContextRunner webContextRunner = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    ServletIdempotentAutoConfiguration.class,
                    IdempotentAutoConfiguration.class
            ));

    @Test
    void shouldConfigureIdempotentInfrastructureWhenCacheExists() {
        contextRunner
                .withBean(DistributedCache.class, this::distributedCache)
                .withPropertyValues("railway.idempotent.key-prefix=ticket:idempotent")
                .run(context -> {
                    assertThat(context).hasSingleBean(IdempotentProperties.class);
                    assertThat(context).hasSingleBean(IdempotentKeyResolver.class);
                    assertThat(context).hasSingleBean(RestApiIdempotentExecutor.class);
                    assertThat(context).hasSingleBean(MqIdempotentExecutor.class);
                    assertThat(context).hasSingleBean(IdempotentAspect.class);
                });
    }

    @Test
    void shouldConfigureHttpHeaderTokenProviderForServletApplication() {
        webContextRunner
                .withBean(DistributedCache.class, this::distributedCache)
                .withPropertyValues("railway.idempotent.token-header=X-Request-Token")
                .run(context -> {
                    assertThat(context).hasSingleBean(IdempotentTokenProvider.class);
                    assertThat(context.getBean(IdempotentTokenProvider.class))
                            .isInstanceOf(HttpHeaderIdempotentTokenProvider.class);
                    assertThat(context.getBean(IdempotentProperties.class).getTokenHeader())
                            .isEqualTo("X-Request-Token");
                });
    }

    @Test
    void shouldStayDisabledWithoutCacheOrWhenExplicitlyDisabled() {
        contextRunner.run(context -> assertThat(context).doesNotHaveBean(IdempotentAspect.class));

        contextRunner
                .withBean(DistributedCache.class, () -> mock(DistributedCache.class))
                .withPropertyValues("railway.idempotent.enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean(IdempotentAspect.class));
    }

    private DistributedCache distributedCache() {
        DistributedCache cache = mock(DistributedCache.class);
        when(cache.getRedisTemplate()).thenReturn(mock(StringRedisTemplate.class));
        return cache;
    }
}
