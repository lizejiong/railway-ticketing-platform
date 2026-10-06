package com.lzj.railway.ticket.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

/**
 * 车次区间查询请求参数。
 *
 * @param fromStation 出发站编码，对应 t_station.code
 * @param toStation 到达站编码，对应 t_station.code
 * @param departureDate 乘车日期
 */
public record TicketQueryRequest(
        @Schema(description = "出发站编码，对应 t_station.code", example = "VNP")
        @NotBlank(message = "出发站编码不能为空")
        @Pattern(regexp = "^[A-Za-z0-9]{2,16}$", message = "出发站编码格式不正确") String fromStation,
        @Schema(description = "到达站编码，对应 t_station.code", example = "NKH")
        @NotBlank(message = "到达站编码不能为空")
        @Pattern(regexp = "^[A-Za-z0-9]{2,16}$", message = "到达站编码格式不正确") String toStation,
        @Schema(description = "乘车日期", example = "2026-10-02")
        @NotNull(message = "乘车日期不能为空")
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate departureDate) {
}
