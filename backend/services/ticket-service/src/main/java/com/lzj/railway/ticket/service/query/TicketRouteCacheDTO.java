package com.lzj.railway.ticket.service.query;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 按地区缓存的列车区间信息。
 */
@Data
public class TicketRouteCacheDTO {

    /** 列车主键。 */
    private Long trainId;
    /** 出发站名称。 */
    private String departure;
    /** 到达站名称。 */
    private String arrival;
    /** 是否始发站。 */
    private Boolean departureFlag;
    /** 是否终到站。 */
    private Boolean arrivalFlag;
    /** 区间出发时间。 */
    private LocalDateTime departureTime;
    /** 区间到达时间。 */
    private LocalDateTime arrivalTime;
}
