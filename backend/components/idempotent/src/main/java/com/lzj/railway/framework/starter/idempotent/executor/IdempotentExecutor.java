package com.lzj.railway.framework.starter.idempotent.executor;

import com.lzj.railway.framework.starter.idempotent.annotation.Idempotent;
import com.lzj.railway.framework.starter.idempotent.enums.IdempotentScene;
import org.aspectj.lang.ProceedingJoinPoint;

/**
 * 不同幂等实现的统一执行入口。
 */
public interface IdempotentExecutor {

    IdempotentScene scene();

    Object execute(ProceedingJoinPoint joinPoint, Idempotent idempotent, String key) throws Throwable;
}
