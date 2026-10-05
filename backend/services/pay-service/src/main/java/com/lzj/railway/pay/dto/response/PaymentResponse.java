package com.lzj.railway.pay.dto.response;

/** 创建支付单或重复获取支付页后的返回结果。 */
public record PaymentResponse(String paySn, String orderSn, String status, String paymentPage) {
}
