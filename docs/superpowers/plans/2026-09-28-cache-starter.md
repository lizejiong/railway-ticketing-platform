# Cache 组件 Starter 实施计划

> **给 agentic workers：** 必需子技能：使用 superpowers:subagent-driven-development（推荐）或 superpowers:executing-plans 按任务逐项执行本计划。步骤使用 checkbox（`- [ ]`）语法跟踪状态。

**目标：** 新增 Redis 缓存基础设施 Starter，统一 JSON 序列化、Key 规范、基础缓存操作、缓存回源、布隆过滤器防穿透、分布式锁防击穿和多 Key 原子占位。

**架构：** Spring Boot 与 Redisson Starter 负责根据 `spring.data.redis.*` 创建连接，cache Starter 自动装配 `DistributedCache`。业务值由 Fastjson2 显式按目标类型序列化，Redisson 提供共享布隆过滤器和按 Key 分布式锁；多 Key 原子占位使用 Lua，并要求所有 Key 共享 Redis Cluster hash tag。

**技术栈：** Java 17、Spring Boot 3.0.7、Spring Data Redis、Redisson 3.21.3、Fastjson2、JUnit 5、Mockito。

---

### 任务 1：注册 Cache Starter 模块与依赖

**文件：**
- 修改：`backend/components/pom.xml`
- 修改：`backend/dependencies/pom.xml`
- 新建：`backend/components/cache/pom.xml`

- [ ] 将 `cache` 加入组件聚合 POM。
- [ ] 在统一 BOM 中管理 `railway-cache-spring-boot-starter` 和 `redisson-spring-boot-starter`。
- [ ] Cache 模块传递依赖 `spring-boot-starter-data-redis`、`redisson-spring-boot-starter`、Fastjson2，并配置测试依赖。
- [ ] 执行 `backend/mvnw.cmd -pl components/cache -am -DskipTests compile`，预期模块可解析并编译。

### 任务 2：定义配置与 Key 规范

**文件：**
- 新建：`backend/components/cache/src/main/java/com/lzj/railway/framework/starter/cache/config/CacheProperties.java`
- 新建：`backend/components/cache/src/main/java/com/lzj/railway/framework/starter/cache/key/RedisKeyBuilder.java`
- 测试：`backend/components/cache/src/test/java/com/lzj/railway/framework/starter/cache/key/RedisKeyBuilderTest.java`

- [ ] 定义 `railway.cache.key-prefix`、布隆过滤器名称、预计元素数量、误判率和锁前缀，提供可用默认值并校验范围。
- [ ] `build(namespace, identifiers...)` 生成 `<prefix>:<namespace>:<identifier...>`。
- [ ] `buildWithHashTag(namespace, hashTag, identifiers...)` 生成 `<prefix>:<namespace>:{<hashTag>}:<identifier...>`，供 Lua 多 Key 操作保证同槽。
- [ ] 对空命名空间、空 hash tag、空标识段进行快速失败测试。

### 任务 3：定义缓存接口与基础实现

**文件：**
- 新建：`backend/components/cache/src/main/java/com/lzj/railway/framework/starter/cache/DistributedCache.java`
- 新建：`backend/components/cache/src/main/java/com/lzj/railway/framework/starter/cache/RedisDistributedCache.java`
- 测试：`backend/components/cache/src/test/java/com/lzj/railway/framework/starter/cache/RedisDistributedCacheTest.java`

- [ ] 定义 `get`、`put`、带 `Duration` 的 `put`、单 Key/批量 `delete` 和 `hasKey`。
- [ ] 使用 `StringRedisTemplate` 存储 Fastjson2 JSON 字符串，读取时按调用方提供的 `Class<T>` 反序列化。
- [ ] 拒绝空 Key、空类型、空值以及非正 TTL，避免模糊缓存语义。
- [ ] 提供 `getRedisTemplate()` 和 `getRedissonClient()`，用于组件未覆盖的底层能力。
- [ ] 使用 Mockito 验证序列化、TTL、删除、存在判断和底层客户端访问。

### 任务 4：实现缓存回源和安全读写

**文件：**
- 修改：`backend/components/cache/src/main/java/com/lzj/railway/framework/starter/cache/DistributedCache.java`
- 修改：`backend/components/cache/src/main/java/com/lzj/railway/framework/starter/cache/RedisDistributedCache.java`
- 修改：`backend/components/cache/src/test/java/com/lzj/railway/framework/starter/cache/RedisDistributedCacheTest.java`

- [ ] `getOrLoad` 在命中时直接返回；未命中时执行 `Supplier<T>`，非空结果写入缓存后返回。
- [ ] `safeGet` 先查缓存，再查布隆过滤器；布隆过滤器判断不存在时直接返回 `null`。
- [ ] `safeGet` 对缓存 Key 获取 Redisson 锁，并在持锁后再次读取；仍未命中时才执行加载器并写入缓存。
- [ ] 使用无固定租期的 `lock()` 交给 Redisson watchdog 续期，并仅由持锁线程释放。
- [ ] `safePut` 严格先写 Redis，再把 Key 加入布隆过滤器。
- [ ] 测试命中不回源、普通未命中回源、布隆过滤器拒绝、锁内二次命中和安全写入顺序。

### 任务 5：实现多 Key 操作

**文件：**
- 修改：`backend/components/cache/src/main/java/com/lzj/railway/framework/starter/cache/DistributedCache.java`
- 修改：`backend/components/cache/src/main/java/com/lzj/railway/framework/starter/cache/RedisDistributedCache.java`
- 修改：`backend/components/cache/src/test/java/com/lzj/railway/framework/starter/cache/RedisDistributedCacheTest.java`

- [ ] 通过 Lua 先检查全部 Key；任一存在返回 `false` 且不写入，全部不存在才一次性写入所有 JSON 值。
- [ ] 支持永久占位和统一 TTL 占位。
- [ ] 调用前验证所有 Key 都包含相同的 `{hashTag}`，保证 Redis Cluster 可执行脚本。
- [ ] 使用 Spring Data Redis 的批量 `EXISTS` 能力实现 `countExistingKeys`。
- [ ] 测试 Lua 参数顺序、返回值、TTL 和不同 hash tag 的拒绝行为。

### 任务 6：实现自动配置

**文件：**
- 新建：`backend/components/cache/src/main/java/com/lzj/railway/framework/starter/cache/config/CacheAutoConfiguration.java`
- 新建：`backend/components/cache/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`
- 测试：`backend/components/cache/src/test/java/com/lzj/railway/framework/starter/cache/config/CacheAutoConfigurationTest.java`

- [ ] 自动配置在 `StringRedisTemplate` 与 `RedissonClient` 均存在时生效。
- [ ] 创建并初始化命名布隆过滤器、`RedisKeyBuilder` 与 `RedisDistributedCache`。
- [ ] 用户提供同名布隆过滤器、Key 构造器或 `DistributedCache` 时自动退让。
- [ ] 验证配置属性绑定、默认装配、客户端缺失和自定义 Bean 覆盖。

### 任务 7：文档与完整验证

**文件：**
- 修改：`README.md`
- 修改：`docs/architecture/project-structure.md`
- 修改：`docs/architecture/spring-boot-starter-structure.md`

- [ ] 补充 Cache Starter 目录、依赖方式、`spring.data.redis.*` 与 `railway.cache.*` 配置示例。
- [ ] 给出 `RedisKeyBuilder`、`getOrLoad`、`safeGet`、`safePut` 和同 hash tag 原子占位示例。
- [ ] 明确布隆过滤器需要由已有数据预热，新数据必须通过 `safePut` 同步加入。
- [ ] 执行 `backend/mvnw.cmd -pl components/cache -am test`。
- [ ] 执行 `backend/mvnw.cmd test`，预期全部模块通过。
- [ ] 执行 `git diff --check`，预期无空白错误。
