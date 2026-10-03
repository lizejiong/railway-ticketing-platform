package com.lzj.railway.ticket.service.purchase;

/**
 * 已为乘车人锁定的实体座位。
 *
 * @param passengerId 乘车人主键
 * @param seatType 席别编码
 * @param carriageNumber 车厢编号
 * @param seatNumber 座位编号
 * @param price 票价，单位：分
 */
public record AllocatedSeat(
        Long passengerId,
        Integer seatType,
        String carriageNumber,
        String seatNumber,
        Integer price) {
}
