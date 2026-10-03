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
     * 生成列车余票令牌桶的 Hash Key。
     *
     * <p>一趟列车的所有区间余票放在同一个 Hash 中，保证 Lua 脚本可以对多个途经区间原子扣减。</p>
     *
     * @param trainId 列车主键
     * @return Redis Hash Key
     */
    public static String remaining(Long trainId) {
        return PREFIX + "remaining:{" + trainId + '}';
    }

    /**
     * 生成余票令牌桶中的区间席别 Field。
     *
     * @param departure 区间出发站名称
     * @param arrival 区间到达站名称
     * @param seatType 席别编码
     * @return Hash Field
     */
    public static String remainingField(String departure, String arrival, Integer seatType) {
        return departure + '_' + arrival + '_' + seatType;
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
