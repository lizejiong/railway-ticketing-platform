package com.lzj.railway.framework.starter.persistence.handler;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.lzj.railway.framework.starter.persistence.base.BaseDO;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.reflection.SystemMetaObject;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class PersistenceMetaObjectHandlerTest {

    private static final Instant NOW = Instant.parse("2026-09-28T12:00:00Z");
    private static final LocalDateTime EXPECTED_TIME = LocalDateTime.ofInstant(NOW, ZoneOffset.UTC);

    private final PersistenceMetaObjectHandler handler =
            new PersistenceMetaObjectHandler(Clock.fixed(NOW, ZoneOffset.UTC));

    @BeforeAll
    static void initializeTableInfo() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "test");
        TableInfoHelper.initTableInfo(assistant, TestDO.class);
    }

    @Test
    void shouldFillBaseFieldsOnInsert() {
        TestDO entity = new TestDO();

        handler.insertFill(SystemMetaObject.forObject(entity));

        assertThat(entity.getCreateTime()).isEqualTo(EXPECTED_TIME);
        assertThat(entity.getUpdateTime()).isEqualTo(EXPECTED_TIME);
        assertThat(entity.getDeleted()).isZero();
    }

    @Test
    void shouldKeepExplicitValuesOnInsert() {
        TestDO entity = new TestDO();
        LocalDateTime explicitTime = EXPECTED_TIME.minusDays(1);
        entity.setCreateTime(explicitTime);
        entity.setUpdateTime(explicitTime);
        entity.setDeleted(1);

        handler.insertFill(SystemMetaObject.forObject(entity));

        assertThat(entity.getCreateTime()).isEqualTo(explicitTime);
        assertThat(entity.getUpdateTime()).isEqualTo(explicitTime);
        assertThat(entity.getDeleted()).isOne();
    }

    @Test
    void shouldRefreshUpdateTimeOnUpdate() {
        TestDO entity = new TestDO();
        entity.setUpdateTime(EXPECTED_TIME.minusDays(1));

        handler.updateFill(SystemMetaObject.forObject(entity));

        assertThat(entity.getUpdateTime()).isEqualTo(EXPECTED_TIME);
        assertThat(entity.getCreateTime()).isNull();
        assertThat(entity.getDeleted()).isNull();
    }

    static final class TestDO extends BaseDO {
    }
}
