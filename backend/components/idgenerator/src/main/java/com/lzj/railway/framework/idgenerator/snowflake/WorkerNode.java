package com.lzj.railway.framework.idgenerator.snowflake;

/**
 * Snowflake 节点标识。
 * <p>
 * nodeId 使用 10 位，取值范围为 0 到 1023。
 */
public record WorkerNode(long nodeId) {
}
