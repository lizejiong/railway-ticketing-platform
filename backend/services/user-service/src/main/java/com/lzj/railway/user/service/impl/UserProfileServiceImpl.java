package com.lzj.railway.user.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lzj.railway.framework.convention.exception.ClientException;
import com.lzj.railway.framework.starter.user.core.UserContext;
import com.lzj.railway.user.common.errorcode.UserErrorCode;
import com.lzj.railway.user.common.util.SensitiveDataMasker;
import com.lzj.railway.user.dao.entity.UserDO;
import com.lzj.railway.user.dao.mapper.UserMapper;
import com.lzj.railway.user.dto.response.UserProfileResponse;
import com.lzj.railway.user.service.UserProfileService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/** 当前登录用户资料服务实现。 */
@Service
@RequiredArgsConstructor
public class UserProfileServiceImpl implements UserProfileService {

    private final UserMapper userMapper;

    /** 使用 Token 中的用户名直达用户分片，并返回脱敏资料。 */
    @Override
    public UserProfileResponse getCurrentProfile() {
        String username = UserContext.getUsername();
        if (!StringUtils.hasText(username)) {
            throw new ClientException(UserErrorCode.AUTHENTICATION_REQUIRED);
        }
        UserDO user = userMapper.selectOne(new LambdaQueryWrapper<UserDO>()
                .eq(UserDO::getUsername, username)
                .eq(UserDO::getDeletionTime, 0L)
                .eq(UserDO::getDelFlag, 0)
                .last("LIMIT 1"));
        if (user == null) {
            throw new ClientException(UserErrorCode.ACCOUNT_NOT_FOUND);
        }
        return new UserProfileResponse(
                user.getId(),
                user.getUsername(),
                user.getRealName(),
                user.getRegion(),
                user.getIdType(),
                SensitiveDataMasker.maskIdCard(user.getIdCard()),
                SensitiveDataMasker.maskPhone(user.getPhone()),
                SensitiveDataMasker.maskEmail(user.getMail()),
                user.getUserType(),
                user.getVerifyStatus());
    }
}
