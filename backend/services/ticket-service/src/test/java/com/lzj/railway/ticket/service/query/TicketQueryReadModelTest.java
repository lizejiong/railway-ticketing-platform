package com.lzj.railway.ticket.service.query;

import com.lzj.railway.ticket.dao.entity.TrainStationRelationDO;
import com.lzj.railway.ticket.dao.mapper.TrainMapper;
import com.lzj.railway.ticket.dao.mapper.TrainStationPriceMapper;
import com.lzj.railway.ticket.dao.mapper.TrainStationRelationMapper;
import com.lzj.railway.ticket.service.purchase.TicketAvailabilityTokenBucket;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 票务查询 Redis 读模型测试。
 */
@ExtendWith(MockitoExtension.class)
class TicketQueryReadModelTest {

    @Mock
    private TrainStationRelationMapper trainStationRelationMapper;
    @Mock
    private TrainMapper trainMapper;
    @Mock
    private TrainStationPriceMapper trainStationPriceMapper;
    @Mock
    private TicketAvailabilityTokenBucket ticketAvailabilityTokenBucket;
    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private HashOperations<String, Object, Object> hashOperations;
    @Mock
    private RedissonClient redissonClient;
    @Mock
    private RLock lock;

    private TicketQueryReadModel readModel;

    /**
     * 初始化查询读模型及其 Redis 依赖。
     */
    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForHash()).thenReturn(hashOperations);
        readModel = new TicketQueryReadModel(trainStationRelationMapper, trainMapper,
                trainStationPriceMapper, ticketAvailabilityTokenBucket, redisTemplate, redissonClient);
    }

    /**
     * 区间缓存缺失时应由锁保护回源并回填 Redis Hash。
     */
    @Test
    void shouldLoadRegionRouteWhenRedisHashIsEmpty() {
        String cacheKey = "railway:ticket:region_route:{北京_南京}";
        when(hashOperations.entries(cacheKey)).thenReturn(Map.of());
        when(redissonClient.getLock("railway:ticket:lock:region_route:{北京_南京}")).thenReturn(lock);
        when(lock.isHeldByCurrentThread()).thenReturn(true);
        when(trainStationRelationMapper.selectList(any())).thenReturn(List.of(relation()));

        List<TicketRouteCacheDTO> routes = readModel.findRoutes("北京", "南京");

        assertThat(routes).singleElement().satisfies(route -> {
            assertThat(route.getTrainId()).isEqualTo(1L);
            assertThat(route.getDeparture()).isEqualTo("北京南");
        });
        verify(hashOperations).putAll(eq(cacheKey), any());
        verify(lock).unlock();
    }

    private TrainStationRelationDO relation() {
        TrainStationRelationDO relation = new TrainStationRelationDO();
        relation.setTrainId(1L);
        relation.setDeparture("北京南");
        relation.setArrival("南京南");
        relation.setDepartureFlag(true);
        relation.setArrivalFlag(false);
        relation.setDepartureTime(LocalDateTime.of(2026, 10, 2, 7, 0));
        relation.setArrivalTime(LocalDateTime.of(2026, 10, 2, 10, 0));
        return relation;
    }
}
