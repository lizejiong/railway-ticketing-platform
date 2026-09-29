package com.lzj.railway.framework.starter.idempotent.executor;

import com.lzj.railway.framework.starter.cache.DistributedCache;
import com.lzj.railway.framework.starter.idempotent.annotation.Idempotent;
import com.lzj.railway.framework.starter.idempotent.enums.IdempotentScene;

import java.util.function.Supplier;

/**
 * MQ 重复消费处理器，发现重复消息时直接跳过业务方法。
 */
public class MqIdempotentExecutor extends AbstractRedisIdempotentExecutor {

    public MqIdempotentExecutor(DistributedCache distributedCache) {
        super(distributedCache);
    }

    MqIdempotentExecutor(DistributedCache distributedCache, Supplier<String> tokenSupplier) {
        super(distributedCache, tokenSupplier);
    }

    @Override
    public IdempotentScene scene() {
        return IdempotentScene.MQ;
    }

    @Override
    protected Object handleDuplicate(Idempotent idempotent) {
        return null;
    }
}
