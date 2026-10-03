package com.lzj.railway.ticket.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 列车途经站点的站序记录。
 *
 * <p>每一行的 {@code departure} 和 {@code arrival} 表示相邻两个站点，
 * {@code sequence} 用于还原整趟列车的连续行程。</p>
 */
@Data
@TableName("t_train_station")
public class TrainStationDO {

    /** 主键。 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 列车主键。 */
    private Long trainId;

    /** 车站主键。 */
    private Long stationId;

    /** 当前站点在列车行程中的顺序。 */
    private String sequence;

    /** 当前站点，即该相邻区间的出发站。 */
    private String departure;

    /** 下一站，即该相邻区间的到达站；终到站为空。 */
    private String arrival;

    /** 始发地区。 */
    private String startRegion;

    /** 终到地区。 */
    private String endRegion;

    /** 到站时间。 */
    private LocalDateTime arrivalTime;

    /** 出站时间。 */
    private LocalDateTime departureTime;

    /** 停留时长，单位：分钟。 */
    private Integer stopoverTime;

    /** 创建时间。 */
    private LocalDateTime createTime;

    /** 更新时间。 */
    private LocalDateTime updateTime;

    /** 逻辑删除标记。 */
    private Integer delFlag;
}
