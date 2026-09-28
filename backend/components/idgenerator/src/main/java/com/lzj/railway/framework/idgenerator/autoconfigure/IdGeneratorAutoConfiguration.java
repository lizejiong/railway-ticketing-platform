package com.lzj.railway.framework.idgenerator.autoconfigure;

import com.lzj.railway.framework.idgenerator.snowflake.RedisWorkerNodeAssigner;
import com.lzj.railway.framework.idgenerator.snowflake.WorkerNodeAssigner;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * Redis 工作节点编号自动配置。
 * <p>
 * 仅当业务服务已主动提供 {@link StringRedisTemplate} 时生效。
 */
@AutoConfiguration
@ConditionalOnClass(StringRedisTemplate.class)
@ConditionalOnBean(StringRedisTemplate.class)
public class IdGeneratorAutoConfiguration {

    @Bean(destroyMethod = "close")
    @ConditionalOnMissingBean(WorkerNodeAssigner.class)
    public RedisWorkerNodeAssigner redisWorkerNodeAssigner(StringRedisTemplate redisTemplate) {
        return new RedisWorkerNodeAssigner(redisTemplate);
    }
}
