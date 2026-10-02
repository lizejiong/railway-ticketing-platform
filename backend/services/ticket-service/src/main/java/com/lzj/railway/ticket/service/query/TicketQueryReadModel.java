package com.lzj.railway.ticket.service.query;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.lzj.railway.ticket.common.constant.TicketCacheKey;
import com.lzj.railway.ticket.dao.entity.TrainDO;
import com.lzj.railway.ticket.dao.entity.TrainStationPriceDO;
import com.lzj.railway.ticket.dao.entity.TrainStationRelationDO;
import com.lzj.railway.ticket.dao.mapper.SeatMapper;
import com.lzj.railway.ticket.dao.mapper.TrainMapper;
import com.lzj.railway.ticket.dao.mapper.TrainStationPriceMapper;
import com.lzj.railway.ticket.dao.mapper.TrainStationRelationMapper;
import com.lzj.railway.ticket.dao.mapper.dto.SeatRemainingDTO;
import lombok.RequiredArgsConstructor;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 票务查询的 Redis 读模型。
 *
 * <p>车次区间、列车和票价均采用缓存旁路模式。缓存未命中时由 Redisson 锁保护回源，
 * 余票使用按列车和区间划分的 Hash，查询接口只读取该 Hash。</p>
 */
@Component
@RequiredArgsConstructor
public class TicketQueryReadModel {

    private final TrainStationRelationMapper trainStationRelationMapper;
    private final TrainMapper trainMapper;
    private final TrainStationPriceMapper trainStationPriceMapper;
    private final SeatMapper seatMapper;
    private final StringRedisTemplate redisTemplate;
    private final RedissonClient redissonClient;

    /**
     * 获取指定地区对下所有列车区间。
     *
     * @param fromRegion 出发地区名称
     * @param toRegion 到达地区名称
     * @return 区间缓存记录
     */
    public List<TicketRouteCacheDTO> findRoutes(String fromRegion, String toRegion) {
        String cacheKey = TicketCacheKey.regionRoute(fromRegion, toRegion);
        List<TicketRouteCacheDTO> routes = readRoutes(cacheKey);
        if (!routes.isEmpty()) {
            return routes;
        }
        RLock lock = redissonClient.getLock(TicketCacheKey.lock(cacheKey));
        lock.lock();
        try {
            routes = readRoutes(cacheKey);
            if (routes.isEmpty()) {
                List<TrainStationRelationDO> relations = trainStationRelationMapper.selectList(
                        Wrappers.<TrainStationRelationDO>lambdaQuery()
                                .eq(TrainStationRelationDO::getStartRegion, fromRegion)
                                .eq(TrainStationRelationDO::getEndRegion, toRegion)
                                .eq(TrainStationRelationDO::getDelFlag, 0));
                if (!relations.isEmpty()) {
                    Map<Object, Object> values = relations.stream().collect(Collectors.toMap(
                            this::routeField,
                            relation -> JSON.toJSONString(toRoute(relation)),
                            (first, ignored) -> first,
                            LinkedHashMap::new));
                    redisTemplate.opsForHash().putAll(cacheKey, values);
                    routes = values.values().stream()
                            .map(Object::toString)
                            .map(value -> JSON.parseObject(value, TicketRouteCacheDTO.class))
                            .toList();
                }
            }
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
        return routes;
    }

    /**
     * 批量读取列车基础信息；单个列车缓存缺失时回源并回填。
     *
     * @param trainIds 列车主键集合
     * @return 以列车主键为键的列车信息
     */
    public Map<Long, TrainDO> findTrains(List<Long> trainIds) {
        Map<Long, TrainDO> trains = new LinkedHashMap<>();
        for (Long trainId : trainIds) {
            TrainDO train = findTrain(trainId);
            if (train != null) {
                trains.put(trainId, train);
            }
        }
        return trains;
    }

    /**
     * 获取指定列车区间的全部席别票价。
     *
     * @param route 列车区间
     * @return 票价列表
     */
    public List<TrainStationPriceDO> findPrices(TicketRouteCacheDTO route) {
        String cacheKey = TicketCacheKey.price(route.getTrainId(), route.getDeparture(), route.getArrival());
        ensurePricesCached(route, cacheKey);
        return JSON.parseArray(redisTemplate.opsForValue().get(cacheKey), TrainStationPriceDO.class);
    }

    /**
     * 使用 Redis Pipeline 批量读取多个区间的票价。
     *
     * @param routes 列车区间列表
     * @return 以列车主键为键的票价列表
     */
    public Map<Long, List<TrainStationPriceDO>> findPricesByRoutePipelined(List<TicketRouteCacheDTO> routes) {
        routes.forEach(this::findPrices);
        List<Object> values = redisTemplate.executePipelined((RedisCallback<Object>) connection -> {
            for (TicketRouteCacheDTO route : routes) {
                connection.stringCommands().get(TicketCacheKey.price(
                        route.getTrainId(), route.getDeparture(), route.getArrival())
                        .getBytes(StandardCharsets.UTF_8));
            }
            return null;
        });
        Map<Long, List<TrainStationPriceDO>> result = new LinkedHashMap<>();
        for (int index = 0; index < routes.size(); index++) {
            String value = values.get(index) == null ? "[]" : values.get(index).toString();
            result.put(routes.get(index).getTrainId(), JSON.parseArray(value, TrainStationPriceDO.class));
        }
        return result;
    }

    /**
     * 使用 Redis Pipeline 批量读取多个区间的余票 Hash。
     *
     * @param routes 列车区间列表
     * @return 以列车主键为键、席别为内层键的余票映射
     */
    public Map<Long, Map<Integer, Integer>> findRemainingTicketsByRoutePipelined(
            List<TicketRouteCacheDTO> routes) {
        routes.forEach(this::findRemainingTickets);
        List<Object> values = redisTemplate.executePipelined((RedisCallback<Object>) connection -> {
            for (TicketRouteCacheDTO route : routes) {
                connection.hashCommands().hGetAll(TicketCacheKey.remaining(
                        route.getTrainId(), route.getDeparture(), route.getArrival())
                        .getBytes(StandardCharsets.UTF_8));
            }
            return null;
        });
        Map<Long, Map<Integer, Integer>> result = new LinkedHashMap<>();
        for (int index = 0; index < routes.size(); index++) {
            result.put(routes.get(index).getTrainId(), decodeRemainingTickets(values.get(index)));
        }
        return result;
    }

    /**
     * 启动阶段预热全部有效区间、票价和余票读模型。
     *
     * <p>预热失败会向上抛出异常，避免服务在读模型不完整时启动并返回错误余票。</p>
     */
    public void warmUpRoutes() {
        List<TrainStationRelationDO> relations = trainStationRelationMapper.selectList(
                Wrappers.<TrainStationRelationDO>lambdaQuery()
                        .eq(TrainStationRelationDO::getDelFlag, 0)
                        .isNotNull(TrainStationRelationDO::getStartRegion)
                        .isNotNull(TrainStationRelationDO::getEndRegion));
        Map<String, List<TrainStationRelationDO>> relationGroups = relations.stream().collect(Collectors.groupingBy(
                relation -> TicketCacheKey.regionRoute(relation.getStartRegion(), relation.getEndRegion()),
                LinkedHashMap::new,
                Collectors.toList()));
        for (Map.Entry<String, List<TrainStationRelationDO>> entry : relationGroups.entrySet()) {
            Map<Object, Object> values = entry.getValue().stream().collect(Collectors.toMap(
                    this::routeField,
                    relation -> JSON.toJSONString(toRoute(relation)),
                    (first, ignored) -> first,
                    LinkedHashMap::new));
            redisTemplate.opsForHash().putAll(entry.getKey(), values);
        }
        List<TicketRouteCacheDTO> routes = relations.stream().map(this::toRoute).toList();
        routes.forEach(this::findPrices);
        routes.forEach(this::findRemainingTickets);
    }

    private void ensurePricesCached(TicketRouteCacheDTO route, String cacheKey) {
        String cached = redisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            return;
        }
        RLock lock = redissonClient.getLock(TicketCacheKey.lock(cacheKey));
        lock.lock();
        try {
            cached = redisTemplate.opsForValue().get(cacheKey);
            if (cached == null) {
                List<TrainStationPriceDO> prices = trainStationPriceMapper.selectList(
                        Wrappers.<TrainStationPriceDO>lambdaQuery()
                                .eq(TrainStationPriceDO::getTrainId, route.getTrainId())
                                .eq(TrainStationPriceDO::getDeparture, route.getDeparture())
                                .eq(TrainStationPriceDO::getArrival, route.getArrival())
                                .eq(TrainStationPriceDO::getDelFlag, 0));
                cached = JSON.toJSONString(prices);
                redisTemplate.opsForValue().set(cacheKey, cached);
            }
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    /**
     * 获取指定列车区间的席别余票。
     *
     * <p>本地开发数据没有订单侧的余票同步链路，因此首次查询会按现有座位数据初始化 Hash；
     * 后续查询只读取 Hash。扣减与回补不属于本查询读模型。</p>
     *
     * @param route 列车区间
     * @return 以席别为键的余票数量
     */
    public Map<Integer, Integer> findRemainingTickets(TicketRouteCacheDTO route) {
        String cacheKey = TicketCacheKey.remaining(route.getTrainId(), route.getDeparture(), route.getArrival());
        Map<Integer, Integer> tickets = readRemainingTickets(cacheKey);
        if (!tickets.isEmpty()) {
            return tickets;
        }
        RLock lock = redissonClient.getLock(TicketCacheKey.lock(cacheKey));
        lock.lock();
        try {
            tickets = readRemainingTickets(cacheKey);
            if (tickets.isEmpty()) {
                List<SeatRemainingDTO> rows = seatMapper.countAvailableSeatsByTrainIds(
                        List.of(route.getTrainId()), route.getDeparture(), route.getArrival());
                if (!rows.isEmpty()) {
                    Map<Object, Object> values = rows.stream().collect(Collectors.toMap(
                            row -> String.valueOf(row.seatType()),
                            row -> String.valueOf(row.remainingTickets()),
                            (first, ignored) -> first,
                            LinkedHashMap::new));
                    redisTemplate.opsForHash().putAll(cacheKey, values);
                    tickets = rows.stream().collect(Collectors.toMap(
                            SeatRemainingDTO::seatType, SeatRemainingDTO::remainingTickets));
                }
            }
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
        return tickets;
    }

    private TrainDO findTrain(Long trainId) {
        String cacheKey = TicketCacheKey.train(trainId);
        String cached = redisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            return JSON.parseObject(cached, TrainDO.class);
        }
        RLock lock = redissonClient.getLock(TicketCacheKey.lock(cacheKey));
        lock.lock();
        try {
            cached = redisTemplate.opsForValue().get(cacheKey);
            if (cached == null) {
                TrainDO train = trainMapper.selectById(trainId);
                if (train == null || !Objects.equals(train.getDelFlag(), 0)) {
                    return null;
                }
                cached = JSON.toJSONString(train);
                redisTemplate.opsForValue().set(cacheKey, cached);
            }
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
        return JSON.parseObject(cached, TrainDO.class);
    }

    private List<TicketRouteCacheDTO> readRoutes(String cacheKey) {
        Map<Object, Object> entries = redisTemplate.opsForHash().entries(cacheKey);
        if (entries == null || entries.isEmpty()) {
            return List.of();
        }
        return entries.values().stream()
                .map(Object::toString)
                .map(value -> JSON.parseObject(value, TicketRouteCacheDTO.class))
                .toList();
    }

    private Map<Integer, Integer> readRemainingTickets(String cacheKey) {
        Map<Object, Object> entries = redisTemplate.opsForHash().entries(cacheKey);
        if (entries == null || entries.isEmpty()) {
            return Map.of();
        }
        Map<Integer, Integer> result = new LinkedHashMap<>();
        entries.forEach((seatType, remainingTickets) -> result.put(
                Integer.valueOf(seatType.toString()), Integer.valueOf(remainingTickets.toString())));
        return result;
    }

    private Map<Integer, Integer> decodeRemainingTickets(Object value) {
        if (!(value instanceof Map<?, ?> entries) || entries.isEmpty()) {
            return Map.of();
        }
        Map<Integer, Integer> result = new LinkedHashMap<>();
        entries.forEach((seatType, remainingTickets) -> result.put(
                Integer.valueOf(decodePipelineValue(seatType)),
                Integer.valueOf(decodePipelineValue(remainingTickets))));
        return result;
    }

    private String decodePipelineValue(Object value) {
        if (value instanceof byte[] bytes) {
            return new String(bytes, StandardCharsets.UTF_8);
        }
        return value.toString();
    }

    private String routeField(TrainStationRelationDO relation) {
        return relation.getTrainId() + "_" + relation.getDeparture() + '_' + relation.getArrival();
    }

    private TicketRouteCacheDTO toRoute(TrainStationRelationDO relation) {
        TicketRouteCacheDTO route = new TicketRouteCacheDTO();
        route.setTrainId(relation.getTrainId());
        route.setDeparture(relation.getDeparture());
        route.setArrival(relation.getArrival());
        route.setDepartureFlag(relation.getDepartureFlag());
        route.setArrivalFlag(relation.getArrivalFlag());
        route.setDepartureTime(relation.getDepartureTime());
        route.setArrivalTime(relation.getArrivalTime());
        return route;
    }
}
