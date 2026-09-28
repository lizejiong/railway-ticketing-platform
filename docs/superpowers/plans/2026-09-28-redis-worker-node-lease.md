# Redis 工作节点租约实施计划

> **给 agentic workers：** 必须使用 `superpowers:executing-plans` 按任务逐项执行本计划。步骤使用 checkbox（`- [ ]`）语法跟踪状态。
**目标：** 为 `railway-id-generator` 增加基于 Redis 原子租约的工作节点分配策略，并在失去租约时停止 Snowflake 发号。

**架构：** `RedisWorkerNodeAssigner` 通过 Redisson `RScript` 一次性在 32 个候选工作节点键中续租已有编号或抢占空闲编号；所有键使用同一个 Hash Tag，以兼容 Redis Cluster。策略启动后台续租任务，续租失败后由 `verifyLease()` 拒绝后续发号；`SnowflakeIdGenerator` 在每次 `nextId()` 前执行该校验。实例租约 owner 在可读实例标识后追加随机 UUID，避免同名实例短暂重叠时共享编号。

**技术栈：** Java 17、Redisson 3.21.3、Redis Lua、JUnit 5、Mockito、AssertJ。

---

### 任务 1：接入 Redisson 并演进节点 SPI

**文件：**
- 修改：`backend/components/idgenerator/pom.xml`
- 修改：`backend/dependencies/pom.xml`
- 修改：`backend/components/idgenerator/src/main/java/com/lzj/railway/framework/idgenerator/snowflake/WorkerNodeAssigner.java`
- 修改：`backend/components/idgenerator/src/main/java/com/lzj/railway/framework/idgenerator/snowflake/SnowflakeIdGenerator.java`
- 修改：`backend/components/idgenerator/src/main/java/com/lzj/railway/framework/idgenerator/snowflake/SnowflakeIdUtil.java`
- 测试：`backend/components/idgenerator/src/test/java/com/lzj/railway/framework/idgenerator/snowflake/SnowflakeIdGeneratorTest.java`

- [x] **步骤 1：添加 Redisson 依赖**

```xml
<dependency>
    <groupId>org.redisson</groupId>
    <artifactId>redisson</artifactId>
</dependency>
```

版本由现有 `backend/dependencies/pom.xml` 的 `redisson.version` 管理。
同时在该 BOM 的 `<dependencyManagement>` 中声明 `org.redisson:redisson` 并引用该版本属性。

- [x] **步骤 2：为节点策略加入租约校验和生命周期默认方法**

```java
@FunctionalInterface
public interface WorkerNodeAssigner extends AutoCloseable {
    WorkerNode assign();

    default void verifyLease() {
    }

    @Override
    default void close() {
    }
}
```

固定和主机名策略不需要修改行为，自动继承两个无操作默认方法。

- [x] **步骤 3：让生成器在每次发号前校验节点租约**

```java
private final Runnable leaseVerifier;

public SnowflakeIdGenerator(WorkerNodeAssigner assigner) {
    this(assigner.assign(), DEFAULT_EPOCH, Clock.systemUTC(), assigner::verifyLease);
}

public synchronized long nextId() {
    leaseVerifier.run();
    // 保留现有 Snowflake 发号逻辑
}
```

`SnowflakeIdUtil.create(assigner)` 改为 `return new SnowflakeIdGenerator(assigner);`。已有 `WorkerNode` 构造路径传入空操作校验器，保持兼容。

- [x] **步骤 4：测试失租时拒绝发号**

```java
WorkerNodeAssigner assigner = new WorkerNodeAssigner() {
    public WorkerNode assign() { return new WorkerNode(0, 0); }
    public void verifyLease() { throw new WorkerNodeLeaseLostException(); }
};

assertThatThrownBy(() -> new SnowflakeIdGenerator(assigner).nextId())
        .isInstanceOf(WorkerNodeLeaseLostException.class);
```

运行：`cd backend && .\mvnw.cmd -pl components/idgenerator -am -Dsurefire.failIfNoSpecifiedTests=false -Dtest=SnowflakeIdGeneratorTest test`

预期：旧用法与新增失租保护测试均通过。

### 任务 2：实现 Redis 原子租约策略

**文件：**
- 新建：`backend/components/idgenerator/src/main/java/com/lzj/railway/framework/idgenerator/snowflake/RedisWorkerNodeAssigner.java`
- 新建：`backend/components/idgenerator/src/main/java/com/lzj/railway/framework/idgenerator/snowflake/WorkerNodeUnavailableException.java`
- 新建：`backend/components/idgenerator/src/main/java/com/lzj/railway/framework/idgenerator/snowflake/WorkerNodeLeaseLostException.java`
- 测试：`backend/components/idgenerator/src/test/java/com/lzj/railway/framework/idgenerator/snowflake/RedisWorkerNodeAssignerTest.java`

- [x] **步骤 1：编写 Redis 策略单元测试**

使用 Mockito 模拟 `RedissonClient` 与 `RScript`，覆盖：

```java
when(script.eval(any(), anyString(), any(), anyList(), any(), any())).thenReturn(7L);
RedisWorkerNodeAssigner assigner = new RedisWorkerNodeAssigner(client, 2, "ticket-service-0");
assertThat(assigner.assign()).isEqualTo(new WorkerNode(2, 7));

when(script.eval(any(), anyString(), any(), anyList(), any(), any())).thenReturn(-1L);
assertThatThrownBy(assigner::assign)
        .isInstanceOf(WorkerNodeUnavailableException.class);
```

令续租脚本返回 `0L` 后调用包可见的 `renewLease()`，再断言 `verifyLease()` 抛出 `WorkerNodeLeaseLostException`。同时校验预约脚本传入 32 个带相同 `{namespace:datacenter}` Hash Tag 的键。

- [x] **步骤 2：实现原子预约、续租和释放脚本**

预约脚本接收 32 个候选 key：先寻找 owner 相同的 key 并续期；未找到时以 `SET key owner NX PX ttl` 依次抢占；无可用 key 时返回 `-1`。

```lua
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
```

续租和释放都必须先检查 key 的 owner 是否等于当前 owner。调用 Redisson 时使用：

```java
redissonClient.getScript(StringCodec.INSTANCE).eval(
        RScript.Mode.READ_WRITE,
        script,
        RScript.ReturnType.INTEGER,
        keys,
        ownerId,
        String.valueOf(leaseMillis)
);
```

`RedisWorkerNodeAssigner` 默认租约 30 秒、每 10 秒续租。`assign()` 完成预约后再启动续租；任何续租异常或非 1 返回值都会标记为失租。`close()` 取消续租、以 compare-and-delete 脚本尽力释放编号，并关闭自身创建的续租线程。

- [x] **步骤 3：运行 Redis 策略单元测试**

运行：`cd backend && .\mvnw.cmd -pl components/idgenerator -am -Dsurefire.failIfNoSpecifiedTests=false -Dtest=RedisWorkerNodeAssignerTest test`

预期：不需要真实 Redis；预约结果、满额拒绝、失租保护和 Cluster key 规划测试均通过。

### 任务 3：更新文档与完整验证

**文件：**
- 修改：`README.md`
- 修改：`docs/architecture/spring-boot-starter-structure.md`
- 修改：`docs/superpowers/plans/2026-09-28-redis-worker-node-lease.md`

- [x] **步骤 1：补充 Redis 策略说明和使用边界**

文档说明 `RedisWorkerNodeAssigner` 需要业务服务提供 `RedissonClient` 与实例标识；实例标识会追加随机 owner 后缀；ID 域中最多 32 个工作编号；Bean 必须声明 `destroyMethod = "close"`。明确 Redis 故障或失租后将停止发号，应用需要依赖健康检查或重启恢复。

- [x] **步骤 2：运行完整验证和静态检查**

运行：`cd backend && .\mvnw.cmd clean test`、`git diff --check`、`git status --short`。

预期：所有模块测试通过，不存在空白错误，改动仅包含 idgenerator Redis 租约实现、文档和本实施计划。
