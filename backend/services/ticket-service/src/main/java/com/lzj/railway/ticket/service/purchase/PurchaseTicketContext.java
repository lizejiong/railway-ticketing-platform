package com.lzj.railway.ticket.service.purchase;

import com.lzj.railway.ticket.dto.request.PurchaseTicketRequest;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * 购票责任链在各处理器之间传递的上下文。
 */
@Getter
@Setter
@RequiredArgsConstructor
public class PurchaseTicketContext {

    /** 客户端提交的原始购票请求。 */
    private final PurchaseTicketRequest request;

    /** 由车站编码解析出的出发站名称。 */
    private String departureName;

    /** 由车站编码解析出的到达站名称。 */
    private String arrivalName;

    /** 购票行程覆盖的相邻区间。 */
    private List<TrainRouteSegment> routeSegments;
}
