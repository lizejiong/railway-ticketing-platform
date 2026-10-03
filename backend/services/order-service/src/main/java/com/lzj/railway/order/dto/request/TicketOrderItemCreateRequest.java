package com.lzj.railway.order.dto.request;

/** 订单服务写入的单个乘车人车票快照。 */
public record TicketOrderItemCreateRequest(
        String carriageNumber,
        Integer seatType,
        String seatNumber,
        Long passengerId,
        String realName,
        Integer idType,
        String idCard,
        String phone,
        Integer amount,
        Integer ticketType) {
}
