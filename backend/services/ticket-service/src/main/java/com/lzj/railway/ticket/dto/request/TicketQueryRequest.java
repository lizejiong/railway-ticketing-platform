package com.lzj.railway.ticket.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

/**
 * 车次区间查询请求参数。
 *
 * @param departure 出发站名称
 * @param arrival 到达站名称
 * @param departureDate 乘车日期
 */
public record TicketQueryRequest(
        @Schema(description = "出发站名称", example = "北京南")
        @NotBlank(message = "出发站不能为空") @Size(max = 64) String departure,
        @Schema(description = "到达站名称", example = "上海虹桥")
        @NotBlank(message = "到达站不能为空") @Size(max = 64) String arrival,
        @Schema(description = "乘车日期", example = "2026-10-02")
        @NotNull(message = "乘车日期不能为空")
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate departureDate) {
}
