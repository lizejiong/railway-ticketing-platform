package com.lzj.railway.pay.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 退款执行快照，保留票、乘车人与退款金额，便于后续审计和对账。 */
@Data
@TableName("t_refund")
public class RefundDO {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private String paySn;
    private String orderSn;
    /** 对应订单明细，作为逐乘车人退款的幂等键。 */
    private Long orderItemId;
    private String tradeNo;
    private Integer amount;
    private Long userId;
    private String username;
    private Long trainId;
    private String trainNumber;
    private LocalDateTime ridingDate;
    private String departure;
    private String arrival;
    private LocalDateTime departureTime;
    private LocalDateTime arrivalTime;
    private Integer seatType;
    private Integer idType;
    private String idCard;
    private String realName;
    private Integer status;
    private LocalDateTime refundTime;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    @TableLogic(value = "0", delval = "1")
    @TableField("del_flag")
    private Integer delFlag;
}
