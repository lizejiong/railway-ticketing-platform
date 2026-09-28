# Redis 单节点号分配实施计划

> **给 agentic workers：** 使用 `superpowers:executing-plans` 按任务逐项执行本计划。步骤使用 checkbox（`- [ ]`）语法跟踪状态。
**目标：** 将 ID 组件收敛为可选的 Spring Data Redis 集成：业务服务主动引入 Redis Starter 后，组件自动租约分配一个 `0-1023` 的节点号并拆成 Snowflake 的两个 5 位字段。

**架构：** `railway-id-generator` 保持单 Maven 模块。Spring Data Redis 与 Spring Boot 自动配置均为 optional 依赖，业务服务不会传递获得 Redis Starter。`RedisWorkerNodeAssigner` 通过构造器接收 `StringRedisTemplate`，由自动配置在容器已有该 Bean 时创建；Redis Lua 只处理一个节点号租约。

**技术栈：** Java 17、Spring Boot 3.0.7、Spring Data Redis、Redis Lua、JUnit 5、Mockito、AssertJ。

---

### 任务 1：替换 Redis 客户端依赖和节点分配实现

**文件：**
- 修改：`backend/components/idgenerator/pom.xml`
- 修改：`backend/dependencies/pom.xml`
- 修改：`backend/components/idgenerator/src/main/java/com/lzj/railway/framework/idgenerator/snowflake/RedisWorkerNodeAssigner.java`
- 测试：`backend/components/idgenerator/src/test/java/com/lzj/railway/framework/idgenerator/snowflake/RedisWorkerNodeAssignerTest.java`

- [x] **步骤 1：编写单节点号映射测试**

```java
when(redisTemplate.execute(any(), anyList(), any(), any())).thenReturn(66L);
assertThat(assigner.assign()).isEqualTo(new WorkerNode(2, 2));
```

验证 Redis 返回 `nodeId` 后，`datacenterId = nodeId >> 5`、`workerId = nodeId & 31`。

- [x] **步骤 2：实现 Spring Data Redis Lua 调用**

`RedisWorkerNodeAssigner` 使用 `StringRedisTemplate.execute(DefaultRedisScript<Long>, keys, ownerId, leaseMillis)`。预占、续租和释放脚本保留 compare-and-expire / compare-and-delete 语义；候选键改为 `0-1023`，并使用相同 hash tag。

- [x] **步骤 3：标记依赖为可选依赖**

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-autoconfigure</artifactId>
    <optional>true</optional>
</dependency>
<dependency>
    <groupId>org.springframework.data</groupId>
    <artifactId>spring-data-redis</artifactId>
    <optional>true</optional>
</dependency>
```

删除组件中的 `redisson` 依赖和本次新增的 BOM 管理项；保留已有的 `redisson.version` 属性，以便后续独立 Redis 组件使用。

### 任务 2：添加最小 Spring Boot 自动配置

**文件：**
- 新建：`backend/components/idgenerator/src/main/java/com/lzj/railway/framework/idgenerator/autoconfigure/IdGeneratorAutoConfiguration.java`
- 新建：`backend/components/idgenerator/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`
- 测试：`backend/components/idgenerator/src/test/java/com/lzj/railway/framework/idgenerator/autoconfigure/IdGeneratorAutoConfigurationTest.java`

- [x] **步骤 1：编写上下文测试**

```java
new ApplicationContextRunner()
        .withBean(StringRedisTemplate.class, () -> redisTemplate)
        .withConfiguration(AutoConfigurations.of(IdGeneratorAutoConfiguration.class))
        .run(context -> assertThat(context).hasSingleBean(RedisWorkerNodeAssigner.class));
```

无 `StringRedisTemplate` 时断言不创建 `WorkerNodeAssigner`。

- [x] **步骤 2：实现自动配置**

```java
@AutoConfiguration
@ConditionalOnClass(StringRedisTemplate.class)
@ConditionalOnBean(StringRedisTemplate.class)
public class IdGeneratorAutoConfiguration {
    @Bean(destroyMethod = "close")
    @ConditionalOnMissingBean(WorkerNodeAssigner.class)
    RedisWorkerNodeAssigner redisWorkerNodeAssigner(StringRedisTemplate redisTemplate) {
        return new RedisWorkerNodeAssigner(redisTemplate);
    }
}
```

自动配置不读取 `spring.data.redis.host`，也不从 `ApplicationContext` 主动查找 Bean。用户自定义 `WorkerNodeAssigner` 时自动配置退让。

### 任务 3：更新说明并验证

**文件：**
- 修改：`README.md`
- 修改：`docs/architecture/spring-boot-starter-structure.md`
- 修改：`docs/superpowers/plans/2026-09-28-simplify-redis-worker-id.md`

- [x] **步骤 1：更新使用边界**

明确业务服务需直接依赖 `spring-boot-starter-data-redis`；组件自身的 Redis 依赖为 optional。说明自动配置以 `StringRedisTemplate` 为条件，并说明 Redis 节点号 `0-1023` 的拆分规则。

- [x] **步骤 2：运行验证**

运行：`cd backend && .\mvnw.cmd clean test`、`git diff --check`、`git status --short`。

预期：所有模块通过，业务服务未引入 Redis Starter 时不加载 Redis 自动配置。
