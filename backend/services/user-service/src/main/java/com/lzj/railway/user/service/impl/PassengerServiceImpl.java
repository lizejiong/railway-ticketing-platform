package com.lzj.railway.user.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lzj.railway.framework.convention.exception.ClientException;
import com.lzj.railway.framework.starter.user.core.UserContext;
import com.lzj.railway.user.common.errorcode.UserErrorCode;
import com.lzj.railway.user.common.util.SensitiveDataMasker;
import com.lzj.railway.user.dao.entity.PassengerDO;
import com.lzj.railway.user.dao.mapper.PassengerMapper;
import com.lzj.railway.user.dto.request.PassengerCreateRequest;
import com.lzj.railway.user.dto.request.PassengerUpdateRequest;
import com.lzj.railway.user.dto.response.PassengerResponse;
import com.lzj.railway.user.dto.response.PassengerActualResponse;
import com.lzj.railway.user.service.PassengerService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

/** 当前登录用户的乘车人管理服务实现。 */
@Service
@RequiredArgsConstructor
public class PassengerServiceImpl implements PassengerService {

    private final PassengerMapper passengerMapper;

    /** 使用当前用户名作为分片键查询乘车人，并对敏感字段脱敏。 */
    @Override
    public List<PassengerResponse> listCurrentUserPassengers() {
        String username = currentUsername();
        return passengerMapper.selectList(new LambdaQueryWrapper<PassengerDO>()
                        .eq(PassengerDO::getUsername, username)
                        .eq(PassengerDO::getDelFlag, 0)
                        .orderByAsc(PassengerDO::getCreateTime)
                        .orderByAsc(PassengerDO::getId))
                .stream()
                .map(this::toResponse)
                .toList();
    }

    /**
     * 使用用户名分片键批量取得真实乘车人信息。
     *
     * <p>该方法不读取 {@link UserContext}：调用方显式传入用户名，并由票务服务将其与登录上下文保持一致。
     * 返回值保留敏感字段，只能由内部服务间接口调用。</p>
     */
    @Override
    public List<PassengerActualResponse> listPassengerActualByIds(String username, List<Long> passengerIds) {
        if (!StringUtils.hasText(username) || passengerIds == null || passengerIds.isEmpty()) {
            return List.of();
        }
        return passengerMapper.selectList(new LambdaQueryWrapper<PassengerDO>()
                        .eq(PassengerDO::getUsername, username)
                        .in(PassengerDO::getId, passengerIds)
                        .eq(PassengerDO::getDelFlag, 0))
                .stream()
                .map(passenger -> new PassengerActualResponse(passenger.getId(), passenger.getRealName(),
                        passenger.getIdType(), passenger.getIdCard(), passenger.getDiscountType(),
                        passenger.getPhone(), passenger.getVerifyStatus()))
                .toList();
    }

    /** 校验当前用户下证件号不重复后写入乘车人分片表。 */
    @Override
    public PassengerResponse create(PassengerCreateRequest request) {
        String username = currentUsername();
        ensureIdCardUnique(username, request.idCard(), null);
        LocalDateTime now = LocalDateTime.now();
        PassengerDO passenger = new PassengerDO();
        passenger.setUsername(username);
        passenger.setRealName(request.realName());
        passenger.setIdType(request.idType());
        passenger.setIdCard(request.idCard());
        passenger.setDiscountType(request.discountType());
        passenger.setPhone(request.phone());
        passenger.setCreateDate(now);
        passenger.setVerifyStatus(0);
        passenger.setCreateTime(now);
        passenger.setUpdateTime(now);
        passenger.setDelFlag(0);
        passengerMapper.insert(passenger);
        return toResponse(passenger);
    }

    /** 携带用户名和乘车人 ID 定位记录，防止修改其他用户的数据。 */
    @Override
    public PassengerResponse update(Long passengerId, PassengerUpdateRequest request) {
        String username = currentUsername();
        PassengerDO passenger = findOwnedPassenger(username, passengerId);
        if (passenger == null) {
            throw new ClientException(UserErrorCode.PASSENGER_NOT_FOUND);
        }
        ensureIdCardUnique(username, request.idCard(), passengerId);
        LocalDateTime now = LocalDateTime.now();
        PassengerDO changes = new PassengerDO();
        changes.setRealName(request.realName());
        changes.setIdType(request.idType());
        changes.setIdCard(request.idCard());
        changes.setDiscountType(request.discountType());
        changes.setPhone(request.phone());
        changes.setUpdateTime(now);
        int affectedRows = passengerMapper.update(changes, new LambdaQueryWrapper<PassengerDO>()
                .eq(PassengerDO::getUsername, username)
                .eq(PassengerDO::getId, passengerId)
                .eq(PassengerDO::getDelFlag, 0));
        if (affectedRows == 0) {
            throw new ClientException(UserErrorCode.PASSENGER_NOT_FOUND);
        }
        passenger.setRealName(request.realName());
        passenger.setIdType(request.idType());
        passenger.setIdCard(request.idCard());
        passenger.setDiscountType(request.discountType());
        passenger.setPhone(request.phone());
        passenger.setUpdateTime(now);
        return toResponse(passenger);
    }

    /** 按当前用户分片执行软删除，不暴露记录是否属于其他用户。 */
    @Override
    public void delete(Long passengerId) {
        String username = currentUsername();
        PassengerDO changes = new PassengerDO();
        changes.setDelFlag(1);
        changes.setUpdateTime(LocalDateTime.now());
        int affectedRows = passengerMapper.update(changes, new LambdaQueryWrapper<PassengerDO>()
                .eq(PassengerDO::getUsername, username)
                .eq(PassengerDO::getId, passengerId)
                .eq(PassengerDO::getDelFlag, 0));
        if (affectedRows == 0) {
            throw new ClientException(UserErrorCode.PASSENGER_NOT_FOUND);
        }
    }

    /** 校验同一用户下没有重复的有效证件号。 */
    private void ensureIdCardUnique(String username, String idCard, Long excludedPassengerId) {
        LambdaQueryWrapper<PassengerDO> query = new LambdaQueryWrapper<PassengerDO>()
                .eq(PassengerDO::getUsername, username)
                .eq(PassengerDO::getIdCard, idCard)
                .eq(PassengerDO::getDelFlag, 0);
        if (excludedPassengerId != null) {
            query.ne(PassengerDO::getId, excludedPassengerId);
        }
        if (passengerMapper.selectCount(query) > 0) {
            throw new ClientException(UserErrorCode.PASSENGER_ALREADY_EXISTS);
        }
    }

    /** 按用户名分片键和乘车人 ID 查询当前用户拥有的有效记录。 */
    private PassengerDO findOwnedPassenger(String username, Long passengerId) {
        return passengerMapper.selectOne(new LambdaQueryWrapper<PassengerDO>()
                .eq(PassengerDO::getUsername, username)
                .eq(PassengerDO::getId, passengerId)
                .eq(PassengerDO::getDelFlag, 0)
                .last("LIMIT 1"));
    }

    /** 获取认证上下文中的用户名，缺失时统一返回未登录错误。 */
    private String currentUsername() {
        String username = UserContext.getUsername();
        if (!StringUtils.hasText(username)) {
            throw new ClientException(UserErrorCode.AUTHENTICATION_REQUIRED);
        }
        return username;
    }

    /** 将数据库对象转换为对外脱敏响应。 */
    private PassengerResponse toResponse(PassengerDO passenger) {
        return new PassengerResponse(
                passenger.getId(),
                passenger.getRealName(),
                passenger.getIdType(),
                SensitiveDataMasker.maskIdCard(passenger.getIdCard()),
                passenger.getDiscountType(),
                SensitiveDataMasker.maskPhone(passenger.getPhone()),
                passenger.getCreateDate(),
                passenger.getVerifyStatus());
    }
}
