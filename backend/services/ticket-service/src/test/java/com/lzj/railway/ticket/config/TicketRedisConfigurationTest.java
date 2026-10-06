package com.lzj.railway.ticket.config;

import com.lzj.railway.framework.starter.cache.DistributedCache;
import com.lzj.railway.framework.starter.cache.config.CacheAutoConfiguration;
import org.junit.jupiter.api.Test;
import org.redisson.api.RBloomFilter;
import org.redisson.api.RedissonClient;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 验证票务服务引入缓存组件后能够自动装配统一的分布式缓存能力。
 */
class TicketRedisConfigurationTest {

    /**
     * StringRedisTemplate、RedissonClient 和布隆过滤器均存在时应创建 DistributedCache。
     */
    @Test
    void shouldExposeRedisInfrastructureBeans() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(CacheAutoConfiguration.class))
                .withUserConfiguration(TestRedisConfiguration.class)
                .withBean(StringRedisTemplate.class, () -> mock(StringRedisTemplate.class))
                .withBean(RedissonClient.class, () -> mock(RedissonClient.class))
                .run(context -> assertThat(context).hasSingleBean(DistributedCache.class));
    }

    /**
     * 为测试提供具名布隆过滤器，避免自动配置连接真实 Redis。
     */
    @Configuration(proxyBeanMethods = false)
    static class TestRedisConfiguration {

        /**
         * 提供缓存组件所需的布隆过滤器依赖。
         *
         * @return 模拟的布隆过滤器
         */
        @Bean(name = CacheAutoConfiguration.BLOOM_FILTER_BEAN_NAME)
        RBloomFilter<String> railwayCacheBloomFilter() {
            RBloomFilter<String> bloomFilter = mock(RBloomFilter.class);
            when(bloomFilter.tryInit(1_000_000L, 0.001D)).thenReturn(true);
            return bloomFilter;
        }
    }
}
