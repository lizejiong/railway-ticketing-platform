# 单一 Node ID 模型实施计划

> **给 agentic workers：** 使用 `superpowers:executing-plans` 按任务逐项执行本计划。步骤使用 checkbox（`- [ ]`）语法跟踪状态。
**目标：** 将 Snowflake 节点标识从 `datacenterId + workerId` 收敛为单一的 10 位 `nodeId`，由 Redis 全局租约分配 0 到 1023。

**架构：** ID 位布局保持 41 位时间差、10 位节点号和 12 位序列。`WorkerNode`、`SnowflakeId`、策略接口实现、生成器和解析工具都只暴露 `nodeId`。未来多机房仍可通过新的策略计算 `nodeId = (datacenterId << 5) | workerId`，无需再修改 Snowflake 位布局。

**技术栈：** Java 17、Spring Data Redis、Redis Lua、JUnit 5、Mockito、AssertJ。

---

### 任务 1：收敛核心模型与算法

**文件：**
- 修改：`backend/components/idgenerator/src/main/java/com/lzj/railway/framework/idgenerator/snowflake/WorkerNode.java`
- 修改：`backend/components/idgenerator/src/main/java/com/lzj/railway/framework/idgenerator/snowflake/SnowflakeId.java`
- 修改：`backend/components/idgenerator/src/main/java/com/lzj/railway/framework/idgenerator/snowflake/SnowflakeIdGenerator.java`
- 修改：`backend/components/idgenerator/src/main/java/com/lzj/railway/framework/idgenerator/snowflake/SnowflakeIdUtil.java`
- 测试：`backend/components/idgenerator/src/test/java/com/lzj/railway/framework/idgenerator/snowflake/SnowflakeIdGeneratorTest.java`
- 测试：`backend/components/idgenerator/src/test/java/com/lzj/railway/framework/idgenerator/snowflake/SnowflakeIdUtilTest.java`

- [x] **步骤 1：更新核心测试断言**

```java
SnowflakeId decoded = SnowflakeIdUtil.parse(id);
assertThat(decoded.nodeId()).isEqualTo(66L);
```

覆盖合法范围 0 到 1023、非法节点号与解析结果。

- [x] **步骤 2：实现单一节点号位运算**

```java
return ((timestamp - epoch) << TIMESTAMP_SHIFT)
        | (nodeId << NODE_ID_SHIFT)
        | sequence;
```

`WorkerNode` 与 `SnowflakeId` 只保存 `nodeId`。删除数据中心与工作节点的位移常量，新增 `MAX_NODE_ID = 1023` 与 `NODE_ID_SHIFT = 12`。

### 任务 2：同步节点分配策略

**文件：**
- 修改：`backend/components/idgenerator/src/main/java/com/lzj/railway/framework/idgenerator/snowflake/FixedWorkerNodeAssigner.java`
- 修改：`backend/components/idgenerator/src/main/java/com/lzj/railway/framework/idgenerator/snowflake/HostnameWorkerNodeAssigner.java`
- 修改：`backend/components/idgenerator/src/main/java/com/lzj/railway/framework/idgenerator/snowflake/RedisWorkerNodeAssigner.java`
- 测试：`backend/components/idgenerator/src/test/java/com/lzj/railway/framework/idgenerator/snowflake/WorkerNodeAssignerTest.java`
- 测试：`backend/components/idgenerator/src/test/java/com/lzj/railway/framework/idgenerator/snowflake/RedisWorkerNodeAssignerTest.java`

- [x] **步骤 1：更新策略测试**

```java
assertThat(new FixedWorkerNodeAssigner(66).assign()).isEqualTo(new WorkerNode(66));
assertThat(new HostnameWorkerNodeAssigner("ticket-service-66").assign())
        .isEqualTo(new WorkerNode(66));
```

- [x] **步骤 2：让全部策略直接返回 nodeId**

固定策略直接接收 `nodeId`；主机名策略校验末尾编号在 0 到 1023；Redis 策略将 Lua 返回值直接构造成 `WorkerNode`，不再拆分位段。

### 任务 3：更新文档并验证

**文件：**
- 修改：`README.md`
- 修改：`docs/architecture/spring-boot-starter-structure.md`
- 修改：`docs/superpowers/plans/2026-09-28-single-node-id-model.md`

- [x] **步骤 1：删除旧双字段语义**

文档统一使用“10 位 `nodeId`、范围 0 到 1023”，说明未来多机房策略可在分配阶段组合 nodeId，不在 Snowflake 核心中保存机房字段。

- [x] **步骤 2：运行验证**

运行：`cd backend && .\mvnw.cmd clean test`、`git diff --check`、`git status --short`。

预期：全部模块测试通过，ID 组件没有 `datacenterId` 或 `workerId` 的运行时代码引用。
