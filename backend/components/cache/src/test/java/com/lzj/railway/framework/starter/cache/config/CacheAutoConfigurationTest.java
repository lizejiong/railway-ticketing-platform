package com.lzj.railway.framework.starter.cache.config;

import com.lzj.railway.framework.starter.cache.DistributedCache;
import com.lzj.railway.framework.starter.cache.RedisDistributedCache;
import com.lzj.railway.framework.starter.cache.key.RedisKeyBuilder;
import org.junit.jupiter.api.Test;
import org.redisson.api.RBloomFilter;
import org.redisson.api.RedissonClient;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SuppressWarnings("unchecked")
class CacheAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(CacheAutoConfiguration.class));

    @Test
    void shouldConfigureCacheInfrastructureAndBindProperties() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        RedissonClient redissonClient = mock(RedissonClient.class);
        RBloomFilter<String> bloomFilter = mock(RBloomFilter.class);
        when(redissonClient.<String>getBloomFilter("ticket:bloom")).thenReturn(bloomFilter);

        contextRunner
                .withBean(StringRedisTemplate.class, () -> redisTemplate)
                .withBean(RedissonClient.class, () -> redissonClient)
                .withPropertyValues(
                        "railway.cache.key-prefix=ticketing",
                        "railway.cache.lock-key-prefix=ticket:lock:",
                        "railway.cache.bloom.name=ticket:bloom",
                        "railway.cache.bloom.expected-insertions=2000",
                        "railway.cache.bloom.false-positive-probability=0.02"
                )
                .run(context -> {
                    assertThat(context).hasSingleBean(CacheProperties.class);
                    assertThat(context).hasSingleBean(RedisKeyBuilder.class);
                    assertThat(context).hasSingleBean(DistributedCache.class);
                    assertThat(context.getBean(DistributedCache.class))
                            .isInstanceOf(RedisDistributedCache.class);
                    assertThat(context.getBean(RedisKeyBuilder.class).build("train", "G123"))
                            .isEqualTo("ticketing:train:G123");
                    assertThat(context).getBean("railwayCacheBloomFilter").isSameAs(bloomFilter);
                    verify(bloomFilter).tryInit(2_000L, 0.02D);
                });
    }

    @Test
    void shouldStayDisabledWhenRedisClientIsMissing() {
        contextRunner.run(context -> {
            assertThat(context).doesNotHaveBean(DistributedCache.class);
            assertThat(context).doesNotHaveBean(RedisKeyBuilder.class);
        });
    }

    @Test
    void shouldBackOffForApplicationBeans() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        RedissonClient redissonClient = mock(RedissonClient.class);
        RBloomFilter<String> bloomFilter = mock(RBloomFilter.class);
        DistributedCache customCache = mock(DistributedCache.class);
        RedisKeyBuilder customKeyBuilder = new RedisKeyBuilder("custom");

        contextRunner
                .withBean(StringRedisTemplate.class, () -> redisTemplate)
                .withBean(RedissonClient.class, () -> redissonClient)
                .withBean("railwayCacheBloomFilter", RBloomFilter.class, () -> bloomFilter)
                .withBean(DistributedCache.class, () -> customCache)
                .withBean(RedisKeyBuilder.class, () -> customKeyBuilder)
                .run(context -> {
                    assertThat(context.getBean(DistributedCache.class)).isSameAs(customCache);
                    assertThat(context.getBean(RedisKeyBuilder.class)).isSameAs(customKeyBuilder);
                    assertThat(context.getBean("railwayCacheBloomFilter")).isSameAs(bloomFilter);
                });
    }
}
