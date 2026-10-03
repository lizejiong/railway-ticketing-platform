package com.lzj.railway.order.dto.response;

import java.time.LocalDateTime;
import java.util.List;

/** 订单主记录与全部乘车人订单明细。 */
public record TicketOrderResponse(
        String orderSn,
        Long userId,
        String username,
        Long trainId,
        String trainNumber,
        String departure,
        String arrival,
        LocalDateTime departureTime,
        LocalDateTime arrivalTime,
        Integer status,
        List<TicketOrderItemResponse> passengerDetails) {
}
