package com.lzj.railway.order.dto.request;

import java.time.LocalDateTime;
import java.util.List;

/** 票务服务创建待支付订单时传入的完整行程与乘车人快照。 */
public record TicketOrderCreateRequest(
        Long userId,
        String username,
        Long trainId,
        String departure,
        String arrival,
        Integer source,
        LocalDateTime orderTime,
        LocalDateTime ridingDate,
        String trainNumber,
        LocalDateTime departureTime,
        LocalDateTime arrivalTime,
        List<TicketOrderItemCreateRequest> ticketOrderItems) {
}
