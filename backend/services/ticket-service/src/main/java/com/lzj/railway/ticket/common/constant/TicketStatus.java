package com.lzj.railway.ticket.common.constant;

/** 车票记录状态，后续支付和退票流程将在此状态机继续流转。 */
public final class TicketStatus {
    /** 已锁座、等待支付。 */
    public static final int UNPAID = 0;
    /** 已支付。 */
    public static final int PAID = 1;
    /** 已取消。 */
    public static final int CLOSED = 5;
    /** 已退款。 */
    public static final int REFUNDED = 7;

    private TicketStatus() {
    }
}
