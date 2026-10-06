package com.lzj.railway.ticket.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

/**
 * 单个席别的票价与余票信息。
 *
 * @param seatType 席别类型编码
 * @param remainingTickets 当前区间可售余票数
 * @param price 票价，单位为元
 */
public record SeatClassResponse(
        @Schema(description = "席别类型") Integer seatType,
        @Schema(description = "当前区间余票数") Integer remainingTickets,
        @Schema(description = "票价，单位：元") BigDecimal price) {
}
