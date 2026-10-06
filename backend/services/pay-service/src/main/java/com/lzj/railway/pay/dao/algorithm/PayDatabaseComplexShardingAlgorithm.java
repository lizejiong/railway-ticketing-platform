package com.lzj.railway.pay.dao.algorithm;

import org.apache.shardingsphere.sharding.api.sharding.complex.ComplexKeysShardingAlgorithm;
import org.apache.shardingsphere.sharding.api.sharding.complex.ComplexKeysShardingValue;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * 支付库路由算法。
 *
 * <p>订单号和支付流水号均在末六位保留相同的路由片段，因此无论是创建支付单、
 * 支付宝异步回调，还是按流水号查询，都能精确落到同一个库。</p>
 */
public final class PayDatabaseComplexShardingAlgorithm implements ComplexKeysShardingAlgorithm<Comparable<?>> {
    private int shardingCount;
    private int tableShardingCount;

    @Override
    public void init(Properties props) {
        shardingCount = Integer.parseInt(props.getProperty("sharding-count"));
        tableShardingCount = Integer.parseInt(props.getProperty("table-sharding-count"));
    }

    @Override
    public Collection<String> doSharding(Collection<String> targets, ComplexKeysShardingValue<Comparable<?>> value) {
        String key = resolveRoutingKey(value.getColumnNameAndShardingValuesMap());
        if (key == null) {
            return targets;
        }
        int databaseIndex = Math.floorMod(key.hashCode(), shardingCount) / tableShardingCount;
        return new LinkedHashSet<>(List.of("ds_" + databaseIndex));
    }

    /** 优先使用订单号，支付平台回调仅携带支付流水号时再使用后者。 */
    private String resolveRoutingKey(Map<String, Collection<Comparable<?>>> values) {
        String orderSn = firstValue(values.get("order_sn"));
        return orderSn != null ? tail(orderSn) : tail(firstValue(values.get("pay_sn")));
    }

    private String firstValue(Collection<Comparable<?>> values) {
        return values == null || values.isEmpty() ? null : String.valueOf(values.iterator().next());
    }

    private String tail(String value) {
        return value == null ? null : value.substring(Math.max(0, value.length() - 6));
    }

    @Override
    public String getType() {
        return "CLASS_BASED";
    }
}
