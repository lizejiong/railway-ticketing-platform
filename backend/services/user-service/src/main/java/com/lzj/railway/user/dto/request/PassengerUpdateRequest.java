package com.lzj.railway.user.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** 修改乘车人请求。 */
public record PassengerUpdateRequest(
        @Schema(description = "真实姓名", example = "李四")
        @NotBlank(message = "真实姓名不能为空")
        @Size(max = 30, message = "真实姓名不能超过 30 个字符")
        String realName,

        @Schema(description = "证件类型，0 表示身份证", example = "0")
        @NotNull(message = "证件类型不能为空")
        @Min(value = 0, message = "证件类型不正确")
        @Max(value = 9, message = "证件类型不正确")
        Integer idType,

        @Schema(description = "证件号码", example = "110101199002022345")
        @NotBlank(message = "证件号码不能为空")
        @Size(max = 30, message = "证件号码不能超过 30 个字符")
        String idCard,

        @Schema(description = "优惠类型，0 表示成人", example = "0")
        @NotNull(message = "优惠类型不能为空")
        @Min(value = 0, message = "优惠类型不正确")
        @Max(value = 9, message = "优惠类型不正确")
        Integer discountType,

        @Schema(description = "手机号", example = "13900139000")
        @NotBlank(message = "手机号不能为空")
        @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确")
        String phone) {
}
