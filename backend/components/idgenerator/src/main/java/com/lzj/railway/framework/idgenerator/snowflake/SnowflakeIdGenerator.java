package com.lzj.railway.framework.idgenerator.snowflake;

import java.time.Clock;
import java.util.Objects;

/**
 * 经典 Snowflake 算法实现。
 * <p>
 * ID 由 41 位毫秒时间差、10 位节点编号和 12 位毫秒内序列组成。
 * 节点编号必须由部署环境或外部协调服务保证唯一。
 */
public final class SnowflakeIdGenerator implements IdGenerator {

    /** 2024-01-01T00:00:00Z，修改该值会改变 ID 解析结果。 */
    public static final long DEFAULT_EPOCH = 1_704_067_200_000L;

    static final long MAX_NODE_ID = 1_023L;
    static final long SEQUENCE_MASK = 4_095L;
    static final int NODE_ID_SHIFT = 12;
    static final int TIMESTAMP_SHIFT = 22;

    private final long nodeId;
    private final long epoch;
    private final Clock clock;
    private final Runnable leaseVerifier;

    private long sequence;
    private long lastTimestamp = -1L;

    public SnowflakeIdGenerator(WorkerNode workerNode) {
        this(workerNode, DEFAULT_EPOCH, Clock.systemUTC(), () -> {
        });
    }

    /**
     * 使用节点分配策略创建生成器，并在每次发号前校验策略租约。
     *
     * @param workerNodeAssigner 节点分配策略
     */
    public SnowflakeIdGenerator(WorkerNodeAssigner workerNodeAssigner) {
        this(
                Objects.requireNonNull(workerNodeAssigner, "worker node assigner must not be null").assign(),
                DEFAULT_EPOCH,
                Clock.systemUTC(),
                workerNodeAssigner::verifyLease
        );
    }

    /**
     * 创建可测试或使用自定义纪元时间的生成器。
     *
     * @param workerNode 当前实例的唯一节点标识
     * @param epoch 纪元毫秒时间戳
     * @param clock 时间来源
     */
    public SnowflakeIdGenerator(WorkerNode workerNode, long epoch, Clock clock) {
        this(workerNode, epoch, clock, () -> {
        });
    }

    SnowflakeIdGenerator(WorkerNode workerNode, long epoch, Clock clock, Runnable leaseVerifier) {
        WorkerNode resolvedWorkerNode = Objects.requireNonNull(workerNode, "worker node must not be null");
        if (resolvedWorkerNode.nodeId() < 0 || resolvedWorkerNode.nodeId() > MAX_NODE_ID) {
            throw new IllegalArgumentException("node id must be between 0 and 1023");
        }
        this.nodeId = resolvedWorkerNode.nodeId();
        this.epoch = epoch;
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        this.leaseVerifier = Objects.requireNonNull(leaseVerifier, "lease verifier must not be null");
    }

    /**
     * 生成 ID。该方法串行化同一实例内的毫秒序列，保证并发调用不会重复。
     *
     * @return 正数 Snowflake ID
     */
    @Override
    public synchronized long nextId() {
        leaseVerifier.run();
        long timestamp = currentTimestamp();
        if (timestamp < lastTimestamp) {
            throw new ClockMovedBackwardsException(lastTimestamp - timestamp);
        }

        if (timestamp == lastTimestamp) {
            sequence = (sequence + 1) & SEQUENCE_MASK;
            if (sequence == 0) {
                timestamp = waitNextMillis(lastTimestamp);
            }
        } else {
            sequence = 0L;
        }
        lastTimestamp = timestamp;

        return ((timestamp - epoch) << TIMESTAMP_SHIFT)
                | (nodeId << NODE_ID_SHIFT)
                | sequence;
    }

    private long currentTimestamp() {
        long timestamp = clock.millis();
        if (timestamp < epoch) {
            throw new IllegalStateException("current timestamp must not be before epoch");
        }
        return timestamp;
    }

    private long waitNextMillis(long previousTimestamp) {
        long timestamp;
        do {
            Thread.onSpinWait();
            timestamp = currentTimestamp();
        } while (timestamp <= previousTimestamp);
        return timestamp;
    }
}
