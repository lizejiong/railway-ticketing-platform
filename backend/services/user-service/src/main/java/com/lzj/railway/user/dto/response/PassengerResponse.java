package com.lzj.railway.user.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/** 乘车人响应，证件号码和手机号均已脱敏。 */
public record PassengerResponse(
        @Schema(description = "乘车人 ID") Long id,
        @Schema(description = "真实姓名") String realName,
        @Schema(description = "证件类型") Integer idType,
        @Schema(description = "脱敏证件号码") String idCard,
        @Schema(description = "优惠类型") Integer discountType,
        @Schema(description = "脱敏手机号") String phone,
        @Schema(description = "创建日期") LocalDateTime createDate,
        @Schema(description = "核验状态") Integer verifyStatus) {
}
