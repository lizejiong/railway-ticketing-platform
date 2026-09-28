package com.lzj.railway.framework.starter.persistence.base;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableLogic;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.assertj.core.api.Assertions.assertThat;

class BaseDOTest {

    @Test
    void shouldDeclareExpectedFillAndLogicalDeleteRules() throws NoSuchFieldException {
        Field createTime = BaseDO.class.getDeclaredField("createTime");
        Field updateTime = BaseDO.class.getDeclaredField("updateTime");
        Field deleted = BaseDO.class.getDeclaredField("deleted");

        assertThat(createTime.getAnnotation(TableField.class).fill()).isEqualTo(FieldFill.INSERT);
        assertThat(updateTime.getAnnotation(TableField.class).fill()).isEqualTo(FieldFill.INSERT_UPDATE);
        assertThat(deleted.getAnnotation(TableField.class).fill()).isEqualTo(FieldFill.INSERT);
        assertThat(deleted.getAnnotation(TableLogic.class).value()).isEqualTo("0");
        assertThat(deleted.getAnnotation(TableLogic.class).delval()).isEqualTo("1");
    }
}
