package com.lzj.railway.user.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 乘车人持久化对象。 */
@Data
@TableName("t_passenger")
public class PassengerDO {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    /** 用户名既是归属标识，也是乘车人表的分片键。 */
    private String username;
    private String realName;
    private Integer idType;
    private String idCard;
    private Integer discountType;
    private String phone;
    private LocalDateTime createDate;
    private Integer verifyStatus;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private Integer delFlag;
}
