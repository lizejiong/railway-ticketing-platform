package com.lzj.railway.pay.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 支付平台侧支付单，与业务订单一一对应并保存第三方交易凭证。 */
@Data
@TableName("t_pay")
public class PayDO {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private String paySn;
    private String orderSn;
    private String outOrderSn;
    private String channel;
    private String tradeType;
    private String subject;
    private String orderRequestId;
    /** 金额统一以分保存，避免浮点金额误差。 */
    private Integer totalAmount;
    private String tradeNo;
    private LocalDateTime gmtPayment;
    private Integer payAmount;
    private String status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    @TableLogic(value = "0", delval = "1")
    @TableField("del_flag")
    private Integer delFlag;
}
