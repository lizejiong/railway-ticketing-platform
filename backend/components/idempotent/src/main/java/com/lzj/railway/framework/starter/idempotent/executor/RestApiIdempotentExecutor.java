package com.lzj.railway.framework.starter.idempotent.executor;

import com.lzj.railway.framework.convention.exception.ClientException;
import com.lzj.railway.framework.starter.cache.DistributedCache;
import com.lzj.railway.framework.starter.idempotent.annotation.Idempotent;
import com.lzj.railway.framework.starter.idempotent.enums.IdempotentScene;

import java.util.function.Supplier;

/**
 * REST API 重复调用处理器，发现重复时向客户端返回明确错误。
 */
public class RestApiIdempotentExecutor extends AbstractRedisIdempotentExecutor {

    public RestApiIdempotentExecutor(DistributedCache distributedCache) {
        super(distributedCache);
    }

    RestApiIdempotentExecutor(DistributedCache distributedCache, Supplier<String> tokenSupplier) {
        super(distributedCache, tokenSupplier);
    }

    @Override
    public IdempotentScene scene() {
        return IdempotentScene.REST_API;
    }

    @Override
    protected Object handleDuplicate(Idempotent idempotent) {
        throw new ClientException(idempotent.message());
    }
}
