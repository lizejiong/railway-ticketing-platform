package com.lzj.railway.user.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "当前登录用户资料")
public record UserProfileResponse(
        @Schema(description = "用户 ID", example = "1880000000000000001") Long userId,
        @Schema(description = "用户名", example = "railway_user_01") String username,
        @Schema(description = "真实姓名", nullable = true, example = "张三") String realName,
        @Schema(description = "国家或地区代码", example = "0") String region,
        @Schema(description = "证件类型", nullable = true, example = "0") Integer idType,
        @Schema(description = "脱敏证件号", nullable = true, example = "1101**********1234") String idCard,
        @Schema(description = "脱敏手机号", example = "138****8000") String phone,
        @Schema(description = "脱敏邮箱", example = "r***@example.com") String email,
        @Schema(description = "用户类型", nullable = true, example = "0") Integer userType,
        @Schema(description = "实名认证状态", nullable = true, example = "0") Integer verifyStatus) {
}
