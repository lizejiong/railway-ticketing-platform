package com.lzj.railway.user.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_user_phone")
public class UserPhoneDO {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private String username;
    private String phone;
    private Long deletionTime;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private Integer delFlag;
}
