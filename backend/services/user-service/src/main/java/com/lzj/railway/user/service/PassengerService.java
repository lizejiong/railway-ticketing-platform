package com.lzj.railway.user.service;

import com.lzj.railway.user.dto.request.PassengerCreateRequest;
import com.lzj.railway.user.dto.request.PassengerUpdateRequest;
import com.lzj.railway.user.dto.response.PassengerResponse;
import com.lzj.railway.user.dto.response.PassengerActualResponse;

import java.util.List;

/** 当前登录用户的乘车人管理服务。 */
public interface PassengerService {

    /** 查询当前用户的全部有效乘车人。 */
    List<PassengerResponse> listCurrentUserPassengers();

    /**
     * 按用户名和乘车人主键批量读取未脱敏快照，供票务服务创建订单时使用。
     *
     * @param username 乘车人归属用户名，同时作为分片键
     * @param passengerIds 需要读取的乘车人主键集合
     * @return 原始乘车人快照
     */
    List<PassengerActualResponse> listPassengerActualByIds(String username, List<Long> passengerIds);

    /** 为当前用户新增乘车人。 */
    PassengerResponse create(PassengerCreateRequest request);

    /** 修改当前用户拥有的乘车人。 */
    PassengerResponse update(Long passengerId, PassengerUpdateRequest request);

    /** 软删除当前用户拥有的乘车人。 */
    void delete(Long passengerId);
}
