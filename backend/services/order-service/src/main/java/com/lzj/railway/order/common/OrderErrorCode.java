package com.lzj.railway.order.common;

import com.lzj.railway.framework.convention.errorcode.ErrorCode;

/** 订单域对外稳定错误码。 */
public enum OrderErrorCode implements ErrorCode {
    ORDER_NOT_FOUND("O000001", "订单不存在"),
    ORDER_ACCESS_DENIED("O000002", "无权操作该订单"),
    ORDER_STATUS_NOT_CANCELLABLE("O000003", "当前订单状态不可取消"),
    ORDER_CREATE_FAILED("O000004", "订单创建失败");

    private final String code;
    private final String message;

    OrderErrorCode(String code, String message) {
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
