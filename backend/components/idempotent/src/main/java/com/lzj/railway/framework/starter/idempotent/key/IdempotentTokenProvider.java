package com.lzj.railway.framework.starter.idempotent.key;

/**
 * 提供当前调用携带的幂等 Token。
 */
@FunctionalInterface
public interface IdempotentTokenProvider {

    String getToken();
}
