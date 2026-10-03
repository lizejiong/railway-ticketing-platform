package com.lzj.railway.ticket.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * 提交购票请求。
 *
 * @param trainId 列车主键
 * @param departure 出发站编码，使用 t_station.code
 * @param arrival 到达站编码，使用 t_station.code
 * @param passengers 乘车人与席别选择
 * @param chooseSeats 用户期望座位；当前阶段仅保留参数，不支持指定或相邻选座
 */
public record PurchaseTicketRequest(
        @Schema(description = "列车 ID", example = "3")
        @NotNull(message = "列车不能为空") Long trainId,
        @Schema(description = "出发站编码", example = "VNP")
        @NotBlank(message = "出发站不能为空") String departure,
        @Schema(description = "到达站编码", example = "NKH")
        @NotBlank(message = "到达站不能为空") String arrival,
        @Schema(description = "乘车人与席别")
        @NotEmpty(message = "乘车人不能为空")
        List<@Valid PurchaseTicketPassengerRequest> passengers,
        @Schema(description = "期望座位；暂不支持指定或相邻选座")
        List<String> chooseSeats) {
}
