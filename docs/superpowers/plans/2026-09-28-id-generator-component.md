# 分布式 ID 组件实施计划

> **给 agentic workers：** 必须使用 `superpowers:executing-plans` 按任务逐项执行本计划。步骤使用 checkbox（`- [ ]`）语法跟踪状态。
**目标：** 新增 `railway-id-generator` 组件，提供线程安全的 Snowflake `long` ID 生成器、节点编号策略和 ID 解析工具。

**架构：** 组件是普通 Java JAR，不自动配置、不持有全局 Spring 上下文。默认使用经典 41 位毫秒时间、5 位数据中心、5 位工作节点、12 位序列布局；工作节点通过 `WorkerNodeAssigner` SPI 提供，内置固定节点和 StatefulSet 主机名序号策略，Redis、数据库、ZooKeeper 等租约分配器以后以独立适配器实现。

**技术栈：** Java 17、`java.time.Clock`、JUnit 5、AssertJ。

---

### 任务 1：接入 Maven 模块

**文件：**
- 新建：`backend/components/idgenerator/pom.xml`
- 修改：`backend/components/pom.xml`
- 修改：`backend/dependencies/pom.xml`

- [x] **步骤 1：创建普通 JAR 模块 POM**

```xml
<artifactId>railway-id-generator</artifactId>
<name>Railway ID Generator</name>
<description>Distributed unique ID generators for railway services</description>
<dependencies>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-test</artifactId>
        <scope>test</scope>
    </dependency>
</dependencies>
```

- [x] **步骤 2：加入组件聚合和 BOM**

在 `backend/components/pom.xml` 的 `<modules>` 增加：

```xml
<module>idgenerator</module>
```

在 `backend/dependencies/pom.xml` 的 `<dependencyManagement>` 增加：

```xml
<dependency>
    <groupId>com.lzj.railway</groupId>
    <artifactId>railway-id-generator</artifactId>
    <version>${railway-components.version}</version>
</dependency>
```

- [x] **步骤 3：验证 Maven reactor**

运行：`cd backend && .\mvnw.cmd -pl components/idgenerator -am validate`

预期：新模块与父 POM、组件聚合 POM 一同构建成功。

### 任务 2：实现节点编号模型和策略

**文件：**
- 新建：`backend/components/idgenerator/src/main/java/com/lzj/railway/framework/idgenerator/snowflake/WorkerNode.java`
- 新建：`backend/components/idgenerator/src/main/java/com/lzj/railway/framework/idgenerator/snowflake/WorkerNodeAssigner.java`
- 新建：`backend/components/idgenerator/src/main/java/com/lzj/railway/framework/idgenerator/snowflake/FixedWorkerNodeAssigner.java`
- 新建：`backend/components/idgenerator/src/main/java/com/lzj/railway/framework/idgenerator/snowflake/HostnameWorkerNodeAssigner.java`
- 测试：`backend/components/idgenerator/src/test/java/com/lzj/railway/framework/idgenerator/snowflake/WorkerNodeAssignerTest.java`

- [x] **步骤 1：编写节点策略测试**

```java
assertThat(new FixedWorkerNodeAssigner(2, 7).assign()).isEqualTo(new WorkerNode(2, 7));
assertThat(new HostnameWorkerNodeAssigner("ticket-service-12", 3).assign())
        .isEqualTo(new WorkerNode(3, 12));
assertThatThrownBy(() -> new HostnameWorkerNodeAssigner("ticket-service", 3))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("hostname must end with a numeric ordinal");
```

- [x] **步骤 2：实现节点模型与策略**

```java
public record WorkerNode(long datacenterId, long workerId) {
}

@FunctionalInterface
public interface WorkerNodeAssigner {
    WorkerNode assign();
}

public final class FixedWorkerNodeAssigner implements WorkerNodeAssigner {
    private final WorkerNode workerNode;
    public FixedWorkerNodeAssigner(long datacenterId, long workerId) {
        this.workerNode = new WorkerNode(datacenterId, workerId);
    }
    public WorkerNode assign() { return workerNode; }
}
```

`HostnameWorkerNodeAssigner` 接收 `hostname` 和 `datacenterId`，用正则 `.*-(\\d+)$` 读取末尾序号并创建 `WorkerNode`。该策略只解析调用方提供的主机名，不尝试从 IP 或 MAC 地址推导节点号。

- [x] **步骤 3：运行节点策略测试**

运行：`cd backend && .\mvnw.cmd -pl components/idgenerator -am -Dsurefire.failIfNoSpecifiedTests=false -Dtest=WorkerNodeAssignerTest test`

预期：固定配置、StatefulSet 序号和非法主机名场景全部通过。

### 任务 3：实现 Snowflake 生成器和解析工具

**文件：**
- 新建：`backend/components/idgenerator/src/main/java/com/lzj/railway/framework/idgenerator/snowflake/IdGenerator.java`
- 新建：`backend/components/idgenerator/src/main/java/com/lzj/railway/framework/idgenerator/snowflake/SnowflakeIdGenerator.java`
- 新建：`backend/components/idgenerator/src/main/java/com/lzj/railway/framework/idgenerator/snowflake/SnowflakeId.java`
- 新建：`backend/components/idgenerator/src/main/java/com/lzj/railway/framework/idgenerator/snowflake/ClockMovedBackwardsException.java`
- 新建：`backend/components/idgenerator/src/main/java/com/lzj/railway/framework/idgenerator/snowflake/SnowflakeIdUtil.java`
- 测试：`backend/components/idgenerator/src/test/java/com/lzj/railway/framework/idgenerator/snowflake/SnowflakeIdGeneratorTest.java`
- 测试：`backend/components/idgenerator/src/test/java/com/lzj/railway/framework/idgenerator/snowflake/SnowflakeIdUtilTest.java`

- [x] **步骤 1：编写生成和解析测试**

```java
SnowflakeIdGenerator generator = new SnowflakeIdGenerator(new WorkerNode(2, 7));
long first = generator.nextId();
long second = generator.nextId();
assertThat(second).isGreaterThan(first);

SnowflakeId parsed = SnowflakeIdUtil.parse(first);
assertThat(parsed.datacenterId()).isEqualTo(2);
assertThat(parsed.workerId()).isEqualTo(7);
assertThat(parsed.timestamp()).isLessThanOrEqualTo(Instant.now().toEpochMilli());
```

使用 `Clock.fixed` 和可变 Clock 测试时间基点前、节点号超界以及时钟从 `lastTimestamp` 回拨 1 毫秒时抛出 `ClockMovedBackwardsException`。

- [x] **步骤 2：实现经典 Snowflake 位布局**

```java
public interface IdGenerator {
    long nextId();
}

public record SnowflakeId(long timestamp, long datacenterId, long workerId, long sequence) {
}
```

`SnowflakeIdGenerator` 使用以下常量：

```java
public static final long DEFAULT_EPOCH = 1704067200000L;
private static final long MAX_DATACENTER_ID = 31L;
private static final long MAX_WORKER_ID = 31L;
private static final long SEQUENCE_MASK = 4095L;
private static final int WORKER_ID_SHIFT = 12;
private static final int DATACENTER_ID_SHIFT = 17;
private static final int TIMESTAMP_SHIFT = 22;
```

构造函数接收 `WorkerNode`，高级构造函数额外接收 `epoch` 和 `Clock`。`nextId()` 必须 `synchronized`：同毫秒递增 sequence；sequence 达到 4095 后等待下一毫秒；时间小于上次成功时间时抛出 `ClockMovedBackwardsException`；时间小于 epoch、节点编号超出 0 到 31 时立即拒绝创建生成器。

- [x] **步骤 3：实现 `SnowflakeIdUtil`**

```java
public final class SnowflakeIdUtil {
    public static SnowflakeIdGenerator create(WorkerNodeAssigner assigner) {
        return new SnowflakeIdGenerator(Objects.requireNonNull(assigner).assign());
    }
    public static SnowflakeId parse(long id) { ... }
}
```

`parse` 使用与生成器相同的位移常量，返回原始毫秒时间、数据中心号、工作节点号和序列号。工具类不得维护静态生成器，避免跨应用实例共享隐藏配置。

- [x] **步骤 4：运行生成器测试**

运行：`cd backend && .\mvnw.cmd -pl components/idgenerator -am -Dsurefire.failIfNoSpecifiedTests=false -Dtest=SnowflakeIdGeneratorTest,SnowflakeIdUtilTest test`

预期：单调递增、并发唯一、位解析、参数校验、时钟回拨保护全部通过。

### 任务 4：补充中文注释、文档和全量验证

**文件：**
- 修改：`README.md`
- 修改：`docs/architecture/project-structure.md`
- 修改：`docs/architecture/spring-boot-starter-structure.md`
- 修改：`docs/superpowers/plans/2026-09-28-id-generator-component.md`

- [x] **步骤 1：更新组件文档**

在目录树增加 `idgenerator/`，说明 artifactId 为 `railway-id-generator`。文档必须描述：41/5/5/12 位布局、节点对必须唯一、固定节点与 StatefulSet 序号策略的适用条件、时钟回拨时拒绝发号，以及未来 Redis、DB、ZooKeeper 分配器通过 `WorkerNodeAssigner` 接入。

- [x] **步骤 2：运行完整验证**

运行：`cd backend && .\mvnw.cmd clean test`

预期：全部 Maven 模块和测试通过。

- [x] **步骤 3：检查改动范围**

运行：`git diff --check` 和 `git status --short`。

预期：只包含 ID 组件、Maven 聚合和 BOM、文档及本实施计划。
