package com.lzj.railway.ticket.service.purchase;

import com.lzj.railway.ticket.dao.mapper.SeatMapper;
import com.lzj.railway.ticket.dao.mapper.dto.SeatRemainingDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 余票令牌桶测试。
 */
@ExtendWith(MockitoExtension.class)
class TicketAvailabilityTokenBucketTest {

    private static final String BUCKET_KEY = "railway:ticket:remaining:{3}";

    @Mock
    private SeatMapper seatMapper;
    @Mock
    private TrainRouteService trainRouteService;
    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private HashOperations<String, Object, Object> hashOperations;
    @Mock
    private RedissonClient redissonClient;
    @Mock
    private RLock lock;

    private TicketAvailabilityTokenBucket tokenBucket;

    /**
     * 初始化待测令牌桶。
     */
    @BeforeEach
    void setUp() {
        tokenBucket = new TicketAvailabilityTokenBucket(
                seatMapper, trainRouteService, redisTemplate, redissonClient);
    }

    /**
     * 首次读取应将列车的所有可售区间和席别写入同一个 Hash。
     */
    @Test
    void shouldInitializeAllSaleSegmentsIntoOneBucket() {
        when(redisTemplate.opsForHash()).thenReturn(hashOperations);
        when(redisTemplate.hasKey(BUCKET_KEY)).thenReturn(false);
        when(redissonClient.getLock("railway:ticket:lock:remaining:{3}")).thenReturn(lock);
        when(lock.isHeldByCurrentThread()).thenReturn(true);
        when(trainRouteService.listAllSaleSegments(3L)).thenReturn(List.of(
                new TrainRouteSegment("A", "B"),
                new TrainRouteSegment("A", "C"),
                new TrainRouteSegment("B", "C")
        ));
        when(seatMapper.countAvailableSeatsByTrainIds(List.of(3L), "A", "B"))
                .thenReturn(List.of(new SeatRemainingDTO(3L, 1, 20)));
        when(seatMapper.countAvailableSeatsByTrainIds(List.of(3L), "A", "C"))
                .thenReturn(List.of(new SeatRemainingDTO(3L, 1, 18)));
        when(seatMapper.countAvailableSeatsByTrainIds(List.of(3L), "B", "C"))
                .thenReturn(List.of(new SeatRemainingDTO(3L, 1, 19)));

        tokenBucket.ensureInitialized(3L);

        ArgumentCaptor<Map<Object, Object>> tokens = ArgumentCaptor.forClass(Map.class);
        verify(hashOperations).putAll(eq(BUCKET_KEY), tokens.capture());
        assertThat(tokens.getValue()).containsEntry("A_B_1", "20")
                .containsEntry("A_C_1", "18")
                .containsEntry("B_C_1", "19");
        verify(lock).unlock();
    }

    /**
     * 购票预扣应把请求区间、全部途经区间和席别数量作为同一 Lua 调用的参数。
     */
    @Test
    void shouldUseLuaToAtomicallyTakeTokensForEveryRouteSegment() {
        when(redisTemplate.hasKey(BUCKET_KEY)).thenReturn(true);
        when(redisTemplate.execute(any(RedisScript.class), eq(List.of(BUCKET_KEY)), any(), any(), eq("A_C")))
                .thenReturn(1L);

        boolean success = tokenBucket.takeTokenFromBucket(3L, "A", "C",
                List.of(new TrainRouteSegment("A", "B"), new TrainRouteSegment("A", "C"),
                        new TrainRouteSegment("B", "C")),
                Map.of(1, 2L));

        assertThat(success).isTrue();
    }

    /**
     * 查询余票应从令牌桶中筛选出目标区间的所有席别。
     */
    @Test
    void shouldReadRemainingTicketsFromTokenBucket() {
        when(redisTemplate.opsForHash()).thenReturn(hashOperations);
        when(redisTemplate.hasKey(BUCKET_KEY)).thenReturn(true);
        when(hashOperations.entries(BUCKET_KEY)).thenReturn(Map.of(
                "A_B_1", "20",
                "A_C_1", "18",
                "A_C_2", "6",
                "B_C_1", "19"
        ));

        Map<Integer, Integer> remainingTickets = tokenBucket.getRemainingTickets(3L, "A", "C");

        assertThat(remainingTickets).containsExactlyInAnyOrderEntriesOf(Map.of(1, 18, 2, 6));
    }
}
