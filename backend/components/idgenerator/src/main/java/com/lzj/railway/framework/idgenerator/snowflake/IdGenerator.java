package com.lzj.railway.framework.idgenerator.snowflake;

/**
 * 分布式唯一 ID 生成器。
 */
@FunctionalInterface
public interface IdGenerator {

    /**
     * 生成下一个唯一 ID。
     *
     * @return 正数 ID
     */
    long nextId();
}
