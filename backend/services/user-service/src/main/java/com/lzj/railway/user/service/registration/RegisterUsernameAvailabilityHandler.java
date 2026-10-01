package com.lzj.railway.user.service.registration;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lzj.railway.framework.convention.exception.ClientException;
import com.lzj.railway.framework.designpattern.chain.ChainDecision;
import com.lzj.railway.framework.designpattern.chain.ChainHandler;
import com.lzj.railway.user.common.errorcode.UserErrorCode;
import com.lzj.railway.user.dao.entity.UserDO;
import com.lzj.railway.user.dao.mapper.UserMapper;
import com.lzj.railway.user.dto.request.RegisterRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBloomFilter;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/** 注册责任链第二步：使用 Bloom Filter 和用户名复用集合快速判断用户名可用性。 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RegisterUsernameAvailabilityHandler implements ChainHandler<RegisterRequest> {

    private final RedissonClient redissonClient;
    private final StringRedisTemplate redisTemplate;
    private final UserMapper userMapper;

    /**
     * Bloom 阴性表示用户名大概率从未使用，直接进入后续步骤；Bloom 阳性时先检查注销后可复用集合，
     * 未命中再查询数据库确认，避免 Bloom 假阳性或 Redis 数据缺失导致误拒绝。
     */
    @Override
    public ChainDecision handle(RegisterRequest request) {
        String username = request.getUsername();
        try {
            RBloomFilter<String> bloomFilter = redissonClient.getBloomFilter(RegisterCacheKey.USERNAME_BLOOM_FILTER);
            if (!bloomFilter.contains(username)) {
                return ChainDecision.CONTINUE;
            }
            Boolean reusable = redisTemplate.opsForSet()
                    .isMember(RegisterCacheKey.reusableUsernameSet(username), username);
            if (Boolean.TRUE.equals(reusable)) {
                return ChainDecision.CONTINUE;
            }
        } catch (RuntimeException exception) {
            // 缓存仅用于快速预检；异常时降级到数据库，不能让 Redis 可用性决定能否注册。
            log.warn("注册用户名缓存预检失败，降级查询数据库，username={}", username, exception);
        }
        if (findActiveUser(username) != null) {
            throw new ClientException(UserErrorCode.USERNAME_ALREADY_EXISTS);
        }
        return ChainDecision.CONTINUE;
    }

    /** 用户名可用性在参数校验之后、证件风控之前执行。 */
    @Override
    public int order() {
        return 100;
    }

    /** 按用户名分片键查询当前有效账号。 */
    private UserDO findActiveUser(String username) {
        return userMapper.selectOne(new LambdaQueryWrapper<UserDO>()
                .eq(UserDO::getUsername, username)
                .eq(UserDO::getDeletionTime, 0L)
                .eq(UserDO::getDelFlag, 0)
                .last("LIMIT 1"));
    }
}
