package com.lzj.railway.ticket.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * 车次区间查询响应。
 *
 * @param trainId 列车主键
 * @param trainNumber 车次号
 * @param departure 出发站
 * @param arrival 到达站
 * @param departureTime 出发时刻，格式 HH:mm
 * @param arrivalTime 到达时刻，格式 HH:mm
 * @param durationMinutes 区间历时，单位分钟
 * @param departureFlag 是否为始发站
 * @param arrivalFlag 是否为终到站
 * @param trainType 列车类型
 * @param trainBrand 列车品牌编码集合
 * @param saleStatus 售卖状态
 * @param seatClasses 可购买席别
 */
public record TicketQueryResponse(
        @Schema(description = "列车 ID") Long trainId,
        @Schema(description = "车次号") String trainNumber,
        @Schema(description = "出发站") String departure,
        @Schema(description = "到达站") String arrival,
        @Schema(description = "出发时刻") String departureTime,
        @Schema(description = "到达时刻") String arrivalTime,
        @Schema(description = "区间历时，单位：分钟") Long durationMinutes,
        @Schema(description = "是否始发站") Boolean departureFlag,
        @Schema(description = "是否终到站") Boolean arrivalFlag,
        @Schema(description = "列车类型") Integer trainType,
        @Schema(description = "列车品牌") String trainBrand,
        @Schema(description = "售卖状态，0：可售") Integer saleStatus,
        @Schema(description = "可购买席别") List<SeatClassResponse> seatClasses) {
}
