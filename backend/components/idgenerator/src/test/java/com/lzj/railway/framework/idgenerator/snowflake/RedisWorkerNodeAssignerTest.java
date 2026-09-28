package com.lzj.railway.framework.idgenerator.snowflake;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RedisWorkerNodeAssignerTest {

    @Test
    void shouldReserveSingleNodeIdAndSplitItForSnowflake() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        when(redisTemplate.execute(
                org.mockito.ArgumentMatchers.<RedisScript<Long>>any(),
                org.mockito.ArgumentMatchers.<List<String>>any(),
                org.mockito.ArgumentMatchers.<Object[]>any()
        )).thenReturn(66L);

        try (RedisWorkerNodeAssigner assigner = new RedisWorkerNodeAssigner(redisTemplate, "ticket-service-0")) {
            assertThat(assigner.assign()).isEqualTo(new WorkerNode(66));
            assigner.verifyLease();

            verify(redisTemplate).execute(
                    org.mockito.ArgumentMatchers.<DefaultRedisScript<Long>>any(),
                    org.mockito.ArgumentMatchers.<List<String>>argThat(keys ->
                            keys.size() == 1_024
                                    && keys.stream().allMatch(key -> key.contains("{railway:id-generator}"))
                    ),
                    org.mockito.ArgumentMatchers.<Object[]>any()
            );
        }
    }

    @Test
    void shouldRejectAssignmentWhenAllNodeIdsAreLeased() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        when(redisTemplate.execute(
                org.mockito.ArgumentMatchers.<RedisScript<Long>>any(),
                org.mockito.ArgumentMatchers.<List<String>>any(),
                org.mockito.ArgumentMatchers.<Object[]>any()
        )).thenReturn(-1L);

        try (RedisWorkerNodeAssigner assigner = new RedisWorkerNodeAssigner(redisTemplate, "ticket-service-0")) {
            assertThatThrownBy(assigner::assign)
                    .isInstanceOf(WorkerNodeUnavailableException.class)
                    .hasMessage("no worker node id is available");
        }
    }

    @Test
    void shouldStopIdGenerationAfterLeaseRenewalFails() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        when(redisTemplate.execute(
                org.mockito.ArgumentMatchers.<RedisScript<Long>>any(),
                org.mockito.ArgumentMatchers.<List<String>>any(),
                org.mockito.ArgumentMatchers.<Object[]>any()
        )).thenReturn(66L, 0L);

        try (RedisWorkerNodeAssigner assigner = new RedisWorkerNodeAssigner(redisTemplate, "ticket-service-0")) {
            assertThat(assigner.assign()).isEqualTo(new WorkerNode(66));

            assigner.renewLease();

            assertThatThrownBy(assigner::verifyLease)
                    .isInstanceOf(WorkerNodeLeaseLostException.class);
            assertThatThrownBy(() -> new SnowflakeIdGenerator(assigner).nextId())
                    .isInstanceOf(WorkerNodeLeaseLostException.class);
        }
    }

    @Test
    void shouldValidateRedisWorkerNodeConfiguration() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);

        assertThatThrownBy(() -> new RedisWorkerNodeAssigner(redisTemplate, " "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("instance id must not be blank");
        assertThatThrownBy(() -> new RedisWorkerNodeAssigner(
                redisTemplate,
                "ticket-service-0",
                "railway:id-generator",
                Duration.ofSeconds(2)
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("lease duration must be at least 3 seconds");
    }
}
