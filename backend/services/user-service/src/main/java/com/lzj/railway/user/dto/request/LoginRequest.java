package com.lzj.railway.user.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class LoginRequest {

    @Schema(description = "用户名、手机号或邮箱", example = "railway_user_01")
    @NotBlank(message = "账号不能为空")
    private String account;

    @Schema(description = "登录密码", example = "Railway123")
    @NotBlank(message = "密码不能为空")
    private String password;
}
