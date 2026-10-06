package com.lzj.railway.order.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 一个乘车人对应的一条订单明细。 */
@Data
@TableName("t_order_item")
public class OrderItemDO {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private String orderSn;
    private Long userId;
    private String username;
    private Long trainId;
    private String carriageNumber;
    private Integer seatType;
    private String seatNumber;
    private String realName;
    private Integer idType;
    private String idCard;
    private String phone;
    private Integer status;
    private Integer amount;
    private Integer ticketType;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    @TableLogic(value = "0", delval = "1")
    @TableField("del_flag")
    private Integer delFlag;
}
