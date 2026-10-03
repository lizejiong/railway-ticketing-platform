package com.lzj.railway.ticket.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 实体座位在一个可售区间内的库存记录。
 */
@Data
@TableName("t_seat")
public class SeatDO {

    /** 主键。 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 列车主键。 */
    private Long trainId;

    /** 车厢编号。 */
    private String carriageNumber;

    /** 座位编号。 */
    private String seatNumber;

    /** 席别编码。 */
    private Integer seatType;

    /** 当前记录适用的区间出发站。 */
    private String startStation;

    /** 当前记录适用的区间到达站。 */
    private String endStation;

    /** 票价，单位：分。 */
    private Integer price;

    /** 区间座位状态，见 {@code SeatStatus}。 */
    private Integer seatStatus;

    /** 创建时间。 */
    private LocalDateTime createTime;

    /** 更新时间。 */
    private LocalDateTime updateTime;

    /** 逻辑删除标记。 */
    private Integer delFlag;
}
