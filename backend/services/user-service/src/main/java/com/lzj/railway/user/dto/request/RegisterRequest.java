package com.lzj.railway.user.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
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

    @Schema(description = "真实姓名，2-30 位中文、字母或间隔点", example = "张三")
    @NotBlank(message = "真实姓名不能为空")
    @Pattern(regexp = "^[\\p{L}·•]{2,30}$", message = "真实姓名格式不正确")
    private String realName;

    @Schema(description = "证件类型，当前仅支持 0：居民身份证", example = "0")
    @NotNull(message = "证件类型不能为空")
    @Min(value = 0, message = "当前仅支持居民身份证")
    @Max(value = 0, message = "当前仅支持居民身份证")
    private Integer idType;

    @Schema(description = "18 位居民身份证号码", example = "110101199003071234")
    @NotBlank(message = "证件号码不能为空")
    @Pattern(regexp = "^[1-9]\\d{5}(?:18|19|20)\\d{2}(?:0[1-9]|1[0-2])(?:0[1-9]|[12]\\d|3[01])\\d{3}[0-9Xx]$",
            message = "证件号码格式不正确")
    private String idCard;
}
