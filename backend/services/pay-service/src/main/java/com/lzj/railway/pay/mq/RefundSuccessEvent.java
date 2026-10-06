package com.lzj.railway.pay.mq;

import com.lzj.railway.pay.dto.request.RefundPaymentRequest;

import java.util.List;

/** 支付渠道退款确认后的跨域事件。 */
public record RefundSuccessEvent(String orderSn, Long trainId, String departure, String arrival,
                                 List<RefundPaymentRequest.RefundPaymentItem> items) {
}
