package com.lzj.railway.ticket.common.constant;

/**
 * 票务查询读模型使用的 Redis Key 约定。
 */
public final class TicketCacheKey {

    private static final String PREFIX = "railway:ticket:";

    private TicketCacheKey() {
    }

    /**
     * 生成按地区缓存可售区间的 Hash Key。
     *
     * @param fromRegion 出发地区
     * @param toRegion 到达地区
     * @return Redis Hash Key
     */
    public static String regionRoute(String fromRegion, String toRegion) {
        return PREFIX + "region_route:{" + fromRegion + '_' + toRegion + '}';
    }

    /**
     * 生成列车基础信息缓存 Key。
     *
     * @param trainId 列车主键
     * @return Redis String Key
     */
    public static String train(Long trainId) {
        return PREFIX + "train:{" + trainId + '}';
    }

    /**
     * 生成区间票价缓存 Key。
     *
     * @param trainId 列车主键
     * @param departure 出发站名称
     * @param arrival 到达站名称
     * @return Redis String Key
     */
    public static String price(Long trainId, String departure, String arrival) {
        return PREFIX + "price:{" + trainId + "}:" + departure + '_' + arrival;
    }

    /**
     * 生成区间余票缓存 Hash Key。
     *
     * @param trainId 列车主键
     * @param departure 出发站名称
     * @param arrival 到达站名称
     * @return Redis Hash Key
     */
    public static String remaining(Long trainId, String departure, String arrival) {
        return PREFIX + "remaining:{" + trainId + "}:" + departure + '_' + arrival;
    }

    /**
     * 为任意缓存 Key 生成对应的 Redisson 互斥锁 Key。
     *
     * @param cacheKey 被保护的缓存 Key
     * @return Redisson 锁 Key
     */
    public static String lock(String cacheKey) {
        return PREFIX + "lock:" + cacheKey.substring(PREFIX.length());
    }
}
