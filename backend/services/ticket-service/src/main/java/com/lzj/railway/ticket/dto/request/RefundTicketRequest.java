package com.lzj.railway.ticket.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * 退票请求。
 *
 * @param type 退款类型：0 为部分退票，1 为整单退票
 * @param orderItemIds 部分退票时必须提供的订单明细 ID；整单退票可为空
 */
public record RefundTicketRequest(
        @Schema(description = "退款类型：0-部分退票，1-整单退票", example = "1") Integer type,
        @Schema(description = "部分退票的订单明细 ID 列表") List<Long> orderItemIds) {
}
