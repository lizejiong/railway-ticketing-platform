package com.lzj.railway.gateway.common.errorcode;

import com.lzj.railway.framework.convention.errorcode.ErrorCode;

/** 网关层对外稳定的错误码。 */
public enum GatewayErrorCode implements ErrorCode {

    UNAUTHORIZED("G000001", "登录状态无效或已过期");

    private final String code;
    private final String message;

    GatewayErrorCode(String code, String message) {
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
