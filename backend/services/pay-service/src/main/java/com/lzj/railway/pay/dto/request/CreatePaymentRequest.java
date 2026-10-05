package com.lzj.railway.pay.dto.request;

import jakarta.validation.constraints.NotBlank;

/** 前端仅指定订单与支付渠道，金额由服务端从订单快照计算。 */
public record CreatePaymentRequest(
        @NotBlank(message = "订单号不能为空") String orderSn,
        String channel) {
}
