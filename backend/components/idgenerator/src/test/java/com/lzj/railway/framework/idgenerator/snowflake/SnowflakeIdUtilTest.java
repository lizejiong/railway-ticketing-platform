package com.lzj.railway.framework.idgenerator.snowflake;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SnowflakeIdUtilTest {

    @Test
    void shouldCreateGeneratorFromWorkerNodeAssigner() {
        SnowflakeIdGenerator generator = SnowflakeIdUtil.create(new FixedWorkerNodeAssigner(107));

        SnowflakeId parsed = SnowflakeIdUtil.parse(generator.nextId());

        assertThat(parsed.nodeId()).isEqualTo(107);
    }

    @Test
    void shouldParseIdWithCustomEpoch() {
        long epoch = 1_700_000_000_000L;
        long timestamp = epoch + 123;
        SnowflakeIdGenerator generator = new SnowflakeIdGenerator(
                new WorkerNode(133),
                epoch,
                Clock.fixed(Instant.ofEpochMilli(timestamp), ZoneId.of("UTC"))
        );

        SnowflakeId parsed = SnowflakeIdUtil.parse(generator.nextId(), epoch);

        assertThat(parsed).isEqualTo(new SnowflakeId(timestamp, 133, 0));
    }

    @Test
    void shouldRejectNullAssignerAndNegativeId() {
        assertThatThrownBy(() -> SnowflakeIdUtil.create(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("worker node assigner must not be null");
        assertThatThrownBy(() -> SnowflakeIdUtil.parse(-1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("id must not be negative");
    }
}
