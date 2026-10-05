package com.lzj.railway.pay.common;

/** 支付单状态，名称与支付宝交易状态语义保持一致。 */
public enum PaymentStatus {
    WAIT_BUYER_PAY,
    TRADE_SUCCESS,
    TRADE_CLOSED,
    REFUND_SUCCESS
}
