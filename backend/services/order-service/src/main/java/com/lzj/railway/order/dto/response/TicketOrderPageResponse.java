package com.lzj.railway.order.dto.response;

import java.time.LocalDateTime;
import java.util.List;

/** 订单中心列表的轻量订单摘要。 */
public record TicketOrderPageResponse(
        String orderSn,
        String trainNumber,
        String departure,
        String arrival,
        LocalDateTime departureTime,
        LocalDateTime arrivalTime,
        LocalDateTime orderTime,
        Integer status,
        Integer totalAmount,
        List<String> passengerNames) {
}
