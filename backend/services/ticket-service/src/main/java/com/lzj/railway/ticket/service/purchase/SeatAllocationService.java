package com.lzj.railway.ticket.service.purchase;

import com.lzj.railway.ticket.dto.request.PurchaseTicketPassengerRequest;

import java.util.List;

/**
 * 实体座位选择、区间锁定与释放服务。
 */
public interface SeatAllocationService {

    /**
     * 按乘车人的席别选择座位，并锁定本次购票影响的全部区间记录。
     *
     * @param trainId 列车主键
     * @param passengers 乘车人与席别选择
     * @param affectedSegments 需要锁定的全部受影响区间
     * @return 已锁定的座位分配结果
     */
    List<AllocatedSeat> allocateAndLock(
            Long trainId,
            List<PurchaseTicketPassengerRequest> passengers,
            List<TrainRouteSegment> affectedSegments,
            List<String> chooseSeats);

    /**
     * 释放已分配座位在本次行程影响的全部区间记录。
     *
     * @param trainId 列车主键
     * @param allocatedSeats 已锁定座位
     * @param affectedSegments 需要释放的全部区间
     */
    void unlock(
            Long trainId,
            List<AllocatedSeat> allocatedSeats,
            List<TrainRouteSegment> affectedSegments);
}
