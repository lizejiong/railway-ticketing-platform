package com.lzj.railway.framework.starter.idempotent.executor;

import com.lzj.railway.framework.starter.cache.DistributedCache;
import com.lzj.railway.framework.starter.idempotent.annotation.Idempotent;
import org.aspectj.lang.ProceedingJoinPoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.util.Collections;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * REST 与 MQ 场景共用的 Redis 幂等状态机。
 */
abstract class AbstractRedisIdempotentExecutor implements IdempotentExecutor {

    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractRedisIdempotentExecutor.class);

    static final String PROCESSING_PREFIX = "PROCESSING:";
    static final String COMPLETED = "COMPLETED";
    static final DefaultRedisScript<Long> COMPLETE_IF_OWNER_SCRIPT = new DefaultRedisScript<>("""
            if redis.call('get', KEYS[1]) == ARGV[1] then
                redis.call('psetex', KEYS[1], ARGV[3], ARGV[2])
                return 1
            end
            return 0
            """, Long.class);
    static final DefaultRedisScript<Long> DELETE_IF_OWNER_SCRIPT = new DefaultRedisScript<>("""
            if redis.call('get', KEYS[1]) == ARGV[1] then
                return redis.call('del', KEYS[1])
            end
            return 0
            """, Long.class);

    private final StringRedisTemplate redisTemplate;
    private final Supplier<String> tokenSupplier;

    protected AbstractRedisIdempotentExecutor(DistributedCache distributedCache) {
        this(distributedCache, () -> UUID.randomUUID().toString());
    }

    protected AbstractRedisIdempotentExecutor(
            DistributedCache distributedCache,
            Supplier<String> tokenSupplier) {
        Objects.requireNonNull(distributedCache, "distributedCache must not be null");
        this.redisTemplate = Objects.requireNonNull(
                distributedCache.getRedisTemplate(),
                "redisTemplate must not be null"
        );
        this.tokenSupplier = Objects.requireNonNull(tokenSupplier, "tokenSupplier must not be null");
    }

    @Override
    public final Object execute(
            ProceedingJoinPoint joinPoint,
            Idempotent idempotent,
            String key) throws Throwable {
        long ttlMillis = ttlMillis(idempotent);
        String processingValue = PROCESSING_PREFIX
                + Objects.requireNonNull(tokenSupplier.get(), "idempotent token must not be null");
        Boolean acquired = redisTemplate.opsForValue().setIfAbsent(
                key,
                processingValue,
                ttlMillis,
                TimeUnit.MILLISECONDS
        );
        if (!Boolean.TRUE.equals(acquired)) {
            return handleDuplicate(idempotent);
        }

        try {
            Object result = joinPoint.proceed();
            markCompleted(key, processingValue, ttlMillis);
            return result;
        } catch (Throwable businessException) {
            try {
                deleteIfOwner(key, processingValue);
            } catch (RuntimeException cleanupException) {
                businessException.addSuppressed(cleanupException);
            }
            throw businessException;
        }
    }

    protected abstract Object handleDuplicate(Idempotent idempotent);

    private void markCompleted(String key, String processingValue, long ttlMillis) {
        try {
            Long updated = redisTemplate.execute(
                    COMPLETE_IF_OWNER_SCRIPT,
                    Collections.singletonList(key),
                    processingValue,
                    COMPLETED,
                    String.valueOf(ttlMillis)
            );
            if (!Long.valueOf(1L).equals(updated)) {
                LOGGER.warn("幂等执行已完成，但当前请求已不再持有状态标记，keyHash={}", key.hashCode());
            }
        } catch (RuntimeException stateException) {
            // 业务已经成功，不能再向调用方抛错诱发重试；保留 PROCESSING 直到 TTL 自动释放。
            LOGGER.error("幂等执行已完成，但写入完成状态失败，keyHash={}", key.hashCode(), stateException);
        }
    }

    private void deleteIfOwner(String key, String processingValue) {
        redisTemplate.execute(
                DELETE_IF_OWNER_SCRIPT,
                Collections.singletonList(key),
                processingValue
        );
    }

    private long ttlMillis(Idempotent idempotent) {
        if (idempotent.keyTimeout() <= 0) {
            throw new IllegalArgumentException("@Idempotent.keyTimeout 必须大于 0");
        }
        long ttlMillis = idempotent.timeUnit().toMillis(idempotent.keyTimeout());
        if (ttlMillis <= 0) {
            throw new IllegalArgumentException("@Idempotent.keyTimeout 不能小于 1 毫秒");
        }
        return ttlMillis;
    }
}
