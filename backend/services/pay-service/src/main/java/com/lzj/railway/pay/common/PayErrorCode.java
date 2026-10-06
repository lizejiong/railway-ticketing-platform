package com.lzj.railway.pay.common;

import com.lzj.railway.framework.convention.errorcode.ErrorCode;

/** 支付域对外稳定错误码。 */
public enum PayErrorCode implements ErrorCode {
    AUTHENTICATION_REQUIRED("P000001", "请先登录"),
    ORDER_NOT_FOUND("P000002", "订单不存在或无权支付"),
    ORDER_NOT_PAYABLE("P000003", "当前订单不可支付"),
    PAYMENT_NOT_FOUND("P000004", "支付单不存在"),
    PAYMENT_AMOUNT_INVALID("P000005", "支付金额校验失败"),
    PAYMENT_CALLBACK_INVALID("P000006", "支付回调验签失败"),
    PAYMENT_CHANNEL_UNSUPPORTED("P000007", "暂不支持该支付渠道"),
    PAYMENT_CHANNEL_FAILED("P000008", "支付渠道调用失败"),
    REFUND_ITEM_ALREADY_PROCESSED("P000009", "乘车人已退款，请勿重复提交"),
    MOCK_PAYMENT_DISABLED("P000010", "本地模拟支付未启用");

    private final String code;
    private final String message;

    PayErrorCode(String code, String message) {
        this.code = code;
        this.message = message;
    }

    @Override
    public String code() {
        return code;
    }

    @Override
    public String message() {
        return message;
    }
}
