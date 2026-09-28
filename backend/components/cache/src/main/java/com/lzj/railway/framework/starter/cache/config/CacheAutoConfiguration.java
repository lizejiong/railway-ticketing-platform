package com.lzj.railway.framework.starter.cache.config;

import com.lzj.railway.framework.starter.cache.DistributedCache;
import com.lzj.railway.framework.starter.cache.RedisDistributedCache;
import com.lzj.railway.framework.starter.cache.key.RedisKeyBuilder;
import org.redisson.api.RBloomFilter;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * Redis 缓存基础设施自动配置。
 */
@AutoConfiguration(afterName = {
        "org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration",
        "org.redisson.spring.starter.RedissonAutoConfiguration",
        "org.redisson.spring.starter.RedissonAutoConfigurationV2"
})
@ConditionalOnClass({StringRedisTemplate.class, RedissonClient.class})
@ConditionalOnBean({StringRedisTemplate.class, RedissonClient.class})
@EnableConfigurationProperties(CacheProperties.class)
public class CacheAutoConfiguration {

    public static final String BLOOM_FILTER_BEAN_NAME = "railwayCacheBloomFilter";

    @Bean
    @ConditionalOnMissingBean
    public RedisKeyBuilder redisKeyBuilder(CacheProperties properties) {
        return new RedisKeyBuilder(properties.getKeyPrefix());
    }

    @Bean(name = BLOOM_FILTER_BEAN_NAME)
    @ConditionalOnMissingBean(name = BLOOM_FILTER_BEAN_NAME)
    public RBloomFilter<String> railwayCacheBloomFilter(
            RedissonClient redissonClient,
            CacheProperties properties) {
        CacheProperties.Bloom bloomProperties = properties.getBloom();
        RBloomFilter<String> bloomFilter = redissonClient.getBloomFilter(bloomProperties.getName());
        bloomFilter.tryInit(
                bloomProperties.getExpectedInsertions(),
                bloomProperties.getFalsePositiveProbability()
        );
        return bloomFilter;
    }

    @Bean
    @ConditionalOnMissingBean(DistributedCache.class)
    public RedisDistributedCache distributedCache(
            StringRedisTemplate redisTemplate,
            RedissonClient redissonClient,
            @Qualifier(BLOOM_FILTER_BEAN_NAME) RBloomFilter<String> bloomFilter,
            CacheProperties properties) {
        return new RedisDistributedCache(
                redisTemplate,
                redissonClient,
                bloomFilter,
                properties.getLockKeyPrefix()
        );
    }
}
