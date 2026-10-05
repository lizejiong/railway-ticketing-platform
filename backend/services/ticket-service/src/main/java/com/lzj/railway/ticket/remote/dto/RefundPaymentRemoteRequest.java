package com.lzj.railway.ticket.remote.dto;

import java.util.List;

/** 票务域完成归属与状态校验后提交给支付域的退款快照。 */
public record RefundPaymentRemoteRequest(String orderSn, Long userId, String username, Long trainId,
                                         String trainNumber, String departure, String arrival,
                                         List<RefundPaymentItemRemoteRequest> items) {
}
