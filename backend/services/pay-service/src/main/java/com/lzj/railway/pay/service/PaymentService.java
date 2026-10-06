package com.lzj.railway.pay.service;

import com.lzj.railway.pay.dto.request.CreatePaymentRequest;
import com.lzj.railway.pay.dto.request.RefundPaymentRequest;
import com.lzj.railway.pay.dto.response.PaymentInfoResponse;
import com.lzj.railway.pay.dto.response.PaymentResponse;

import java.time.LocalDateTime;

/** 支付单创建、查询与支付渠道回调处理。 */
public interface PaymentService {
    /** 为当前用户的待支付订单创建或复用支付单。 */
    PaymentResponse create(CreatePaymentRequest request);

    /** 按订单号查询当前用户的支付状态。 */
    PaymentInfoResponse queryByOrderSn(String orderSn);

    /** 当前用户确认本地 MOCK 支付单成功，仅在开发开关打开时可用。 */
    PaymentInfoResponse confirmMockPayment(String paySn);

    /** 应用支付宝异步通知，返回 false 时控制器应通知支付宝重试。 */
    boolean completeAliPay(String paySn, String tradeNo, Integer paidAmount, LocalDateTime paymentTime, String tradeStatus);

    /** 对票务域已校验的乘车人明细执行退款。 */
    void refund(RefundPaymentRequest request);
}
