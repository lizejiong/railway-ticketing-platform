package com.lzj.railway.user.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户账号注销记录。
 *
 * <p>注册时按照证件类型和证件号统计历史注销次数，用于限制反复注册、注销的异常行为。</p>
 */
@Data
@TableName("t_user_deletion")
public class UserDeletionDO {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Integer idType;
    private String idCard;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private Integer delFlag;
}
