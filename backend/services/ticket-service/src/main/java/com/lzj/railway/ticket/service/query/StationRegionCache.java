package com.lzj.railway.ticket.service.query;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.lzj.railway.framework.convention.exception.ClientException;
import com.lzj.railway.ticket.common.errorcode.TicketErrorCode;
import com.lzj.railway.ticket.dao.entity.StationDO;
import com.lzj.railway.ticket.dao.mapper.StationMapper;
import lombok.RequiredArgsConstructor;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 管理车站编码到展示名称、地区名称的 Redis Hash 映射。
 *
 * <p>缓存缺失时仅允许一个请求回源全量加载车站数据，其他请求在锁释放后复用缓存结果。</p>
 */
@Component
@RequiredArgsConstructor
public class StationRegionCache {

    private static final String STATION_REGION_KEY = "railway:ticket:station_region";
    private static final String STATION_NAME_KEY = "railway:ticket:station_name";
    private static final String STATION_REGION_LOCK_KEY = "railway:ticket:lock:station_region";

    private final StationMapper stationMapper;
    private final StringRedisTemplate redisTemplate;
    private final RedissonClient redissonClient;

    /**
     * 获取两个站点的展示名称和所属地区。
     *
     * @param fromStation 出发站编码
     * @param toStation 到达站编码
     * @return 以车站编码为键的映射信息
     */
    public Map<String, TicketStationCacheDTO> getStations(String fromStation, String toStation) {
        Map<String, TicketStationCacheDTO> stations = readStations(fromStation, toStation);
        if (stations.size() == 2) {
            return stations;
        }
        RLock lock = redissonClient.getLock(STATION_REGION_LOCK_KEY);
        lock.lock();
        try {
            stations = readStations(fromStation, toStation);
            if (stations.size() != 2) {
                loadAllStations();
                stations = readStations(fromStation, toStation);
            }
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
        if (stations.size() != 2) {
            throw new ClientException(TicketErrorCode.STATION_NOT_FOUND);
        }
        return stations;
    }

    /**
     * 启动预热或运维场景下刷新全部站点映射。
     */
    public void warmUp() {
        RLock lock = redissonClient.getLock(STATION_REGION_LOCK_KEY);
        lock.lock();
        try {
            loadAllStations();
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    /**
     * 从站点缓存读取全部可售站点，供公开的车站选择接口使用。
     *
     * <p>名称与区域 Hash 必须同时存在；任一缓存缺失时复用预热逻辑进行一次受锁保护的回源。</p>
     */
    public List<TicketStationResponse> listAllStations() {
        HashOperations<String, Object, Object> hashOperations = redisTemplate.opsForHash();
        Map<Object, Object> names = hashOperations.entries(STATION_NAME_KEY);
        Map<Object, Object> regions = hashOperations.entries(STATION_REGION_KEY);
        if (names.isEmpty() || regions.isEmpty()) {
            warmUp();
            names = hashOperations.entries(STATION_NAME_KEY);
            regions = hashOperations.entries(STATION_REGION_KEY);
        }
        Map<Object, Object> finalRegions = regions;
        return names.entrySet().stream()
                .filter(entry -> entry.getKey() != null && entry.getValue() != null
                        && finalRegions.get(entry.getKey()) != null)
                .map(entry -> new TicketStationResponse(entry.getKey().toString(), entry.getValue().toString(),
                        finalRegions.get(entry.getKey()).toString()))
                .sorted(java.util.Comparator.comparing(TicketStationResponse::code))
                .toList();
    }

    private Map<String, TicketStationCacheDTO> readStations(String fromStation, String toStation) {
        HashOperations<String, Object, Object> hashOperations = redisTemplate.opsForHash();
        List<Object> stationCodes = List.of(fromStation, toStation);
        List<Object> regions = hashOperations.multiGet(STATION_REGION_KEY, stationCodes);
        List<Object> names = hashOperations.multiGet(STATION_NAME_KEY, stationCodes);
        if (regions == null || names == null || regions.size() != 2 || names.size() != 2
                || regions.stream().anyMatch(Objects::isNull) || names.stream().anyMatch(Objects::isNull)) {
            return Map.of();
        }
        Map<String, TicketStationCacheDTO> result = new LinkedHashMap<>();
        result.put(fromStation, new TicketStationCacheDTO(
                fromStation, names.get(0).toString(), regions.get(0).toString()));
        result.put(toStation, new TicketStationCacheDTO(
                toStation, names.get(1).toString(), regions.get(1).toString()));
        return result;
    }

    private void loadAllStations() {
        List<StationDO> stations = stationMapper.selectList(Wrappers.<StationDO>lambdaQuery()
                .eq(StationDO::getDelFlag, 0)
                .isNotNull(StationDO::getCode)
                .isNotNull(StationDO::getName)
                .isNotNull(StationDO::getRegionName));
        if (stations.isEmpty()) {
            return;
        }
        Map<Object, Object> stationNames = stations.stream().collect(Collectors.toMap(
                StationDO::getCode, StationDO::getName, (first, ignored) -> first, LinkedHashMap::new));
        Map<Object, Object> stationRegions = stations.stream().collect(Collectors.toMap(
                StationDO::getCode, StationDO::getRegionName, (first, ignored) -> first, LinkedHashMap::new));
        HashOperations<String, Object, Object> hashOperations = redisTemplate.opsForHash();
        hashOperations.putAll(STATION_NAME_KEY, stationNames);
        hashOperations.putAll(STATION_REGION_KEY, stationRegions);
    }
}
