package com.lzj.railway.framework.starter.persistence.base;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableLogic;
import lombok.Getter;
import lombok.Setter;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 持久化对象公共基础属性。
 * <p>
 * 业务实体继承该类后，创建时间、修改时间和逻辑删除标记由持久层组件统一维护。
 */
@Getter
@Setter
public abstract class BaseDO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 数据创建时间。 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /** 数据最后修改时间。 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    /** 逻辑删除标记：0 未删除，1 已删除。 */
    @TableLogic(value = "0", delval = "1")
    @TableField(fill = FieldFill.INSERT)
    private Integer deleted;
}
