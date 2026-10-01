package com.lzj.railway.user.service.registration;

import com.lzj.railway.framework.convention.exception.ClientException;
import com.lzj.railway.framework.designpattern.chain.ChainHandler;
import com.lzj.railway.framework.designpattern.chain.ResponsibilityChain;
import com.lzj.railway.user.dao.entity.UserDO;
import com.lzj.railway.user.dao.mapper.UserDeletionMapper;
import com.lzj.railway.user.dao.mapper.UserMapper;
import com.lzj.railway.user.dto.request.RegisterRequest;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;
import org.redisson.api.RBloomFilter;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RegisterResponsibilityChainTest {

    @Test
    void rejectsInvalidParametersBeforeOtherChecks() {
        RegisterRequest request = validRequest();
        request.setRealName("");
        RegisterParameterValidationHandler parameterHandler = new RegisterParameterValidationHandler(
                Validation.buildDefaultValidatorFactory().getValidator());
        ChainHandler<RegisterRequest> unexpectedHandler = new ChainHandler<>() {
            @Override
            public com.lzj.railway.framework.designpattern.chain.ChainDecision handle(RegisterRequest request) {
                throw new AssertionError("参数校验失败后不应继续执行责任链");
            }

            @Override
            public int order() {
                return 1_000;
            }
        };
        ResponsibilityChain<RegisterRequest> chain = ResponsibilityChain.<RegisterRequest>builder()
                .add(unexpectedHandler)
                .add(parameterHandler)
                .build();

        ClientException exception = assertThrows(ClientException.class,
                () -> chain.execute(request));

        assertEquals("U000014", exception.getErrorCode());
    }

    @Test
    @SuppressWarnings("unchecked")
    void skipsRedisSetAndDatabaseWhenBloomFilterSaysUsernameWasNeverSeen() {
        RedissonClient redissonClient = mock(RedissonClient.class);
        RBloomFilter<String> bloomFilter = mock(RBloomFilter.class);
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        UserMapper userMapper = mock(UserMapper.class);
        when(redissonClient.<String>getBloomFilter(anyString())).thenReturn(bloomFilter);
        when(bloomFilter.contains("new_user")).thenReturn(false);
        RegisterUsernameAvailabilityHandler handler = new RegisterUsernameAvailabilityHandler(
                redissonClient, redisTemplate, userMapper);

        handler.handle(validRequest());

        org.mockito.Mockito.verifyNoInteractions(redisTemplate, userMapper);
    }

    @Test
    @SuppressWarnings("unchecked")
    void rejectsActiveUsernameWhenBloomFilterAndDatabaseBothFindIt() {
        RedissonClient redissonClient = mock(RedissonClient.class);
        RBloomFilter<String> bloomFilter = mock(RBloomFilter.class);
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        SetOperations<String, String> setOperations = mock(SetOperations.class);
        UserMapper userMapper = mock(UserMapper.class);
        when(redissonClient.<String>getBloomFilter(anyString())).thenReturn(bloomFilter);
        when(bloomFilter.contains("new_user")).thenReturn(true);
        when(redisTemplate.opsForSet()).thenReturn(setOperations);
        when(setOperations.isMember(anyString(), eq("new_user"))).thenReturn(false);
        when(userMapper.selectOne(any())).thenReturn(new UserDO());
        RegisterUsernameAvailabilityHandler handler = new RegisterUsernameAvailabilityHandler(
                redissonClient, redisTemplate, userMapper);

        ClientException exception = assertThrows(ClientException.class,
                () -> handler.handle(validRequest()));

        assertEquals("U000001", exception.getErrorCode());
    }

    @Test
    @SuppressWarnings("unchecked")
    void allowsUsernameThatWasReleasedAfterAccountDeletion() {
        RedissonClient redissonClient = mock(RedissonClient.class);
        RBloomFilter<String> bloomFilter = mock(RBloomFilter.class);
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        SetOperations<String, String> setOperations = mock(SetOperations.class);
        UserMapper userMapper = mock(UserMapper.class);
        when(redissonClient.<String>getBloomFilter(anyString())).thenReturn(bloomFilter);
        when(bloomFilter.contains("new_user")).thenReturn(true);
        when(redisTemplate.opsForSet()).thenReturn(setOperations);
        when(setOperations.isMember(anyString(), eq("new_user"))).thenReturn(true);
        RegisterUsernameAvailabilityHandler handler = new RegisterUsernameAvailabilityHandler(
                redissonClient, redisTemplate, userMapper);

        handler.handle(validRequest());

        org.mockito.Mockito.verifyNoInteractions(userMapper);
    }

    @Test
    void rejectsIdentityWithFiveDeletionRecords() {
        UserDeletionMapper deletionMapper = mock(UserDeletionMapper.class);
        when(deletionMapper.selectCount(any())).thenReturn(5L);
        RegisterDeletionLimitHandler handler = new RegisterDeletionLimitHandler(deletionMapper);

        ClientException exception = assertThrows(ClientException.class,
                () -> handler.handle(validRequest()));

        assertEquals("U000015", exception.getErrorCode());
    }

    @Test
    void validationChainNormalizesRequestBeforeExecutingHandlersInOrder() {
        @SuppressWarnings("unchecked")
        ChainHandler<RegisterRequest> firstHandler = mock(ChainHandler.class);
        @SuppressWarnings("unchecked")
        ChainHandler<RegisterRequest> secondHandler = mock(ChainHandler.class);
        when(firstHandler.order()).thenReturn(0);
        when(secondHandler.order()).thenReturn(100);
        when(firstHandler.handle(any(RegisterRequest.class)))
                .thenReturn(com.lzj.railway.framework.designpattern.chain.ChainDecision.CONTINUE);
        when(secondHandler.handle(any(RegisterRequest.class)))
                .thenReturn(com.lzj.railway.framework.designpattern.chain.ChainDecision.CONTINUE);
        RegisterValidationChain chain = new RegisterValidationChain(List.of(secondHandler, firstHandler));
        RegisterRequest request = validRequest();
        request.setUsername("  new_user  ");
        request.setEmail("  NEW_USER@EXAMPLE.COM  ");
        request.setIdCard("  11010119900307123x  ");

        RegisterRequest normalized = chain.validateAndNormalize(request);

        assertEquals("new_user", normalized.getUsername());
        assertEquals("new_user@example.com", normalized.getEmail());
        assertEquals("11010119900307123X", normalized.getIdCard());
        org.mockito.InOrder order = org.mockito.Mockito.inOrder(firstHandler, secondHandler);
        order.verify(firstHandler).handle(normalized);
        order.verify(secondHandler).handle(normalized);
    }

    private static RegisterRequest validRequest() {
        RegisterRequest request = new RegisterRequest();
        request.setUsername("new_user");
        request.setPassword("Password123");
        request.setPhone("13800138000");
        request.setEmail("NEW_USER@example.com");
        request.setRealName("张三");
        request.setIdType(0);
        request.setIdCard("110101199003071234");
        return request;
    }
}
