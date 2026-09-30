package com.lzj.railway.user.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class RegisterRequest {

    @Schema(description = "4-32 位字母、数字或下划线", example = "railway_user_01")
    @NotBlank(message = "用户名不能为空")
    @Pattern(regexp = "^[A-Za-z0-9_]{4,32}$", message = "用户名必须为 4-32 位字母、数字或下划线")
    private String username;

    @Schema(description = "8-72 位且同时包含字母和数字", example = "Railway123")
    @NotBlank(message = "密码不能为空")
    @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).{8,72}$", message = "密码必须为 8-72 位且同时包含字母和数字")
    private String password;

    @Schema(description = "中国大陆手机号", example = "13800138000")
    @NotBlank(message = "手机号不能为空")
    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确")
    private String phone;

    @Schema(description = "邮箱地址", example = "railway_user_01@example.com")
    @NotBlank(message = "邮箱不能为空")
    @Email(message = "邮箱格式不正确")
    private String email;
}
