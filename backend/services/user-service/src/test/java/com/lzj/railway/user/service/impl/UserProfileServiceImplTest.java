package com.lzj.railway.user.service.impl;

import com.lzj.railway.framework.convention.exception.ClientException;
import com.lzj.railway.framework.starter.user.core.UserContext;
import com.lzj.railway.framework.starter.user.core.UserInfoDTO;
import com.lzj.railway.user.dao.entity.UserDO;
import com.lzj.railway.user.dao.mapper.UserMapper;
import com.lzj.railway.user.dto.response.UserProfileResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserProfileServiceImplTest {

    @Mock
    private UserMapper userMapper;

    @AfterEach
    void clearUserContext() {
        UserContext.removeUser();
    }

    @Test
    void returnsCurrentUserProfileWithSensitiveFieldsMasked() {
        UserContext.setUser(UserInfoDTO.builder()
                .userId("1001")
                .username("railway_user")
                .build());
        UserDO user = new UserDO();
        user.setId(1001L);
        user.setUsername("railway_user");
        user.setRealName("张三");
        user.setRegion("0");
        user.setIdType(0);
        user.setIdCard("110101199001011234");
        user.setPhone("13800138000");
        user.setMail("railway@example.com");
        user.setUserType(0);
        user.setVerifyStatus(1);
        when(userMapper.selectOne(any())).thenReturn(user);
        UserProfileServiceImpl service = new UserProfileServiceImpl(userMapper);

        UserProfileResponse response = service.getCurrentProfile();

        assertEquals(1001L, response.userId());
        assertEquals("railway_user", response.username());
        assertEquals("1101**********1234", response.idCard());
        assertEquals("138****8000", response.phone());
        assertEquals("r***@example.com", response.email());
    }

    @Test
    void rejectsProfileQueryWithoutAuthenticatedUser() {
        UserProfileServiceImpl service = new UserProfileServiceImpl(userMapper);

        ClientException exception = assertThrows(ClientException.class, service::getCurrentProfile);

        assertEquals("U000011", exception.getErrorCode());
    }

    @Test
    void reportsMissingAccountWhenTokenUserNoLongerExists() {
        UserContext.setUser(UserInfoDTO.builder()
                .userId("1001")
                .username("railway_user")
                .build());
        when(userMapper.selectOne(any())).thenReturn(null);
        UserProfileServiceImpl service = new UserProfileServiceImpl(userMapper);

        ClientException exception = assertThrows(ClientException.class, service::getCurrentProfile);

        assertEquals("U000005", exception.getErrorCode());
    }
}
