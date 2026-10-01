package com.lzj.railway.user.common.errorcode;

import com.lzj.railway.framework.convention.errorcode.ErrorCode;

/** 用户域对外稳定的错误码定义。 */
public enum UserErrorCode implements ErrorCode {

    USERNAME_ALREADY_EXISTS("U000001", "用户名已存在"),
    PHONE_ALREADY_BOUND("U000002", "手机号已绑定"),
    EMAIL_ALREADY_BOUND("U000003", "邮箱已绑定"),
    PASSWORD_INCORRECT("U000004", "密码错误"),
    ACCOUNT_NOT_FOUND("U000005", "账号不存在"),
    REGISTRATION_IN_PROGRESS("U000006", "注册请求处理中，请稍后重试"),
    VERIFICATION_CODE_INVALID("U000007", "验证码错误"),
    ACCOUNT_FROZEN("U000008", "账号已冻结"),
    PASSENGER_NOT_FOUND("U000009", "乘车人不存在"),
    PASSENGER_ACCESS_DENIED("U000010", "无权操作该乘车人"),
    AUTHENTICATION_REQUIRED("U000011", "请先登录"),
    REFRESH_TOKEN_INVALID("U000012", "Refresh Token 无效或已过期"),
    PASSENGER_ALREADY_EXISTS("U000013", "该乘车人已存在"),
    REGISTRATION_PARAMETER_INVALID("U000014", "注册参数不正确"),
    IDENTITY_DELETION_LIMIT_EXCEEDED("U000015", "该证件号注销账号次数过多，暂不允许重新注册");

    private final String code;
    private final String message;

    UserErrorCode(String code, String message) {
        this.code = code;
        this.message = message;
    }

    @Override
    public String code() {
        return code;
    }

    @Override
    public String message() {
        return message;
    }
}
