package com.lzj.railway.framework.starter.persistence.sharding;

import org.apache.shardingsphere.sharding.api.sharding.standard.PreciseShardingValue;
import org.apache.shardingsphere.infra.datanode.DataNodeInfo;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CustomDbHashModShardingAlgorithmTest {

    @Test
    void routesPreciseValueToExpectedDatabase() {
        CustomDbHashModShardingAlgorithm algorithm = new CustomDbHashModShardingAlgorithm();
        Properties properties = new Properties();
        properties.setProperty("sharding-count", "32");
        properties.setProperty("table-sharding-count", "16");
        algorithm.init(properties);

        String username = "railway_user";
        String target = algorithm.doSharding(List.of("ds_0", "ds_1"),
                new PreciseShardingValue<>("t_user", "username", new DataNodeInfo("ds_", 1, '0'), username));

        long hash = Math.abs((long) username.hashCode());
        assertEquals("ds_" + (hash % 32 / 16), target);
    }
}
