package com.lzj.railway.framework.starter.persistence.id;

import com.lzj.railway.framework.idgenerator.snowflake.IdGenerator;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

class MybatisPlusSnowflakeIdentifierGeneratorTest {

    @Test
    void shouldDelegateIdGeneration() {
        AtomicLong sequence = new AtomicLong(1000);
        IdGenerator idGenerator = sequence::incrementAndGet;
        MybatisPlusSnowflakeIdentifierGenerator generator =
                new MybatisPlusSnowflakeIdentifierGenerator(idGenerator);

        Long first = generator.nextId(new Object());
        Long second = generator.nextId(new Object());

        assertThat(first).isEqualTo(1001L);
        assertThat(second).isEqualTo(1002L);
    }
}
