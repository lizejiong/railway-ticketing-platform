package com.lzj.railway.framework.idgenerator.snowflake;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.LongStream;

/**
 * 基于 Redis 租约自动分配 Snowflake 工作节点编号。
 * <p>
 * Redis 仅分配一个 10 位 nodeId（0 到 1023）。
 * 预占、续租和释放均通过 Lua 脚本原子执行。失去租约后，{@link #verifyLease()} 会阻止后续发号。
 */
public final class RedisWorkerNodeAssigner implements WorkerNodeAssigner {

    private static final String DEFAULT_KEY_NAMESPACE = "railway:id-generator";
    private static final Duration DEFAULT_LEASE_DURATION = Duration.ofSeconds(30);
    private static final DefaultRedisScript<Long> RESERVE_SCRIPT = createScript("""
            for index, key in ipairs(KEYS) do
                if redis.call('GET', key) == ARGV[1] then
                    redis.call('PEXPIRE', key, ARGV[2])
                    return index - 1
                end
            end
            for index, key in ipairs(KEYS) do
                if redis.call('SET', key, ARGV[1], 'NX', 'PX', ARGV[2]) then
                    return index - 1
                end
            end
            return -1
            """);
    private static final DefaultRedisScript<Long> RENEW_SCRIPT = createScript("""
            if redis.call('GET', KEYS[1]) == ARGV[1] then
                return redis.call('PEXPIRE', KEYS[1], ARGV[2])
            end
            return 0
            """);
    private static final DefaultRedisScript<Long> RELEASE_SCRIPT = createScript("""
            if redis.call('GET', KEYS[1]) == ARGV[1] then
                return redis.call('DEL', KEYS[1])
            end
            return 0
            """);

    private final StringRedisTemplate redisTemplate;
    private final String ownerId;
    private final long leaseMillis;
    private final long renewalIntervalMillis;
    private final List<String> nodeKeys;
    private final ScheduledExecutorService renewalExecutor;

    private volatile WorkerNode workerNode;
    private volatile long nodeId = -1L;
    private volatile boolean leaseValid;
    private volatile boolean closed;
    private ScheduledFuture<?> renewalFuture;

    /**
     * 创建默认 30 秒租约的 Redis 节点分配器。
     *
     * @param redisTemplate 业务服务提供的 Redis 模板
     */
    public RedisWorkerNodeAssigner(StringRedisTemplate redisTemplate) {
        this(redisTemplate, "application");
    }

    /**
     * 创建默认 30 秒租约的 Redis 节点分配器。
     *
     * @param redisTemplate 业务服务提供的 Redis 模板
     * @param instanceId 当前实例的可读标识，例如 Pod 名称
     */
    public RedisWorkerNodeAssigner(StringRedisTemplate redisTemplate, String instanceId) {
        this(redisTemplate, instanceId, DEFAULT_KEY_NAMESPACE, DEFAULT_LEASE_DURATION);
    }

    /**
     * 创建自定义键命名空间和租约时长的 Redis 节点分配器。
     *
     * @param redisTemplate 业务服务提供的 Redis 模板
     * @param instanceId 当前实例的可读标识
     * @param keyNamespace Redis 键命名空间
     * @param leaseDuration 节点编号租约时长，最短三秒
     */
    public RedisWorkerNodeAssigner(
            StringRedisTemplate redisTemplate,
            String instanceId,
            String keyNamespace,
            Duration leaseDuration) {
        this.redisTemplate = Objects.requireNonNull(redisTemplate, "redis template must not be null");
        if (instanceId == null || instanceId.isBlank()) {
            throw new IllegalArgumentException("instance id must not be blank");
        }
        if (keyNamespace == null || keyNamespace.isBlank() || keyNamespace.contains("{") || keyNamespace.contains("}")) {
            throw new IllegalArgumentException("key namespace must not be blank or contain hash tag characters");
        }
        Objects.requireNonNull(leaseDuration, "lease duration must not be null");
        this.leaseMillis = leaseDuration.toMillis();
        if (leaseMillis < 3_000L) {
            throw new IllegalArgumentException("lease duration must be at least 3 seconds");
        }

        this.ownerId = instanceId + ":" + UUID.randomUUID();
        this.renewalIntervalMillis = leaseMillis / 3L;
        this.nodeKeys = buildNodeKeys(keyNamespace);
        this.renewalExecutor = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "railway-id-lease-renewal");
            thread.setDaemon(true);
            return thread;
        });
    }

    /**
     * 原子预占一个完整节点号，并启动后台续租。
     *
     * @return 当前实例的 Snowflake 节点标识
     */
    @Override
    public synchronized WorkerNode assign() {
        ensureOpen();
        if (workerNode != null) {
            return workerNode;
        }

        Long reservedNodeId = redisTemplate.execute(
                RESERVE_SCRIPT,
                nodeKeys,
                ownerId,
                String.valueOf(leaseMillis)
        );
        if (reservedNodeId == null || reservedNodeId < 0 || reservedNodeId > SnowflakeIdGenerator.MAX_NODE_ID) {
            throw new WorkerNodeUnavailableException();
        }

        nodeId = reservedNodeId;
        workerNode = new WorkerNode(nodeId);
        leaseValid = true;
        renewalFuture = renewalExecutor.scheduleAtFixedRate(
                this::renewLease,
                renewalIntervalMillis,
                renewalIntervalMillis,
                TimeUnit.MILLISECONDS
        );
        return workerNode;
    }

    /**
     * 检查续租状态，失租后拒绝继续发号。
     */
    @Override
    public void verifyLease() {
        if (!leaseValid || closed) {
            throw new WorkerNodeLeaseLostException();
        }
    }

    /**
     * 执行一次 compare-and-expire 续租。包可见性仅用于单元测试。
     */
    void renewLease() {
        long currentNodeId = nodeId;
        if (currentNodeId < 0 || !leaseValid || closed) {
            return;
        }
        try {
            Long renewed = redisTemplate.execute(
                    RENEW_SCRIPT,
                    List.of(nodeKey(currentNodeId)),
                    ownerId,
                    String.valueOf(leaseMillis)
            );
            if (renewed == null || renewed != 1L) {
                leaseValid = false;
            }
        } catch (RuntimeException ignored) {
            leaseValid = false;
        }
    }

    /**
     * 停止续租并尽力释放当前实例持有的 Redis 节点号。
     */
    @Override
    public synchronized void close() {
        if (closed) {
            return;
        }
        closed = true;
        leaseValid = false;
        if (renewalFuture != null) {
            renewalFuture.cancel(false);
        }
        if (nodeId >= 0) {
            try {
                redisTemplate.execute(RELEASE_SCRIPT, List.of(nodeKey(nodeId)), ownerId);
            } catch (RuntimeException ignored) {
                // Redis 不可用时让租约自然过期，不阻塞应用关闭。
            }
        }
        renewalExecutor.shutdownNow();
    }

    private static DefaultRedisScript<Long> createScript(String scriptText) {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setScriptText(scriptText);
        script.setResultType(Long.class);
        return script;
    }

    private List<String> buildNodeKeys(String keyNamespace) {
        return LongStream.rangeClosed(0L, SnowflakeIdGenerator.MAX_NODE_ID)
                .mapToObj(currentNodeId -> keyNamespace + ":{" + keyNamespace + "}:node:" + currentNodeId)
                .toList();
    }

    private String nodeKey(long currentNodeId) {
        return nodeKeys.get(Math.toIntExact(currentNodeId));
    }

    private void ensureOpen() {
        if (closed) {
            throw new IllegalStateException("worker node assigner has been closed");
        }
    }
}
