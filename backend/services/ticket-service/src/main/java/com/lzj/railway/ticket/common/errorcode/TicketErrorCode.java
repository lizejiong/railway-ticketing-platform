package com.lzj.railway.ticket.common.errorcode;

import com.lzj.railway.framework.convention.errorcode.ErrorCode;

/**
 * 票务域对外稳定错误码。
 */
public enum TicketErrorCode implements ErrorCode {

    /** 车票查询的站点或日期参数不符合要求。 */
    QUERY_PARAMETER_INVALID("T000001", "车票查询参数不正确"),

    /** 乘车日期早于当前日期，不允许查询。 */
    DEPARTURE_DATE_INVALID("T000002", "乘车日期不能早于当天"),

    /** 出发站或到达站编码未映射到有效车站。 */
    STATION_NOT_FOUND("T000003", "出发站或到达站不存在");

    private final String code;
    private final String message;

    TicketErrorCode(String code, String message) {
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
