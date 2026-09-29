package com.lzj.railway.framework.starter.idempotent.executor;

import com.lzj.railway.framework.convention.exception.ClientException;
import com.lzj.railway.framework.starter.cache.DistributedCache;
import com.lzj.railway.framework.starter.idempotent.annotation.Idempotent;
import com.lzj.railway.framework.starter.idempotent.enums.IdempotentScene;
import org.aspectj.lang.ProceedingJoinPoint;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SuppressWarnings("unchecked")
class RedisIdempotentExecutorTest {

    @Test
    void shouldMarkRequestCompletedAfterSuccessfulExecution() throws Throwable {
        Fixture fixture = fixture(true);
        when(fixture.joinPoint.proceed()).thenReturn("ok");

        Object result = fixture.restExecutor.execute(fixture.joinPoint, annotation("rest"), "key");

        assertThat(result).isEqualTo("ok");
        verify(fixture.operations).setIfAbsent(
                "key",
                "PROCESSING:request-1",
                60_000L,
                TimeUnit.MILLISECONDS
        );
        verify(fixture.redisTemplate).execute(
                AbstractRedisIdempotentExecutor.COMPLETE_IF_OWNER_SCRIPT,
                Collections.singletonList("key"),
                "PROCESSING:request-1",
                "COMPLETED",
                "60000"
        );
    }

    @Test
    void shouldReleaseOwnedMarkerWhenBusinessExecutionFails() throws Throwable {
        Fixture fixture = fixture(true);
        IllegalStateException failure = new IllegalStateException("business failed");
        when(fixture.joinPoint.proceed()).thenThrow(failure);

        assertThatThrownBy(() -> fixture.restExecutor.execute(
                fixture.joinPoint,
                annotation("rest"),
                "key"
        )).isSameAs(failure);
        verify(fixture.redisTemplate).execute(
                AbstractRedisIdempotentExecutor.DELETE_IF_OWNER_SCRIPT,
                Collections.singletonList("key"),
                "PROCESSING:request-1"
        );
    }

    @Test
    void shouldRejectDuplicateRestRequestAndSkipDuplicateMqMessage() throws Throwable {
        Fixture fixture = fixture(false);

        assertThatThrownBy(() -> fixture.restExecutor.execute(
                fixture.joinPoint,
                annotation("rest"),
                "key"
        )).isInstanceOf(ClientException.class)
                .hasMessage("重复请求");
        assertThat(fixture.mqExecutor.execute(fixture.joinPoint, annotation("mq"), "key")).isNull();
        verify(fixture.joinPoint, never()).proceed();
    }

    @Test
    void shouldReturnBusinessResultWhenCompletedMarkerCannotBeUpdated() throws Throwable {
        Fixture fixture = fixture(true);
        when(fixture.joinPoint.proceed()).thenReturn("ok");
        when(fixture.redisTemplate.execute(
                AbstractRedisIdempotentExecutor.COMPLETE_IF_OWNER_SCRIPT,
                Collections.singletonList("key"),
                "PROCESSING:request-1",
                "COMPLETED",
                "60000"
        )).thenThrow(new IllegalStateException("redis unavailable"));

        Object result = fixture.restExecutor.execute(fixture.joinPoint, annotation("rest"), "key");

        assertThat(result).isEqualTo("ok");
    }

    private Fixture fixture(boolean acquired) {
        DistributedCache cache = mock(DistributedCache.class);
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        ValueOperations<String, String> operations = mock(ValueOperations.class);
        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
        when(cache.getRedisTemplate()).thenReturn(redisTemplate);
        when(redisTemplate.opsForValue()).thenReturn(operations);
        when(operations.setIfAbsent(
                "key",
                "PROCESSING:request-1",
                60_000L,
                TimeUnit.MILLISECONDS
        )).thenReturn(acquired);
        when(redisTemplate.execute(
                AbstractRedisIdempotentExecutor.COMPLETE_IF_OWNER_SCRIPT,
                Collections.singletonList("key"),
                "PROCESSING:request-1",
                "COMPLETED",
                "60000"
        )).thenReturn(1L);
        return new Fixture(
                new RestApiIdempotentExecutor(cache, () -> "request-1"),
                new MqIdempotentExecutor(cache, () -> "request-1"),
                joinPoint,
                redisTemplate,
                operations
        );
    }

    private Idempotent annotation(String methodName) throws Exception {
        Method method = Samples.class.getDeclaredMethod(methodName);
        return method.getAnnotation(Idempotent.class);
    }

    private record Fixture(
            RestApiIdempotentExecutor restExecutor,
            MqIdempotentExecutor mqExecutor,
            ProceedingJoinPoint joinPoint,
            StringRedisTemplate redisTemplate,
            ValueOperations<String, String> operations) {
    }

    private static class Samples {

        @Idempotent(keyTimeout = 60, message = "重复请求")
        void rest() {
        }

        @Idempotent(scene = IdempotentScene.MQ, keyTimeout = 60)
        void mq() {
        }
    }
}
