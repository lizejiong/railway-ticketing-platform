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
    STATION_NOT_FOUND("T000003", "出发站或到达站不存在"),

    /** 提交购票的请求字段不符合约束。 */
    PURCHASE_PARAMETER_INVALID("T000004", "购票参数不正确"),

    /** 指定的列车不存在或已被删除。 */
    TRAIN_NOT_FOUND("T000005", "列车不存在"),

    /** 列车当前不处于可售状态。 */
    TRAIN_NOT_FOR_SALE("T000006", "列车当前不可售"),

    /** 出发站、到达站或二者之间的站序不构成有效行程。 */
    JOURNEY_INVALID("T000007", "购票行程不合法"),

    /** 当前请求席别的余票不足。 */
    TICKET_SOLD_OUT("T000008", "余票不足"),

    /** 高并发下实体座位条件更新失败。 */
    SEAT_LOCK_FAILED("T000009", "座位锁定失败，请重新查询余票后重试"),

    /** 当前请求缺少有效登录用户。 */
    AUTHENTICATION_REQUIRED("T000010", "请先登录"),

    /** 乘车人信息无法从用户域获得或不属于当前用户。 */
    PASSENGER_NOT_AVAILABLE("T000011", "乘车人不存在或无权使用"),

    /** 订单服务未能成功完成创建或查询。 */
    ORDER_SERVICE_FAILED("T000012", "订单服务调用失败");

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
