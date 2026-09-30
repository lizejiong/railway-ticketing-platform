package com.lzj.railway.user.session;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private HashOperations<String, Object, Object> hashOperations;

    @Test
    void consumesStoredSessionOnlyWhenRedisKeyIsDeleted() {
        when(redisTemplate.opsForHash()).thenReturn(hashOperations);
        when(hashOperations.entries(anyString())).thenReturn(Map.of(
                "userId", "1001",
                "username", "railway_user",
                "createdAt", "2026-09-30T10:00:00Z"));
        when(redisTemplate.delete(anyString())).thenReturn(true);
        RefreshTokenService service = new RefreshTokenService(redisTemplate);

        Optional<RefreshSession> session = service.consume("refresh-token");

        assertTrue(session.isPresent());
        assertEquals(1001L, session.orElseThrow().userId());
        assertEquals("railway_user", session.orElseThrow().username());
        assertEquals(Instant.parse("2026-09-30T10:00:00Z"), session.orElseThrow().createdAt());
    }

    @Test
    void rejectsAlreadyConsumedSessionWhenDeleteLosesRace() {
        when(redisTemplate.opsForHash()).thenReturn(hashOperations);
        when(hashOperations.entries(anyString())).thenReturn(Map.of(
                "userId", "1001",
                "username", "railway_user",
                "createdAt", "2026-09-30T10:00:00Z"));
        when(redisTemplate.delete(anyString())).thenReturn(false);
        RefreshTokenService service = new RefreshTokenService(redisTemplate);

        Optional<RefreshSession> session = service.consume("refresh-token");

        assertTrue(session.isEmpty());
    }

    @Test
    void revokesRefreshTokenIdempotently() {
        RefreshTokenService service = new RefreshTokenService(redisTemplate);

        service.revoke("refresh-token");

        verify(redisTemplate).delete(anyString());
    }
}
