package com.lzj.railway.framework.starter.persistence.handler;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.lzj.railway.framework.common.enums.DeleteEnum;
import org.apache.ibatis.reflection.MetaObject;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * 统一填充 {@code BaseDO} 中的审计字段和逻辑删除标记。
 */
public final class PersistenceMetaObjectHandler implements MetaObjectHandler {

    private static final String CREATE_TIME = "createTime";
    private static final String UPDATE_TIME = "updateTime";
    private static final String DELETED = "deleted";

    private final Clock clock;

    public PersistenceMetaObjectHandler() {
        this(Clock.systemDefaultZone());
    }

    PersistenceMetaObjectHandler(Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    @Override
    public void insertFill(MetaObject metaObject) {
        LocalDateTime now = LocalDateTime.now(clock);
        strictInsertFill(metaObject, CREATE_TIME, LocalDateTime.class, now);
        strictInsertFill(metaObject, UPDATE_TIME, LocalDateTime.class, now);
        strictInsertFill(metaObject, DELETED, Integer.class, DeleteEnum.NORMAL.code());
    }

    @Override
    public void updateFill(MetaObject metaObject) {
        setFieldValByName(UPDATE_TIME, LocalDateTime.now(clock), metaObject);
    }
}
