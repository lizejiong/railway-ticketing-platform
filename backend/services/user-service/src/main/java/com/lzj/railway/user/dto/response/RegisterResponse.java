package com.lzj.railway.user.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "注册成功后返回的用户基础信息")
public record RegisterResponse(
        @Schema(description = "用户 ID", example = "1880000000000000001") Long userId,
        @Schema(description = "用户名", example = "railway_user_01") String username,
        @Schema(description = "手机号", example = "13800138000") String phone,
        @Schema(description = "邮箱地址", example = "railway_user_01@example.com") String email) {
}
