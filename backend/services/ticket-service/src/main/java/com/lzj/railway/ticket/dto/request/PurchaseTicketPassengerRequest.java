package com.lzj.railway.ticket.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * 单个乘车人的购票席别选择。
 *
 * @param passengerId 当前用户名下的乘车人主键
 * @param seatType 席别编码，取值范围与 t_seat.seat_type 保持一致
 */
public record PurchaseTicketPassengerRequest(
        @Schema(description = "乘车人 ID", example = "1")
        @NotNull(message = "乘车人不能为空") Long passengerId,
        @Schema(description = "席别编码", example = "1")
        @NotNull(message = "席别不能为空")
        @Min(value = 0, message = "席别不合法")
        @Max(value = 5, message = "席别不合法") Integer seatType) {
}
