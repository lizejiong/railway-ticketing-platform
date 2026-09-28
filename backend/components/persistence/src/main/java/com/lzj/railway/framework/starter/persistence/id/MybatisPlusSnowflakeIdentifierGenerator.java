package com.lzj.railway.framework.starter.persistence.id;

import com.baomidou.mybatisplus.core.incrementer.IdentifierGenerator;
import com.lzj.railway.framework.idgenerator.snowflake.IdGenerator;

import java.util.Objects;

/**
 * 将框架统一的雪花 ID 生成器适配为 MyBatis-Plus 主键生成器。
 */
public final class MybatisPlusSnowflakeIdentifierGenerator implements IdentifierGenerator {

    private final IdGenerator idGenerator;

    public MybatisPlusSnowflakeIdentifierGenerator(IdGenerator idGenerator) {
        this.idGenerator = Objects.requireNonNull(idGenerator, "idGenerator must not be null");
    }

    @Override
    public Long nextId(Object entity) {
        return idGenerator.nextId();
    }
}
