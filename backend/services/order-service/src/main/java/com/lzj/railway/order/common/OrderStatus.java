package com.lzj.railway.order.common;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 订单主状态，支付域后续将在此基础上继续流转。 */
@Getter
@RequiredArgsConstructor
public enum OrderStatus {
    /** 已锁座但尚未支付。 */
    PENDING_PAYMENT(0),
    /** 已支付。 */
    PAID(10),
    /** 已完成乘车。 */
    COMPLETED(20),
    /** 未支付状态下已关闭。 */
    CLOSED(30);

    private final int code;
}
