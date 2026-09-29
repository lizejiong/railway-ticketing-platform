package com.lzj.railway.framework.starter.idempotent.aspect;

import com.lzj.railway.framework.starter.idempotent.annotation.Idempotent;
import com.lzj.railway.framework.starter.idempotent.enums.IdempotentScene;
import com.lzj.railway.framework.starter.idempotent.executor.IdempotentExecutor;
import com.lzj.railway.framework.starter.idempotent.key.IdempotentKeyResolver;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.aop.support.AopUtils;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;

import java.lang.reflect.Method;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 拦截 {@link Idempotent} 方法并将调用路由给对应类型的幂等执行器。
 */
@Aspect
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class IdempotentAspect {

    private final Map<IdempotentScene, IdempotentExecutor> executors;
    private final IdempotentKeyResolver keyResolver;

    public IdempotentAspect(List<IdempotentExecutor> executors, IdempotentKeyResolver keyResolver) {
        if (executors == null || executors.isEmpty()) {
            throw new IllegalArgumentException("至少需要一个幂等执行器");
        }
        this.executors = indexExecutors(executors);
        this.keyResolver = Objects.requireNonNull(keyResolver, "keyResolver must not be null");
    }

    @Around("@annotation(idempotent)")
    public Object execute(ProceedingJoinPoint joinPoint, Idempotent idempotent) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = AopUtils.getMostSpecificMethod(
                signature.getMethod(),
                joinPoint.getTarget().getClass()
        );
        IdempotentExecutor executor = executors.get(idempotent.scene());
        if (executor == null) {
            throw new IllegalStateException("未找到幂等场景执行器: " + idempotent.scene());
        }
        String key = keyResolver.resolve(method, joinPoint.getArgs(), idempotent);
        return executor.execute(joinPoint, idempotent, key);
    }

    private Map<IdempotentScene, IdempotentExecutor> indexExecutors(List<IdempotentExecutor> candidates) {
        Map<IdempotentScene, IdempotentExecutor> indexed = new EnumMap<>(IdempotentScene.class);
        for (IdempotentExecutor candidate : candidates) {
            IdempotentExecutor previous = indexed.put(candidate.scene(), candidate);
            if (previous != null) {
                throw new IllegalStateException("存在重复的幂等场景执行器: " + candidate.scene());
            }
        }
        return indexed;
    }
}
