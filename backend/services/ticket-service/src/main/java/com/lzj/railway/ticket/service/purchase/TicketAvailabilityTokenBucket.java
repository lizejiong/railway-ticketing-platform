package com.lzj.railway.ticket.service.purchase;

import com.alibaba.fastjson2.JSON;
import com.lzj.railway.ticket.common.constant.TicketCacheKey;
import com.lzj.railway.ticket.dao.mapper.SeatMapper;
import com.lzj.railway.ticket.dao.mapper.dto.SeatRemainingDTO;
import lombok.RequiredArgsConstructor;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scripting.support.ResourceScriptSource;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 列车可用余票令牌桶。
 *
 * <p>令牌桶以列车为 Hash Key、以“可售区间_席别”为 Field。Lua 脚本会先检查整个行程是否有票，
 * 再一次性扣减全部受影响的可售区间，防止并发请求在检查与扣减之间超卖。</p>
 */
@Component
@RequiredArgsConstructor
public class TicketAvailabilityTokenBucket {

    private static final String TAKE_SCRIPT_PATH = "lua/ticket_availability_token_bucket.lua";
    private static final String ROLLBACK_SCRIPT_PATH = "lua/ticket_availability_rollback_token_bucket.lua";

    private final SeatMapper seatMapper;
    private final TrainRouteService trainRouteService;
    private final StringRedisTemplate redisTemplate;
    private final RedissonClient redissonClient;

    /**
     * 获取指定直达区间各席别的当前可售数量。
     *
     * @param trainId 列车主键
     * @param departure 区间出发站名称
     * @param arrival 区间到达站名称
     * @return 按席别分组的余票数量
     */
    public Map<Integer, Integer> getRemainingTickets(Long trainId, String departure, String arrival) {
        ensureInitialized(trainId);
        Map<Object, Object> entries = redisTemplate.opsForHash().entries(TicketCacheKey.remaining(trainId));
        if (entries == null || entries.isEmpty()) {
            return Map.of();
        }
        String fieldPrefix = departure + '_' + arrival + '_';
        Map<Integer, Integer> result = new LinkedHashMap<>();
        entries.forEach((field, count) -> {
            String fieldName = field.toString();
            if (fieldName.startsWith(fieldPrefix)) {
                Integer seatType = Integer.valueOf(fieldName.substring(fieldPrefix.length()));
                result.put(seatType, Integer.valueOf(count.toString()));
            }
        });
        return result;
    }

    /**
     * 原子预扣一次购票行程所需的全部区间令牌。
     *
     * @param trainId 列车主键
     * @param departure 购票出发站名称
     * @param arrival 购票到达站名称
     * @param affectedSegments 会受本次购票影响的全部可售区间
     * @param seatTypeCounts 每种席别的购票数量
     * @return true 表示预扣成功；false 表示余票不足
     */
    public boolean takeTokenFromBucket(
            Long trainId,
            String departure,
            String arrival,
            List<TrainRouteSegment> affectedSegments,
            Map<Integer, Long> seatTypeCounts) {
        ensureInitialized(trainId);
        if (affectedSegments.isEmpty() || seatTypeCounts.isEmpty()) {
            return false;
        }
        Long result = redisTemplate.execute(createScript(TAKE_SCRIPT_PATH, Long.class),
                List.of(TicketCacheKey.remaining(trainId)),
                JSON.toJSONString(toSeatTypeCounts(seatTypeCounts)),
                JSON.toJSONString(affectedSegments),
                departure + '_' + arrival);
        return Objects.equals(result, 1L);
    }

    /**
     * 将已预扣的全部途经区间令牌原子回补。
     *
     * @param trainId 列车主键
     * @param affectedSegments 需要回补的全部可售区间
     * @param seatTypeCounts 每种席别的回补数量
     */
    public void rollbackInBucket(
            Long trainId,
            List<TrainRouteSegment> affectedSegments,
            Map<Integer, Long> seatTypeCounts) {
        if (affectedSegments.isEmpty() || seatTypeCounts.isEmpty()) {
            return;
        }
        Long result = redisTemplate.execute(createScript(ROLLBACK_SCRIPT_PATH, Long.class),
                List.of(TicketCacheKey.remaining(trainId)),
                JSON.toJSONString(toSeatTypeCounts(seatTypeCounts)),
                JSON.toJSONString(affectedSegments));
        if (!Objects.equals(result, 1L)) {
            throw new IllegalStateException("余票令牌回补失败");
        }
    }

    /**
     * 懒加载初始化列车所有可售区间的余票令牌。
     *
     * @param trainId 列车主键
     */
    public void ensureInitialized(Long trainId) {
        String bucketKey = TicketCacheKey.remaining(trainId);
        if (Boolean.TRUE.equals(redisTemplate.hasKey(bucketKey))) {
            return;
        }
        RLock lock = redissonClient.getLock(TicketCacheKey.lock(bucketKey));
        lock.lock();
        try {
            if (Boolean.TRUE.equals(redisTemplate.hasKey(bucketKey))) {
                return;
            }
            Map<Object, Object> tokens = new LinkedHashMap<>();
            for (TrainRouteSegment routeSegment : trainRouteService.listAllSaleSegments(trainId)) {
                List<SeatRemainingDTO> seatCounts = seatMapper.countAvailableSeatsByTrainIds(
                        List.of(trainId), routeSegment.departure(), routeSegment.arrival());
                for (SeatRemainingDTO seatCount : seatCounts) {
                    tokens.put(TicketCacheKey.remainingField(
                                    routeSegment.departure(), routeSegment.arrival(), seatCount.seatType()),
                            String.valueOf(seatCount.remainingTickets()));
                }
            }
            if (!tokens.isEmpty()) {
                redisTemplate.opsForHash().putAll(bucketKey, tokens);
            }
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    /**
     * 将席别数量映射转换为 Lua 脚本约定的 JSON 数组。
     *
     * @param seatTypeCounts 席别数量映射
     * @return 可序列化的席别数量列表
     */
    private List<SeatTypeCount> toSeatTypeCounts(Map<Integer, Long> seatTypeCounts) {
        return seatTypeCounts.entrySet().stream()
                .map(entry -> new SeatTypeCount(entry.getKey(), entry.getValue()))
                .toList();
    }

    /**
     * 读取类路径 Lua 文件并创建 Redis 脚本对象。
     *
     * @param path 类路径资源位置
     * @param resultType 脚本返回类型
     * @param <T> 脚本返回泛型
     * @return Redis 脚本对象
     */
    private <T> DefaultRedisScript<T> createScript(String path, Class<T> resultType) {
        DefaultRedisScript<T> script = new DefaultRedisScript<>();
        script.setScriptSource(new ResourceScriptSource(new ClassPathResource(path)));
        script.setResultType(resultType);
        return script;
    }

    /**
     * Lua 脚本使用的单个席别数量结构。
     *
     * @param seatType 席别编码
     * @param count 数量
     */
    private record SeatTypeCount(Integer seatType, Long count) {
    }
}
