package com.lzj.railway.pay.dto.request;

import java.util.List;

/** 来自票务域、已完成归属与状态校验的退款快照。 */
public record RefundPaymentRequest(String orderSn, Long userId, String username, Long trainId, String trainNumber,
                                   String departure, String arrival, List<RefundPaymentItem> items) {
    public record RefundPaymentItem(Long orderItemId, Integer amount, Integer seatType, String carriageNumber,
                                    String seatNumber, Integer idType, String idCard, String realName) {
    }
}
