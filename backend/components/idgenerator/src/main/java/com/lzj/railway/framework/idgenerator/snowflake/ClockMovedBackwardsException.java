package com.lzj.railway.framework.idgenerator.snowflake;

/**
 * 系统时间小于上次成功发号时间时抛出。
 */
public class ClockMovedBackwardsException extends IllegalStateException {

    private final long offsetMillis;

    public ClockMovedBackwardsException(long offsetMillis) {
        super("clock moved backwards by " + offsetMillis + "ms");
        this.offsetMillis = offsetMillis;
    }

    /**
     * 获取回拨的毫秒数。
     *
     * @return 回拨毫秒数
     */
    public long getOffsetMillis() {
        return offsetMillis;
    }
}
