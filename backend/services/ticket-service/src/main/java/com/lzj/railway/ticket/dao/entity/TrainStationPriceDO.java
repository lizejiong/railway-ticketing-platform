package com.lzj.railway.ticket.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 列车区间席别票价持久化对象，价格以分存储。
 */
@Data
@TableName("t_train_station_price")
public class TrainStationPriceDO {

    /** 票价主键。 */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    /** 列车主键。 */
    private Long trainId;
    /** 出发站。 */
    private String departure;
    /** 到达站。 */
    private String arrival;
    /** 席别类型。 */
    private Integer seatType;
    /** 票价，单位为分。 */
    private Integer price;
    /** 创建时间。 */
    private LocalDateTime createTime;
    /** 更新时间。 */
    private LocalDateTime updateTime;
    /** 逻辑删除标记。 */
    private Integer delFlag;
}
