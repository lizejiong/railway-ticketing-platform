package com.lzj.railway.framework.idgenerator.snowflake;

/**
 * Snowflake 工作节点编号分配策略。
 * <p>
 * 生产环境必须保证分配结果在同一 ID 域内唯一。
 */
@FunctionalInterface
public interface WorkerNodeAssigner extends AutoCloseable {

    /**
     * 分配当前实例使用的节点标识。
     *
     * @return 当前实例的节点标识
     */
    WorkerNode assign();

    /**
     * 校验当前节点编号租约是否仍然有效。
     * <p>
     * 固定编号和主机名策略没有外部租约，默认不执行任何操作。
     */
    default void verifyLease() {
    }

    /**
     * 释放策略持有的外部资源。
     */
    @Override
    default void close() {
    }
}
