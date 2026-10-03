package com.lzj.railway.order.common;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 单个乘车人的车票状态。 */
@Getter
@RequiredArgsConstructor
public enum OrderItemStatus {
    /** 待支付。 */
    PENDING_PAYMENT(0),
    /** 已支付。 */
    PAID(10),
    /** 已取消。 */
    CLOSED(30),
    /** 已退票。 */
    REFUNDED(40);

    private final int code;
}
