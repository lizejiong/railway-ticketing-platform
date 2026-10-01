package com.lzj.railway.ticket.dao.mapper.dto;

/**
 * 指定列车和席别在一个区间内的可售座位汇总。
 *
 * @param trainId 列车主键
 * @param seatType 席别类型
 * @param remainingTickets 可售余票数
 */
public record SeatRemainingDTO(Long trainId, Integer seatType, Integer remainingTickets) {
}
