package com.lzj.railway.ticket.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 列车基础信息持久化对象。
 */
@Data
@TableName("t_train")
public class TrainDO {

    /** 列车主键。 */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    /** 车次号。 */
    private String trainNumber;
    /** 列车类型。 */
    private Integer trainType;
    /** 列车品牌编码集合。 */
    private String trainBrand;
    /** 售卖状态，0 表示可售。 */
    private Integer saleStatus;
    /** 创建时间。 */
    private LocalDateTime createTime;
    /** 更新时间。 */
    private LocalDateTime updateTime;
    /** 逻辑删除标记。 */
    private Integer delFlag;
}
