package com.lzj.railway.user.service.registration;

/** 用户注册缓存名称与 Key 生成规则。 */
public final class RegisterCacheKey {

    public static final String USERNAME_BLOOM_FILTER = "railway:user:username";
    private static final String REUSABLE_USERNAME_SET_PREFIX = "railway:user:register:reuse:";
    private static final int REUSABLE_SET_SHARD_COUNT = 16;

    private RegisterCacheKey() {
    }

    /**
     * 将可复用用户名分散到多个 Redis Set，避免单个大 Key。
     * 花括号保证每个分片 Key 在 Redis Cluster 中有稳定槽位。
     */
    public static String reusableUsernameSet(String username) {
        int shard = Math.floorMod(username.hashCode(), REUSABLE_SET_SHARD_COUNT);
        return REUSABLE_USERNAME_SET_PREFIX + "{" + shard + "}";
    }
}
