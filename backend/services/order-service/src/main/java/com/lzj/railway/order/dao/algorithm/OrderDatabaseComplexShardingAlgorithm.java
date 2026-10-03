package com.lzj.railway.order.dao.algorithm;

import org.apache.shardingsphere.sharding.api.sharding.complex.ComplexKeysShardingAlgorithm;
import org.apache.shardingsphere.sharding.api.sharding.complex.ComplexKeysShardingValue;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Properties;

/** 根据 user_id 优先、order_sn 兜底路由订单所在库。 */
public final class OrderDatabaseComplexShardingAlgorithm implements ComplexKeysShardingAlgorithm<Comparable<?>> {
    private int shardingCount;
    private int tableShardingCount;

    @Override
    public void init(Properties props) {
        shardingCount = Integer.parseInt(props.getProperty("sharding-count"));
        tableShardingCount = Integer.parseInt(props.getProperty("table-sharding-count"));
    }

    @Override
    public Collection<String> doSharding(Collection<String> targets, ComplexKeysShardingValue<Comparable<?>> value) {
        String key = resolveKey(value.getColumnNameAndShardingValuesMap());
        if (key == null) {
            return targets;
        }
        int databaseIndex = Math.floorMod(key.hashCode(), shardingCount) / tableShardingCount;
        return new LinkedHashSet<>(java.util.List.of("ds_" + databaseIndex));
    }

    /** 订单号尾部保留用户路由片段，因此按订单号查询可命中同一分片。 */
    private String resolveKey(Map<String, Collection<Comparable<?>>> values) {
        Collection<Comparable<?>> userIds = values.get("user_id");
        if (userIds != null && !userIds.isEmpty()) {
            return tail(String.valueOf(userIds.iterator().next()));
        }
        Collection<Comparable<?>> orderSns = values.get("order_sn");
        if (orderSns != null && !orderSns.isEmpty()) {
            return tail(String.valueOf(orderSns.iterator().next()));
        }
        return null;
    }

    private String tail(String value) {
        return value.substring(Math.max(0, value.length() - 6));
    }

    @Override
    public String getType() {
        return "CLASS_BASED";
    }
}
