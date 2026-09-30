package com.lzj.railway.framework.starter.persistence.sharding;

import org.apache.shardingsphere.infra.util.exception.ShardingSpherePreconditions;
import org.apache.shardingsphere.sharding.algorithm.sharding.ShardingAutoTableAlgorithmUtil;
import org.apache.shardingsphere.sharding.api.sharding.standard.PreciseShardingValue;
import org.apache.shardingsphere.sharding.api.sharding.standard.RangeShardingValue;
import org.apache.shardingsphere.sharding.api.sharding.standard.StandardShardingAlgorithm;
import org.apache.shardingsphere.sharding.exception.algorithm.sharding.ShardingAlgorithmInitializationException;

import java.util.Collection;
import java.util.Properties;

/** 将逻辑表后缀范围路由到所属的物理数据源。 */
public final class CustomDbHashModShardingAlgorithm implements StandardShardingAlgorithm<Comparable<?>> {

    private static final String SHARDING_COUNT_KEY = "sharding-count";
    private static final String TABLE_SHARDING_COUNT_KEY = "table-sharding-count";

    private int shardingCount;
    private int tableShardingCount;

    /**
     * 读取逻辑分片总数和每个数据库负责的表后缀数量。
     */
    @Override
    public void init(Properties props) {
        ShardingSpherePreconditions.checkState(props.containsKey(SHARDING_COUNT_KEY),
                () -> new ShardingAlgorithmInitializationException(getType(), "Sharding count cannot be null."));
        ShardingSpherePreconditions.checkState(props.containsKey(TABLE_SHARDING_COUNT_KEY),
                () -> new ShardingAlgorithmInitializationException(getType(), "Table sharding count cannot be null."));
        shardingCount = Integer.parseInt(props.getProperty(SHARDING_COUNT_KEY));
        tableShardingCount = Integer.parseInt(props.getProperty(TABLE_SHARDING_COUNT_KEY));
    }

    /**
     * 对等值分片键使用与表后缀相同的哈希分区规则，路由到唯一物理数据源。
     */
    @Override
    public String doSharding(Collection<String> availableTargetNames, PreciseShardingValue<Comparable<?>> shardingValue) {
        long hash = Math.abs((long) shardingValue.getValue().hashCode());
        // 32 个逻辑后缀每 16 个归属一个库：0-15 -> ds_0，16-31 -> ds_1。
        String suffix = String.valueOf(hash % shardingCount / tableShardingCount);
        return ShardingAutoTableAlgorithmUtil.findMatchedTargetName(availableTargetNames, suffix,
                shardingValue.getDataNodeInfo()).orElse(null);
    }

    /**
     * 范围条件无法映射到唯一分片，因此返回全部候选数据源。
     */
    @Override
    public Collection<String> doSharding(Collection<String> availableTargetNames, RangeShardingValue<Comparable<?>> shardingValue) {
        // 分片键范围条件无法确定唯一目标库，只能路由到全部可用数据源。
        return availableTargetNames;
    }

    @Override
    public String getType() {
        return "CLASS_BASED";
    }
}
