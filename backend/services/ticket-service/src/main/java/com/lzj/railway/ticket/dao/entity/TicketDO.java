package com.lzj.railway.ticket.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 已分配座位对应的车票记录。 */
@Data
@TableName("t_ticket")
public class TicketDO {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private String username;
    private Long trainId;
    private String carriageNumber;
    private String seatNumber;
    private Long passengerId;
    private Integer ticketStatus;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    @TableLogic(value = "0", delval = "1")
    @TableField("del_flag")
    private Integer delFlag;
}
