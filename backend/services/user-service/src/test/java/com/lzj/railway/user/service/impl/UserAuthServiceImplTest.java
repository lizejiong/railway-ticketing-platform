package com.lzj.railway.user.service.impl;

import com.lzj.railway.framework.designpattern.chain.ChainDecision;
import com.lzj.railway.framework.designpattern.chain.ChainHandler;
import com.lzj.railway.framework.starter.user.token.JwtTokenGenerator;
import com.lzj.railway.user.dao.entity.UserDO;
import com.lzj.railway.user.dao.entity.UserMailDO;
import com.lzj.railway.user.dao.entity.UserPhoneDO;
import com.lzj.railway.user.dao.mapper.UserMailMapper;
import com.lzj.railway.user.dao.mapper.UserMapper;
import com.lzj.railway.user.dao.mapper.UserPhoneMapper;
import com.lzj.railway.user.dto.request.LoginRequest;
import com.lzj.railway.user.dto.request.RefreshTokenRequest;
import com.lzj.railway.user.dto.request.RegisterRequest;
import com.lzj.railway.user.dto.response.LoginResponse;
import com.lzj.railway.user.dto.response.RegisterResponse;
import com.lzj.railway.user.service.registration.RegisterValidationChain;
import com.lzj.railway.user.session.RefreshTokenService;
import com.lzj.railway.user.session.RefreshSession;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RBloomFilter;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.concurrent.TimeUnit;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserAuthServiceImplTest {

    @Mock
    private UserMapper userMapper;
    @Mock
    private UserPhoneMapper userPhoneMapper;
    @Mock
    private UserMailMapper userMailMapper;
    @Mock
    private RedissonClient redissonClient;
    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private RLock registerLock;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private JwtTokenGenerator jwtTokenGenerator;
    @Mock
    private RefreshTokenService refreshTokenService;
    @Mock
    private ChainHandler<RegisterRequest> registerHandler;
    private RegisterValidationChain registerValidationChain;
    @Mock
    private TransactionTemplate transactionTemplate;
    @Mock
    private RBloomFilter<String> usernameBloomFilter;
    @Mock
    private SetOperations<String, String> setOperations;

    @BeforeEach
    void setUpRegistrationChain() {
        registerValidationChain = new RegisterValidationChain(List.of(registerHandler));
    }

    @Test
    void registersUserAndCreatesBothAccountIndexes() throws InterruptedException {
        when(registerHandler.handle(any(RegisterRequest.class))).thenReturn(ChainDecision.CONTINUE);
        RegisterRequest request = new RegisterRequest();
        request.setUsername("railway_user");
        request.setPassword("Password123");
        request.setPhone("13800138000");
        request.setEmail("RAILWAY@example.com");
        request.setRealName("张三");
        request.setIdType(0);
        request.setIdCard("11010119900307123x");
        when(redissonClient.getLock(anyString())).thenReturn(registerLock);
        when(registerLock.tryLock(5, TimeUnit.SECONDS)).thenReturn(true);
        when(registerLock.isHeldByCurrentThread()).thenReturn(true);
        when(redissonClient.<String>getBloomFilter(anyString())).thenReturn(usernameBloomFilter);
        when(redisTemplate.opsForSet()).thenReturn(setOperations);
        when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
            TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.doInTransaction(mock(TransactionStatus.class));
        });
        when(passwordEncoder.encode("Password123")).thenReturn("bcrypt-hash");
        doAnswer(invocation -> {
            invocation.<UserDO>getArgument(0).setId(1001L);
            return 1;
        }).when(userMapper).insert(any(UserDO.class));
        UserAuthServiceImpl service = newService();

        RegisterResponse response = service.register(request);

        assertEquals(1001L, response.userId());
        assertEquals("railway@example.com", response.email());
        assertEquals("张三", response.realName());
        assertEquals(1, response.verifyStatus());
        ArgumentCaptor<UserDO> userCaptor = ArgumentCaptor.forClass(UserDO.class);
        verify(userMapper).insert(userCaptor.capture());
        assertEquals("张三", userCaptor.getValue().getRealName());
        assertEquals(0, userCaptor.getValue().getIdType());
        assertEquals("11010119900307123X", userCaptor.getValue().getIdCard());
        assertEquals(1, userCaptor.getValue().getVerifyStatus());
        verify(userPhoneMapper, times(1)).insert(any(UserPhoneDO.class));
        verify(userMailMapper, times(1)).insert(any(UserMailDO.class));
        verify(setOperations).remove(anyString(), org.mockito.ArgumentMatchers.eq("railway_user"));
        InOrder order = inOrder(registerHandler, redissonClient, transactionTemplate,
                usernameBloomFilter, registerLock);
        order.verify(registerHandler).handle(any(RegisterRequest.class));
        order.verify(redissonClient).getLock(anyString());
        order.verify(transactionTemplate).execute(any());
        order.verify(usernameBloomFilter).add("railway_user");
        order.verify(registerLock).unlock();
    }

    @Test
    void logsInByUsernameAndReturnsBothTokens() {
        UserDO user = new UserDO();
        user.setId(1001L);
        user.setUsername("railway_user");
        user.setPassword("bcrypt-hash");
        user.setRealName("张三");
        when(userMapper.selectOne(any())).thenReturn(user);
        when(passwordEncoder.matches("Password123", "bcrypt-hash")).thenReturn(true);
        when(jwtTokenGenerator.generateToken(any())).thenReturn("access-token");
        when(refreshTokenService.issue(1001L, "railway_user"))
                .thenReturn(new RefreshTokenService.IssuedRefreshToken("refresh-token", 2_592_000L));
        UserAuthServiceImpl service = newService();
        LoginRequest request = new LoginRequest();
        request.setAccount("railway_user");
        request.setPassword("Password123");

        LoginResponse response = service.login(request);

        assertEquals("access-token", response.accessToken());
        assertEquals("refresh-token", response.refreshToken());
        assertEquals(900L, response.accessTokenExpiresIn());
        assertEquals(1001L, response.user().userId());
    }

    @Test
    void refreshesTokensByConsumingOldRefreshSession() {
        RefreshTokenRequest request = new RefreshTokenRequest("old-refresh-token");
        when(refreshTokenService.consume("old-refresh-token"))
                .thenReturn(Optional.of(new RefreshSession(1001L, "railway_user", Instant.now())));
        UserDO user = new UserDO();
        user.setId(1001L);
        user.setUsername("railway_user");
        user.setRealName("张三");
        when(userMapper.selectOne(any())).thenReturn(user);
        when(jwtTokenGenerator.generateToken(any())).thenReturn("new-access-token");
        when(refreshTokenService.issue(1001L, "railway_user"))
                .thenReturn(new RefreshTokenService.IssuedRefreshToken("new-refresh-token", 2_592_000L));
        UserAuthServiceImpl service = newService();

        LoginResponse response = service.refresh(request);

        assertEquals("new-access-token", response.accessToken());
        assertEquals("new-refresh-token", response.refreshToken());
    }

    @Test
    void rejectsInvalidRefreshToken() {
        when(refreshTokenService.consume("invalid-refresh-token")).thenReturn(Optional.empty());
        UserAuthServiceImpl service = newService();

        var exception = assertThrows(
                com.lzj.railway.framework.convention.exception.ClientException.class,
                () -> service.refresh(new RefreshTokenRequest("invalid-refresh-token")));

        assertEquals("U000012", exception.getErrorCode());
    }

    @Test
    void logoutRevokesRefreshToken() {
        UserAuthServiceImpl service = newService();

        service.logout(new RefreshTokenRequest("refresh-token"));

        verify(refreshTokenService).revoke("refresh-token");
    }

    private UserAuthServiceImpl newService() {
        return new UserAuthServiceImpl(userMapper, userPhoneMapper, userMailMapper, redissonClient,
                redisTemplate, passwordEncoder, jwtTokenGenerator, refreshTokenService,
                registerValidationChain, transactionTemplate);
    }
}
