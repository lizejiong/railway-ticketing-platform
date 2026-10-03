package com.lzj.railway.user.dto.response;

/** 仅用于服务间下单编排的乘车人原始快照，不能直接用于面向用户的展示接口。 */
public record PassengerActualResponse(
        Long id,
        String realName,
        Integer idType,
        String idCard,
        Integer discountType,
        String phone,
        Integer verifyStatus) {
}
