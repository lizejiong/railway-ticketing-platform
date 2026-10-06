package com.lzj.railway.pay.common;

/** 当前支持的支付渠道编码。 */
public enum PaymentChannel {
    /** 支付宝页面支付。 */
    ALIPAY,
    /** 仅用于本地开发和接口联调的模拟支付。 */
    MOCK
}
