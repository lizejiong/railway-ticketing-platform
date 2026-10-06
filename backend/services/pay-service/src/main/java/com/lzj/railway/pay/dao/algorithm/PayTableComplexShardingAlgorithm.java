package com.lzj.railway.pay.dao.algorithm;

import org.apache.shardingsphere.sharding.api.sharding.complex.ComplexKeysShardingAlgorithm;
import org.apache.shardingsphere.sharding.api.sharding.complex.ComplexKeysShardingValue;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/** 将支付记录路由到目标库内的具体分表。 */
public final class PayTableComplexShardingAlgorithm implements ComplexKeysShardingAlgorithm<Comparable<?>> {
    private int shardingCount;

    @Override
    public void init(Properties props) {
        shardingCount = Integer.parseInt(props.getProperty("sharding-count"));
    }

    @Override
    public Collection<String> doSharding(Collection<String> targets, ComplexKeysShardingValue<Comparable<?>> value) {
        String key = resolveRoutingKey(value.getColumnNameAndShardingValuesMap());
        if (key == null) {
            return targets;
        }
        int tableIndex = Math.floorMod(key.hashCode(), shardingCount);
        return new LinkedHashSet<>(List.of(value.getLogicTableName() + "_" + tableIndex));
    }

    /** 与数据库路由保持完全相同的优先级，避免跨库跨表查询。 */
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
