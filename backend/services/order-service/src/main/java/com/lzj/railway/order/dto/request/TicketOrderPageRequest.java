package com.lzj.railway.order.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/** 当前用户订单分页查询条件。 */
public record TicketOrderPageRequest(
        @Min(value = 1, message = "页码必须大于等于 1") Long pageNo,
        @Min(value = 1, message = "每页大小必须大于等于 1")
        @Max(value = 20, message = "每页大小不能超过 20") Long pageSize,
        Integer status) {

    /** 返回框架分页组件使用的安全页码。 */
    public long current() {
        return pageNo == null ? 1L : pageNo;
    }

    /** 返回框架分页组件使用的安全页大小。 */
    public long size() {
        return pageSize == null ? 10L : pageSize;
    }
}
