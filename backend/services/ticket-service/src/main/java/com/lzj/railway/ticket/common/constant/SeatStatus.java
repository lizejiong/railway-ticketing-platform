package com.lzj.railway.ticket.common.constant;

/**
 * 实体座位在指定可售区间上的占用状态。
 */
public final class SeatStatus {

    /** 可售。 */
    public static final int AVAILABLE = 0;

    /** 已锁定，等待后续订单处理。 */
    public static final int LOCKED = 1;

    private SeatStatus() {
    }
}
