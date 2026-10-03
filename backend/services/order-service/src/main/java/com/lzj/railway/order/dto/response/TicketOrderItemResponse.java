package com.lzj.railway.order.dto.response;

/** 订单明细的内部返回快照，供票务服务释放座位和余票。 */
public record TicketOrderItemResponse(
        String carriageNumber,
        Integer seatType,
        String seatNumber,
        Long passengerId,
        String realName,
        Integer idType,
        String idCard,
        String phone,
        Integer amount,
        Integer ticketType,
        Integer status) {
}
