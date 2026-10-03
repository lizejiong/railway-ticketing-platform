package com.lzj.railway.order.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 乘车人维度的订单检索关系，按证件号分片。 */
@Data
@TableName("t_order_item_passenger")
public class OrderItemPassengerDO {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private String orderSn;
    private Integer idType;
    private String idCard;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    @TableLogic(value = "0", delval = "1")
    @TableField("del_flag")
    private Integer delFlag;
}
