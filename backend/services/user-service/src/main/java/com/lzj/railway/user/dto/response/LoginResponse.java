package com.lzj.railway.user.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "登录成功后返回的令牌与用户基础信息")
public record LoginResponse(
        @Schema(description = "用于访问受保护接口的 JWT", example = "eyJhbGciOiJIUzI1NiJ9.example-access-token") String accessToken,
        @Schema(description = "用于换取新 Access Token 的令牌", example = "6b7ab6a0d4f34ffb941b81d2e8a8f11b") String refreshToken,
        @Schema(description = "Access Token 有效期，单位为秒", example = "900") long accessTokenExpiresIn,
        UserSummary user) {

    @Schema(description = "登录用户基础信息")
    public record UserSummary(
            @Schema(description = "用户 ID", example = "1880000000000000001") Long userId,
            @Schema(description = "用户名", example = "railway_user_01") String username,
            @Schema(description = "实名姓名；未实名时为 null", nullable = true, example = "张三") String realName) {
    }
}
