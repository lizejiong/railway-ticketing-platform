package com.lzj.railway.user.service;

import com.lzj.railway.user.dto.request.PassengerCreateRequest;
import com.lzj.railway.user.dto.request.PassengerUpdateRequest;
import com.lzj.railway.user.dto.response.PassengerResponse;

import java.util.List;

/** 当前登录用户的乘车人管理服务。 */
public interface PassengerService {

    /** 查询当前用户的全部有效乘车人。 */
    List<PassengerResponse> listCurrentUserPassengers();

    /** 为当前用户新增乘车人。 */
    PassengerResponse create(PassengerCreateRequest request);

    /** 修改当前用户拥有的乘车人。 */
    PassengerResponse update(Long passengerId, PassengerUpdateRequest request);

    /** 软删除当前用户拥有的乘车人。 */
    void delete(Long passengerId);
}
