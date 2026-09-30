package com.lzj.railway.user.service.impl;

import com.lzj.railway.framework.starter.user.token.JwtTokenGenerator;
import com.lzj.railway.user.dao.entity.UserDO;
import com.lzj.railway.user.dao.entity.UserMailDO;
import com.lzj.railway.user.dao.entity.UserPhoneDO;
import com.lzj.railway.user.dao.mapper.UserMailMapper;
import com.lzj.railway.user.dao.mapper.UserMapper;
import com.lzj.railway.user.dao.mapper.UserPhoneMapper;
import com.lzj.railway.user.dto.request.LoginRequest;
import com.lzj.railway.user.dto.request.RegisterRequest;
import com.lzj.railway.user.dto.response.LoginResponse;
import com.lzj.railway.user.dto.response.RegisterResponse;
import com.lzj.railway.user.session.RefreshTokenService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
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

    @Test
    void registersUserAndCreatesBothAccountIndexes() throws InterruptedException {
        RegisterRequest request = new RegisterRequest();
        request.setUsername("railway_user");
        request.setPassword("Password123");
        request.setPhone("13800138000");
        request.setEmail("railway@example.com");
        when(redissonClient.getLock(anyString())).thenReturn(registerLock);
        when(registerLock.tryLock(5, 30, TimeUnit.SECONDS)).thenReturn(true);
        when(registerLock.isHeldByCurrentThread()).thenReturn(true);
        when(passwordEncoder.encode("Password123")).thenReturn("bcrypt-hash");
        doAnswer(invocation -> {
            invocation.<UserDO>getArgument(0).setId(1001L);
            return 1;
        }).when(userMapper).insert(any(UserDO.class));
        UserAuthServiceImpl service = newService();

        TransactionSynchronizationManager.initSynchronization();
        try {
            RegisterResponse response = service.register(request);

            assertEquals(1001L, response.userId());
            verify(userMapper).insert(any(UserDO.class));
            verify(userPhoneMapper, times(1)).insert(any(UserPhoneDO.class));
            verify(userMailMapper, times(1)).insert(any(UserMailDO.class));
            verify(registerLock).unlock();
            assertEquals(1, TransactionSynchronizationManager.getSynchronizations().size());
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
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

    private UserAuthServiceImpl newService() {
        return new UserAuthServiceImpl(userMapper, userPhoneMapper, userMailMapper, redissonClient,
                redisTemplate, passwordEncoder, jwtTokenGenerator, refreshTokenService);
    }
}
