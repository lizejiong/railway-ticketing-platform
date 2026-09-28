package com.lzj.railway.framework.idgenerator.snowflake;

/**
 * Snowflake ID 解析后的组成部分。
 */
public record SnowflakeId(long timestamp, long nodeId, long sequence) {
}
