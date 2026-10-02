package com.lzj.railway.ticket.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 列车在一对出发站、到达站之间的运行关系。
 */
@Data
@TableName("t_train_station_relation")
public class TrainStationRelationDO {

    /** 关系主键。 */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    /** 列车主键。 */
    private Long trainId;
    /** 出发站。 */
    private String departure;
    /** 到达站。 */
    private String arrival;
    /** 区间出发站所属地区名称。 */
    private String startRegion;
    /** 区间到达站所属地区名称。 */
    private String endRegion;
    /** 是否始发站。 */
    private Boolean departureFlag;
    /** 是否终到站。 */
    private Boolean arrivalFlag;
    /** 区间出发时间。 */
    private LocalDateTime departureTime;
    /** 区间到达时间。 */
    private LocalDateTime arrivalTime;
    /** 创建时间。 */
    private LocalDateTime createTime;
    /** 更新时间。 */
    private LocalDateTime updateTime;
    /** 逻辑删除标记。 */
    private Integer delFlag;
}
