package com.lzj.railway.ticket.remote.dto;

/** 用户域返回的原始乘车人快照。 */
public record PassengerActualRemoteResponse(Long id, String realName, Integer idType, String idCard,
                                            Integer discountType, String phone, Integer verifyStatus) {
}
