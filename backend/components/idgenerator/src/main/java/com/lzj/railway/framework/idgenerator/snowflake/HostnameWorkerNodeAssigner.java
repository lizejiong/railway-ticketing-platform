package com.lzj.railway.framework.idgenerator.snowflake;

import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 从 StatefulSet 风格主机名末尾的数字序号提取节点编号。
 * <p>
 * 例如 {@code ticket-service-12} 会分配节点编号 {@code 12}。
 */
public final class HostnameWorkerNodeAssigner implements WorkerNodeAssigner {

    private static final Pattern ORDINAL_PATTERN = Pattern.compile(".*-(\\d+)$");

    private final WorkerNode workerNode;

    public HostnameWorkerNodeAssigner(String hostname) {
        Objects.requireNonNull(hostname, "hostname must not be null");
        Matcher matcher = ORDINAL_PATTERN.matcher(hostname);
        if (!matcher.matches()) {
            throw new IllegalArgumentException("hostname must end with a numeric ordinal");
        }
        long nodeId = Long.parseLong(matcher.group(1));
        if (nodeId < 0 || nodeId > SnowflakeIdGenerator.MAX_NODE_ID) {
            throw new IllegalArgumentException("node id must be between 0 and 1023");
        }
        this.workerNode = new WorkerNode(nodeId);
    }

    @Override
    public WorkerNode assign() {
        return workerNode;
    }
}
