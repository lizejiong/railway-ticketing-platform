package com.lzj.railway.ticket.common.constant;

/**
 * 实体座位在指定可售区间上的占用状态。
 */
public final class SeatStatus {

    /** 可售。 */
    public static final int AVAILABLE = 0;

    /** 已锁定，等待后续订单处理。 */
    public static final int LOCKED = 1;

    /** 已支付并最终售出，不再允许取消后直接释放。 */
    public static final int SOLD = 2;

    private SeatStatus() {
    }
}
