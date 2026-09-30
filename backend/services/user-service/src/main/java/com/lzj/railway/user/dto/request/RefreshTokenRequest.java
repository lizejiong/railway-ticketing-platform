package com.lzj.railway.user.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Refresh Token 操作请求")
public record RefreshTokenRequest(
        @NotBlank(message = "Refresh Token 不能为空")
        @Schema(description = "登录或上次刷新时签发的不透明 Refresh Token")
        String refreshToken) {
}
