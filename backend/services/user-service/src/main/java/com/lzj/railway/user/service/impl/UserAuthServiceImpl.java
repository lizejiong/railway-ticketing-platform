package com.lzj.railway.user.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lzj.railway.framework.convention.exception.ClientException;
import com.lzj.railway.framework.starter.user.core.UserInfoDTO;
import com.lzj.railway.framework.starter.user.token.JwtTokenGenerator;
import com.lzj.railway.user.common.errorcode.UserErrorCode;
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
import com.lzj.railway.user.service.UserAuthService;
import com.lzj.railway.user.service.registration.RegisterCacheKey;
import com.lzj.railway.user.service.registration.RegisterValidationChain;
import com.lzj.railway.user.session.RefreshTokenService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBloomFilter;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/** 用户认证与注册服务实现，事务编排与本机参考项目保持一致。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserAuthServiceImpl implements UserAuthService {

    private static final String REGISTER_LOCK_PREFIX = "railway:user:register:lock:";
    private static final String USER_PROFILE_CACHE_PREFIX = "railway:user:profile:";
    private static final Pattern PHONE_PATTERN = Pattern.compile("^1[3-9]\\d{9}$");
    private static final long ACCESS_TOKEN_EXPIRES_IN_SECONDS = 15 * 60L;

    private final UserMapper userMapper;
    private final UserPhoneMapper userPhoneMapper;
    private final UserMailMapper userMailMapper;
    private final RedissonClient redissonClient;
    private final StringRedisTemplate redisTemplate;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenGenerator jwtTokenGenerator;
    private final RefreshTokenService refreshTokenService;
    private final RegisterValidationChain registerValidationChain;
    private final TransactionTemplate transactionTemplate;

    /**
     * 在用户名分布式锁内执行责任链复核、本地事务和注册缓存维护。
     *
     * <p>责任链负责快速失败；锁内数据库查询和物理唯一索引仍是并发场景下的最终约束。</p>
     */
    @Override
    public RegisterResponse register(RegisterRequest request) {
        RegisterRequest normalizedRequest = registerValidationChain.validateAndNormalize(request);
        String username = normalizedRequest.getUsername();
        // 使用用户名作为 hash tag，便于未来 Redis Cluster 下同一用户的相关 Key 落在同一槽位。
        RLock lock = redissonClient.getLock(REGISTER_LOCK_PREFIX + "{" + username + "}");
        try {
            // 不指定固定租约，交给 Redisson watchdog 在事务执行期间自动续期。
            if (!lock.tryLock(5, TimeUnit.SECONDS)) {
                throw new ClientException(UserErrorCode.REGISTRATION_IN_PROGRESS);
            }
            RegisterResponse response = Objects.requireNonNull(transactionTemplate.execute(status ->
                    persistRegistration(normalizedRequest, username)), "注册事务未返回结果");
            maintainRegistrationCache(username);
            return response;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new ClientException(UserErrorCode.REGISTRATION_IN_PROGRESS);
        } catch (DataIntegrityViolationException ex) {
            throw duplicateRegistrationException(ex);
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    /** 在当前本地事务中执行数据库权威复核并写入用户主表、手机号索引和邮箱索引。 */
    private RegisterResponse persistRegistration(RegisterRequest request, String username) {
        ensureRegistrationAvailable(username, request.getPhone(), request.getEmail());
        UserDO user = newUser(request, username);
        userMapper.insert(user);
        userPhoneMapper.insert(newPhoneIndex(username, request.getPhone()));
        userMailMapper.insert(newMailIndex(username, request.getEmail()));
        return new RegisterResponse(user.getId(), username, request.getPhone(), request.getEmail(),
                request.getRealName(), request.getIdType(), user.getVerifyStatus());
    }

    /**
     * 校验账号密码，并签发短期 JWT 与不透明的 Refresh Token。
     */
    @Override
    public LoginResponse login(LoginRequest request) {
        // 手机号、邮箱不直接落在用户表分片键上，需先通过索引表反查 username。
        UserDO user = findUserForLogin(request.getAccount().trim());
        if (user == null) {
            throw new ClientException(UserErrorCode.ACCOUNT_NOT_FOUND);
        }
        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new ClientException(UserErrorCode.PASSWORD_INCORRECT);
        }
        return issueTokens(user);
    }

    /** 消费旧 Refresh Token，并为仍然有效的用户轮换一组新 Token。 */
    @Override
    public LoginResponse refresh(RefreshTokenRequest request) {
        String username = refreshTokenService.consume(request.refreshToken())
                .orElseThrow(() -> new ClientException(UserErrorCode.REFRESH_TOKEN_INVALID))
                .username();
        UserDO user = findUserByUsername(username);
        if (user == null) {
            throw new ClientException(UserErrorCode.ACCOUNT_NOT_FOUND);
        }
        return issueTokens(user);
    }

    /** 撤销 Refresh Token 会话；Access Token 按其短有效期自然失效。 */
    @Override
    public void logout(RefreshTokenRequest request) {
        refreshTokenService.revoke(request.refreshToken());
    }

    /** 为已认证用户签发 Access Token 和不透明 Refresh Token。 */
    private LoginResponse issueTokens(UserDO user) {
        String accessToken = jwtTokenGenerator.generateToken(UserInfoDTO.builder()
                .userId(String.valueOf(user.getId()))
                .username(user.getUsername())
                .realName(user.getRealName())
                .build());
        // Refresh Token 原始值只返回一次，Redis 内仅保留其哈希对应的会话。
        RefreshTokenService.IssuedRefreshToken refreshToken = refreshTokenService.issue(user.getId(), user.getUsername());
        return new LoginResponse(accessToken, refreshToken.token(), ACCESS_TOKEN_EXPIRES_IN_SECONDS,
                new LoginResponse.UserSummary(user.getId(), user.getUsername(), user.getRealName()));
    }

    /** 根据账号格式识别类型，并使用对应的分片键加载用户记录。 */
    private UserDO findUserForLogin(String account) {
        if (PHONE_PATTERN.matcher(account).matches()) {
            UserPhoneDO phoneIndex = findPhoneIndex(account);
            return phoneIndex == null ? null : findUserByUsername(phoneIndex.getUsername());
        }
        if (account.contains("@")) {
            UserMailDO mailIndex = findMailIndex(account);
            return mailIndex == null ? null : findUserByUsername(mailIndex.getUsername());
        }
        // 其余输入按用户名处理，用户名是 t_user 的分片键。
        return findUserByUsername(account);
    }

    /** 写入前进行唯一性预检查，以返回确定的业务错误码。 */
    private void ensureRegistrationAvailable(String username, String phone, String email) {
        if (findUserByUsername(username) != null) {
            throw new ClientException(UserErrorCode.USERNAME_ALREADY_EXISTS);
        }
        if (findPhoneIndex(phone) != null) {
            throw new ClientException(UserErrorCode.PHONE_ALREADY_BOUND);
        }
        if (findMailIndex(email) != null) {
            throw new ClientException(UserErrorCode.EMAIL_ALREADY_BOUND);
        }
    }

    /** 通过 t_user 的直接分片键查询未删除用户。 */
    private UserDO findUserByUsername(String username) {
        return userMapper.selectOne(new LambdaQueryWrapper<UserDO>()
                .eq(UserDO::getUsername, username)
                .eq(UserDO::getDeletionTime, 0L)
                .eq(UserDO::getDelFlag, 0)
                .last("LIMIT 1"));
    }

    /** 查询未删除的手机号到用户名索引。 */
    private UserPhoneDO findPhoneIndex(String phone) {
        return userPhoneMapper.selectOne(new LambdaQueryWrapper<UserPhoneDO>()
                .eq(UserPhoneDO::getPhone, phone)
                .eq(UserPhoneDO::getDeletionTime, 0L)
                .eq(UserPhoneDO::getDelFlag, 0)
                .last("LIMIT 1"));
    }

    /** 查询未删除的邮箱到用户名索引。 */
    private UserMailDO findMailIndex(String email) {
        return userMailMapper.selectOne(new LambdaQueryWrapper<UserMailDO>()
                .eq(UserMailDO::getMail, email)
                .eq(UserMailDO::getDeletionTime, 0L)
                .eq(UserMailDO::getDelFlag, 0)
                .last("LIMIT 1"));
    }

    /** 组装带有注册默认值的用户主记录。 */
    private UserDO newUser(RegisterRequest request, String username) {
        LocalDateTime now = LocalDateTime.now();
        UserDO user = new UserDO();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setRealName(request.getRealName());
        user.setIdType(request.getIdType());
        user.setIdCard(request.getIdCard());
        user.setPhone(request.getPhone());
        user.setMail(request.getEmail());
        user.setRegion("0");
        user.setVerifyStatus(1);
        user.setDeletionTime(0L);
        user.setDelFlag(0);
        user.setCreateTime(now);
        user.setUpdateTime(now);
        return user;
    }

    /** 组装按手机号路由的查询索引记录。 */
    private UserPhoneDO newPhoneIndex(String username, String phone) {
        LocalDateTime now = LocalDateTime.now();
        UserPhoneDO index = new UserPhoneDO();
        index.setUsername(username);
        index.setPhone(phone);
        index.setDeletionTime(0L);
        index.setDelFlag(0);
        index.setCreateTime(now);
        index.setUpdateTime(now);
        return index;
    }

    /** 组装按邮箱路由的查询索引记录。 */
    private UserMailDO newMailIndex(String username, String email) {
        LocalDateTime now = LocalDateTime.now();
        UserMailDO index = new UserMailDO();
        index.setUsername(username);
        index.setMail(email);
        index.setDeletionTime(0L);
        index.setDelFlag(0);
        index.setCreateTime(now);
        index.setUpdateTime(now);
        return index;
    }

    /**
     * 数据库事务提交后维护注册缓存。
     *
     * <p>缓存失败不会改变已经提交的注册结果，后续请求仍会通过数据库查询和唯一索引得到正确结果。</p>
     */
    private void maintainRegistrationCache(String username) {
        try {
            redisTemplate.delete(USER_PROFILE_CACHE_PREFIX + username);
            RBloomFilter<String> bloomFilter = redissonClient.getBloomFilter(RegisterCacheKey.USERNAME_BLOOM_FILTER);
            bloomFilter.tryInit(10_000_000L, 0.0001D);
            bloomFilter.add(username);
            redisTemplate.opsForSet().remove(RegisterCacheKey.reusableUsernameSet(username), username);
        } catch (RuntimeException exception) {
            log.warn("注册已提交，但用户名缓存维护失败，username={}", username, exception);
        }
    }

    /** 将物理唯一索引冲突转换为对外稳定的注册错误码。 */
    private ClientException duplicateRegistrationException(DataIntegrityViolationException exception) {
        String message = String.valueOf(exception.getMostSpecificCause().getMessage());
        if (message.contains("idx_phone")) {
            return new ClientException(UserErrorCode.PHONE_ALREADY_BOUND);
        }
        if (message.contains("idx_mail")) {
            return new ClientException(UserErrorCode.EMAIL_ALREADY_BOUND);
        }
        return new ClientException(UserErrorCode.USERNAME_ALREADY_EXISTS);
    }
}
