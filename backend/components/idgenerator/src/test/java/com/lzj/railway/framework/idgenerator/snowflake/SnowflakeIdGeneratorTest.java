package com.lzj.railway.framework.idgenerator.snowflake;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SnowflakeIdGeneratorTest {

    @Test
    void shouldGenerateIncreasingIdsAndPreserveNodeInformation() {
        SnowflakeIdGenerator generator = new SnowflakeIdGenerator(new WorkerNode(66));

        long first = generator.nextId();
        long second = generator.nextId();
        SnowflakeId parsed = SnowflakeIdUtil.parse(first);

        assertThat(second).isGreaterThan(first);
        assertThat(parsed.nodeId()).isEqualTo(66);
        assertThat(parsed.timestamp()).isLessThanOrEqualTo(Instant.now().toEpochMilli());
    }

    @Test
    void shouldGenerateUniqueIdsConcurrently() throws Exception {
        SnowflakeIdGenerator generator = new SnowflakeIdGenerator(new WorkerNode(1));
        ExecutorService executor = Executors.newFixedThreadPool(8);
        try {
            List<Callable<List<Long>>> tasks = java.util.stream.IntStream.range(0, 8)
                    .<Callable<List<Long>>>mapToObj(ignored -> () -> java.util.stream.LongStream.range(0, 1_000)
                            .map(ignoredId -> generator.nextId())
                            .boxed()
                            .toList())
                    .toList();
            List<Future<List<Long>>> futures = executor.invokeAll(tasks);
            Set<Long> ids = new HashSet<>();
            for (Future<List<Long>> future : futures) {
                ids.addAll(future.get());
            }

            assertThat(ids).hasSize(8_000);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void shouldRejectInvalidWorkerNodeAndTimestampBeforeEpoch() {
        assertThatThrownBy(() -> new SnowflakeIdGenerator(new WorkerNode(1_024)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("node id must be between 0 and 1023");

        SnowflakeIdGenerator generator = new SnowflakeIdGenerator(
                new WorkerNode(0),
                SnowflakeIdGenerator.DEFAULT_EPOCH,
                Clock.fixed(Instant.ofEpochMilli(SnowflakeIdGenerator.DEFAULT_EPOCH - 1), ZoneId.of("UTC"))
        );
        assertThatThrownBy(generator::nextId)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("current timestamp must not be before epoch");
    }

    @Test
    void shouldRejectClockRollback() {
        MutableClock clock = new MutableClock(SnowflakeIdGenerator.DEFAULT_EPOCH + 10);
        SnowflakeIdGenerator generator = new SnowflakeIdGenerator(new WorkerNode(0), SnowflakeIdGenerator.DEFAULT_EPOCH, clock);
        generator.nextId();
        clock.setMillis(SnowflakeIdGenerator.DEFAULT_EPOCH + 9);

        assertThatThrownBy(generator::nextId)
                .isInstanceOfSatisfying(ClockMovedBackwardsException.class, exception ->
                        assertThat(exception.getOffsetMillis()).isEqualTo(1));
    }

    @Test
    void shouldRejectIdGenerationWhenWorkerNodeLeaseIsLost() {
        WorkerNodeAssigner assigner = new WorkerNodeAssigner() {
            @Override
            public WorkerNode assign() {
                return new WorkerNode(0);
            }

            @Override
            public void verifyLease() {
                throw new WorkerNodeLeaseLostException();
            }
        };

        assertThatThrownBy(() -> new SnowflakeIdGenerator(assigner).nextId())
                .isInstanceOf(WorkerNodeLeaseLostException.class)
                .hasMessage("worker node lease has been lost");
    }

    private static final class MutableClock extends Clock {

        private long millis;

        private MutableClock(long millis) {
            this.millis = millis;
        }

        void setMillis(long millis) {
            this.millis = millis;
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("UTC");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return Instant.ofEpochMilli(millis);
        }

        @Override
        public long millis() {
            return millis;
        }
    }
}
