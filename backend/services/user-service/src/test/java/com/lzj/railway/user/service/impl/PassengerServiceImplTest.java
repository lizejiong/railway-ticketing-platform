package com.lzj.railway.user.service.impl;

import com.lzj.railway.framework.convention.exception.ClientException;
import com.lzj.railway.framework.starter.user.core.UserContext;
import com.lzj.railway.framework.starter.user.core.UserInfoDTO;
import com.lzj.railway.user.dao.entity.PassengerDO;
import com.lzj.railway.user.dao.mapper.PassengerMapper;
import com.lzj.railway.user.dto.request.PassengerCreateRequest;
import com.lzj.railway.user.dto.request.PassengerUpdateRequest;
import com.lzj.railway.user.dto.response.PassengerResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PassengerServiceImplTest {

    @Mock
    private PassengerMapper passengerMapper;

    private PassengerServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new PassengerServiceImpl(passengerMapper);
        UserContext.setUser(UserInfoDTO.builder()
                .userId("1001")
                .username("railway_user")
                .build());
    }

    @AfterEach
    void clearUserContext() {
        UserContext.removeUser();
    }

    @Test
    void listsCurrentUserPassengersWithSensitiveFieldsMasked() {
        PassengerDO passenger = passenger(2001L);
        when(passengerMapper.selectList(any())).thenReturn(List.of(passenger));

        List<PassengerResponse> responses = service.listCurrentUserPassengers();

        assertEquals(1, responses.size());
        assertEquals("1101**********1234", responses.get(0).idCard());
        assertEquals("138****8000", responses.get(0).phone());
    }

    @Test
    void createsPassengerForCurrentUser() {
        when(passengerMapper.selectCount(any())).thenReturn(0L);
        doAnswer(invocation -> {
            invocation.<PassengerDO>getArgument(0).setId(2001L);
            return 1;
        }).when(passengerMapper).insert(any(PassengerDO.class));

        PassengerResponse response = service.create(createRequest());

        assertEquals(2001L, response.id());
        assertEquals("张三", response.realName());
        verify(passengerMapper).insert(any(PassengerDO.class));
    }

    @Test
    void rejectsDuplicatePassengerForCurrentUser() {
        when(passengerMapper.selectCount(any())).thenReturn(1L);

        ClientException exception = assertThrows(ClientException.class,
                () -> service.create(createRequest()));

        assertEquals("U000013", exception.getErrorCode());
    }

    @Test
    void updatesPassengerOnlyWithinCurrentUserShard() {
        when(passengerMapper.selectOne(any())).thenReturn(passenger(2001L));
        when(passengerMapper.selectCount(any())).thenReturn(0L);
        when(passengerMapper.update(any(), any())).thenReturn(1);
        PassengerUpdateRequest request = new PassengerUpdateRequest(
                "李四", 0, "110101199002022345", 0, "13900139000");

        PassengerResponse response = service.update(2001L, request);

        assertEquals(2001L, response.id());
        assertEquals("李四", response.realName());
        verify(passengerMapper).update(any(), any());
    }

    @Test
    void reportsMissingPassengerWhenDeleteDoesNotMatchCurrentUser() {
        when(passengerMapper.update(any(), any())).thenReturn(0);

        ClientException exception = assertThrows(ClientException.class,
                () -> service.delete(2001L));

        assertEquals("U000009", exception.getErrorCode());
    }

    private PassengerCreateRequest createRequest() {
        return new PassengerCreateRequest("张三", 0, "110101199001011234", 0, "13800138000");
    }

    private PassengerDO passenger(Long id) {
        PassengerDO passenger = new PassengerDO();
        passenger.setId(id);
        passenger.setUsername("railway_user");
        passenger.setRealName("张三");
        passenger.setIdType(0);
        passenger.setIdCard("110101199001011234");
        passenger.setDiscountType(0);
        passenger.setPhone("13800138000");
        passenger.setCreateDate(LocalDateTime.of(2026, 9, 30, 10, 0));
        passenger.setVerifyStatus(0);
        passenger.setDelFlag(0);
        return passenger;
    }
}
