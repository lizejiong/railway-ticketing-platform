package com.lzj.railway.ticket.service;

import com.lzj.railway.ticket.dto.request.TicketQueryRequest;
import com.lzj.railway.ticket.dto.response.TicketQueryResponse;

import java.util.List;

/**
 * 车次与余票查询服务。
 */
public interface TicketQueryService {

    /**
     * 查询指定区间和乘车日期下的可售车次。
     *
     * @param request 查询条件
     * @return 可售车次及其席别余票
     */
    List<TicketQueryResponse> query(TicketQueryRequest request);
}
