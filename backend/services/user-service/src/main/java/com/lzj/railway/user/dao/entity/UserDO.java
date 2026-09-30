package com.lzj.railway.user.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_user")
public class UserDO {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private String username;
    private String password;
    private String realName;
    private String region;
    private Integer idType;
    private String idCard;
    private String phone;
    private String telephone;
    private String mail;
    private Integer userType;
    private Integer verifyStatus;
    private String postCode;
    private String address;
    private Long deletionTime;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private Integer delFlag;
}
