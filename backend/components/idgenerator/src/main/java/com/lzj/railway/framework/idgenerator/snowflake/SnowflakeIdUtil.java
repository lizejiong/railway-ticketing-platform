package com.lzj.railway.framework.idgenerator.snowflake;

import java.util.Objects;

/**
 * Snowflake 生成器创建和 ID 解析工具。
 * <p>
 * 工具类不维护静态生成器，节点配置始终由调用方显式传入。
 */
public final class SnowflakeIdUtil {

    private SnowflakeIdUtil() {
    }

    /**
     * 使用节点分配策略创建默认 Snowflake 生成器。
     *
     * @param workerNodeAssigner 节点分配策略
     * @return Snowflake 生成器
     */
    public static SnowflakeIdGenerator create(WorkerNodeAssigner workerNodeAssigner) {
        return new SnowflakeIdGenerator(
                Objects.requireNonNull(workerNodeAssigner, "worker node assigner must not be null")
        );
    }

    /**
     * 按默认纪元解析 Snowflake ID。
     *
     * @param id Snowflake ID
     * @return 解析结果
     */
    public static SnowflakeId parse(long id) {
        return parse(id, SnowflakeIdGenerator.DEFAULT_EPOCH);
    }

    /**
     * 按指定纪元解析 Snowflake ID。
     *
     * @param id Snowflake ID
     * @param epoch 生成该 ID 使用的纪元时间
     * @return 解析结果
     */
    public static SnowflakeId parse(long id, long epoch) {
        if (id < 0) {
            throw new IllegalArgumentException("id must not be negative");
        }
        long sequence = id & SnowflakeIdGenerator.SEQUENCE_MASK;
        long nodeId = (id >> SnowflakeIdGenerator.NODE_ID_SHIFT) & SnowflakeIdGenerator.MAX_NODE_ID;
        long timestamp = (id >> SnowflakeIdGenerator.TIMESTAMP_SHIFT) + epoch;
        return new SnowflakeId(timestamp, nodeId, sequence);
    }
}
