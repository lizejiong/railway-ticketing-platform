package com.lzj.railway.pay.dto.response;

import java.time.LocalDateTime;

/** 支付状态查询结果，金额单位为分。 */
public record PaymentInfoResponse(String paySn, String orderSn, Integer totalAmount, Integer payAmount,
                                  String channel, String status, LocalDateTime paymentTime) {
}
