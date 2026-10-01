# Railway Platform

## 票务查询

`ticket-service` 默认监听 `8082`，通过 Nacos 中的 `ticket-service.yaml` 配置 MySQL 与端口，并由网关将 `/api/ticket/**` 转发至该服务。车次与余票属于公开信息，不需要携带 JWT。

- `GET /api/ticket/query?departure=北京南&arrival=上海虹桥&departureDate=2026-10-02`：按区间和乘车日期查询可售车次、席别票价与实时余票。

首版直接从 `12306_ticket` 的 `t_train_station_relation`、`t_train`、`t_train_station_price` 和 `t_seat` 查询；余票按未锁定座位实时聚合。暂不包含 Redis 余票缓存、选座、购票扣减、订单和支付，这些会在后续购票链路中实现。

## 用户账号核心接口

所有客户端请求统一从网关 `http://127.0.0.1:8080` 进入。注册、登录、刷新令牌和退出登录为公开接口；用户资料与乘车人接口必须携带 `Authorization: Bearer <access-token>`。

- `POST /api/user/register`：注册账号。
- `POST /api/user/login`：登录并获得 Access Token 与 Refresh Token。
- `POST /api/user/token/refresh`：消费旧 Refresh Token，轮换得到一组新 Token。
- `POST /api/user/logout`：撤销 Refresh Token；已签发的短期 Access Token 到期后自然失效。
- `GET /api/user/profile`：查询当前登录用户的脱敏资料。
- `GET /api/user/passengers`：查询当前用户的乘车人列表。
- `POST /api/user/passengers`：新增乘车人。
- `PUT /api/user/passengers/{passengerId}`：修改当前用户拥有的乘车人。
- `DELETE /api/user/passengers/{passengerId}`：软删除当前用户拥有的乘车人。

Refresh Token 原文只返回给客户端，Redis 中仅保存其 SHA-256 哈希及会话元数据。乘车人查询、修改和删除始终携带当前用户名作为分片键；证件号和手机号由 ShardingSphere AES 加密落库，对外响应只返回脱敏值。

## User Service：注册与登录（第一阶段）

`user-service` 现在监听 `8081`，已实现以下端点：

- `POST /api/user/register`：用户名、密码、手机号、邮箱注册；用户名使用 Redisson 分布式锁，密码使用 BCrypt 哈希，写入 `t_user`、`t_user_phone`、`t_user_mail`。
- `POST /api/user/login`：支持用户名、手机号或邮箱加密码登录。手机号/邮箱会先查询索引表获得用户名，再查询 `t_user`。

成功登录返回 15 分钟的 JWT Access Token 和 30 天的随机 Refresh Token。Redis 仅以 `SHA-256` 哈希作为 Refresh Token 的 Key 保存 Session 元数据，绝不保存原始 Refresh Token。注册提交后会清理预留的用户缓存 Key，并将用户名加入 `railway:user:username` Bloom Filter。

本地运行前，启动 `deploy/compose.yaml` 中的 MySQL、Redis 和 Nacos；用户分库配置默认连接 `12306_user_0` / `12306_user_1`，其本地开发密码与 `deploy/.env.example` 的 `MYSQL_ROOT_PASSWORD` 一致。生产环境应将数据源密码、JWT 密钥和 AES 密钥替换为独立密钥管理系统中的值。

注册目前使用普通 Spring `@Transactional`。因为手机号与邮箱索引可能路由到不同的物理库，这不能提供跨库原子提交保证；后续若需要强一致性，应改为分布式事务或采用可靠事件/补偿方案。

本阶段不实现验证码、账号冻结/注销、失败次数锁定与风控、登录审计、登出、Refresh Token 换发、找回/修改密码、实名资料和乘车人接口；相应错误码已预留，后续逐项接入。

这是一个逐步建设的前后端 Monorepo。目前已建立后端 Maven 多模块结构和基础组件库，业务服务与前端工程将继续按模块演进。

## 当前结构

```text
backend/
├── pom.xml
├── dependencies/
│   └── pom.xml
├── parent/
│   └── pom.xml
├── components/
│   ├── pom.xml
│   ├── base/
│   │   └── pom.xml
│   ├── cache/
│   │   └── pom.xml
│   ├── common/
│   │   └── pom.xml
│   ├── convention/
│   │   └── pom.xml
│   ├── designpattern/
│   │   └── pom.xml
│   ├── idgenerator/
│   │   └── pom.xml
│   ├── idempotent/
│   │   └── pom.xml
│   ├── log/
│   │   └── pom.xml
│   ├── persistence/
│   │   └── pom.xml
│   ├── user/
│   │   └── pom.xml
│   └── web/
│       └── pom.xml
└── services/

frontend/
└── web/
```

- `backend/pom.xml`：聚合所有后端 Maven 模块。
- `backend/dependencies/pom.xml`：纯 BOM，统一管理 Spring Boot、第三方依赖和内部组件版本。
- `backend/parent/pom.xml`：统一管理 Java、编码和 Maven 插件等构建约定，并导入 dependencies BOM。
- `backend/components/pom.xml`：聚合逐个增加的后端组件与 Spring Boot Starter。
- `backend/components/base`：基础常量、单例容器、启动事件和基础自动配置。
- `backend/components/cache`：Redis JSON 缓存、Key 规范、缓存回源、防穿透和防击穿。
- `backend/components/common`：通用码值枚举、断言、对象复制、环境和线程工具。
- `backend/components/convention`：错误码、异常、分页和公共响应契约，不依赖 Web 或 ORM。
- `backend/components/designpattern`：框架无关的构建者、责任链和策略模式实现。
- `backend/components/idgenerator`：分布式唯一 Snowflake ID 生成器、节点分配策略和 ID 解析工具。
- `backend/components/idempotent`：基于 Redis、Redisson 和 `@Idempotent` 的 REST/MQ 幂等控制。
- `backend/components/log`：基于 `@ILog` 和 Spring AOP 的方法入参、返回值与耗时日志。
- `backend/components/persistence`：MyBatis-Plus 分页、基础持久化对象、字段自动填充和统一主键生成。
- `backend/components/user`：JWT 登录凭证、TTL 用户上下文和请求 Token 过滤器。
- `backend/components/web`：统一 Web 异常响应和 `Results` 快捷构造。
- `backend/services`：后续按业务服务名称增加独立微服务模块。
- `frontend/web`：仅保留前端项目边界，开发范式后续确认。

详细设计见 [docs/architecture/project-structure.md](docs/architecture/project-structure.md)。

## Design Pattern

构建者模式仅提供 `Builder<T>` 契约；具体对象在其所属模块实现专用 Builder，或使用 Lombok 的 `@Builder`。

责任链按 `ChainHandler.order()` 升序执行，处理器返回 `STOP` 时立即中断：

```java
ResponsibilityChain<OrderRequest> chain = ResponsibilityChain.<OrderRequest>builder()
        .add(request -> request.isValid() ? ChainDecision.CONTINUE : ChainDecision.STOP)
        .add(request -> reserveTicket(request))
        .build();

ChainDecision decision = chain.execute(request);
```

策略接口只定义业务标识和执行方法，选择器负责按标识执行：

```java
StrategySelector<OrderRequest, OrderResult> selector = new StrategySelector<>(
        List.of(highSpeedStrategy, regularTrainStrategy));

OrderResult result = selector.execute("high-speed", request);
```

该模块不自动扫描 Spring Bean。Spring 业务服务可以注入 `List<Strategy<OrderRequest, OrderResult>>` 或 `List<ChainHandler<T>>` 后构造选择器和责任链，从而保持公共组件框架无关。

## ID Generator

`railway-id-generator` 提供 Redis 租约式工作节点分配器 `RedisWorkerNodeAssigner`。Redis 原子分配一个 `0` 到 `1023` 的 10 位 `nodeId`，直接写入 Snowflake ID；租约续租失败后，`SnowflakeIdGenerator` 会拒绝继续生成 ID，避免节点号被其他实例复用时产生重复值。

组件内的 Spring Data Redis 依赖为 optional，业务服务需要自行直接引入 Redis Starter：

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-redis</artifactId>
</dependency>
```

服务创建 `StringRedisTemplate` 后，组件自动注册带 `destroyMethod = "close"` 的 `RedisWorkerNodeAssigner`；没有 Redis Starter 或 `StringRedisTemplate` 时，不创建任何 Redis 工作节点策略。业务服务需要 ID 生成器时，再按自身业务边界组装：

```java
@Bean
SnowflakeIdGenerator snowflakeIdGenerator(WorkerNodeAssigner workerNodeAssigner) {
    return SnowflakeIdUtil.create(workerNodeAssigner);
}
```

实现会为 Redis 租约 owner 附加随机 UUID，因此不会仅依赖重复的主机名判定实例唯一性。持久层 Starter 会在容器存在 `WorkerNodeAssigner` 时，将这个 `SnowflakeIdGenerator` 自动适配为 MyBatis-Plus 的 `IdentifierGenerator`。

## Cache Starter

业务服务引入 `railway-cache-spring-boot-starter` 后，会同时获得 Spring Data Redis、Redisson 和 `DistributedCache` 自动配置：

```xml
<dependency>
    <groupId>com.lzj.railway</groupId>
    <artifactId>railway-cache-spring-boot-starter</artifactId>
</dependency>
```

连接沿用 Spring Boot 配置，组件只增加缓存命名配置：

```yaml
spring:
  data:
    redis:
      host: 127.0.0.1
      port: 6379

railway:
  cache:
    key-prefix: railway
    lock-key-prefix: railway:cache:lock:
    bloom:
      name: railway:cache:bloom
      expected-insertions: 1000000
      false-positive-probability: 0.01
```

普通缓存回源在未命中时执行传入的 lambda，非空结果按 TTL 写回：

```java
String key = keyBuilder.build("train", trainNumber);
TrainDTO train = distributedCache.getOrLoad(
        key,
        TrainDTO.class,
        () -> trainMapper.selectByTrainNumber(trainNumber),
        Duration.ofMinutes(10)
);
```

`safeGet` 在普通回源之外增加布隆过滤器和 Redisson 分布式锁，并在获得锁后再次读取缓存；`safePut` 严格先写缓存，再登记布隆过滤器：

```java
distributedCache.safePut(key, train, Duration.ofMinutes(10));
TrainDTO cached = distributedCache.safeGet(
        key,
        TrainDTO.class,
        () -> trainMapper.selectByTrainNumber(trainNumber),
        Duration.ofMinutes(10)
);
```

布隆过滤器必须在上线或数据迁移时用已有合法 Key 预热，新数据应通过 `safePut` 写入；否则 `safeGet` 会认为 Key 不存在并跳过数据库。布隆过滤器存在误判，因此它只能避免大部分无效回源，不能替代数据库约束。

多 Key 原子占位由 Lua 完成。参与同一次操作的 Key 必须使用相同 hash tag，以满足 Redis Cluster 同槽要求：

```java
String first = keyBuilder.buildWithHashTag("seat", trainNumber, "carriage-1", "1A");
String second = keyBuilder.buildWithHashTag("seat", trainNumber, "carriage-1", "1B");
boolean occupied = distributedCache.putIfAllAbsent(
        Map.of(first, orderId, second, orderId),
        Duration.ofMinutes(5)
);
```

只要其中一个 Key 已存在，本次 Lua 调用就不会写入任何 Key。`countExistingKeys` 可统计一组 Key 中已存在的数量；确实需要组件未覆盖的 Redis 能力时，可通过 `getRedisTemplate()` 或 `getRedissonClient()` 获取底层客户端。

## Idempotent Starter

业务服务引入 `railway-idempotent-spring-boot-starter` 并正常配置 Redis 后，在 Spring Bean 的业务方法上添加 `@Idempotent` 即可：

```xml
<dependency>
    <groupId>com.lzj.railway</groupId>
    <artifactId>railway-idempotent-spring-boot-starter</artifactId>
</dependency>
```

REST API 可以通过 SpEL 提取订单号等业务唯一字段，在 TTL 窗口内拒绝重复提交：

```java
@Idempotent(
        type = IdempotentType.SPEL,
        key = "#request.orderId",
        uniqueKeyPrefix = "order:submit",
        keyTimeout = 10,
        timeUnit = TimeUnit.MINUTES
)
public void submitOrder(OrderCreateRequest request) {
    orderService.create(request);
}
```

客户端能够生成请求唯一 Token 时，也可以选择 `TOKEN`。组件默认读取 `Idempotency-Key` 请求头：

```java
@Idempotent(type = IdempotentType.TOKEN, uniqueKeyPrefix = "passenger:create")
public void createPassenger(PassengerCreateRequest request) {
    passengerService.create(request);
}
```

MQ 消费应使用业务事件 ID，而不是 HTTP Token；发现重复消息时直接跳过业务方法，因此消费方法应返回 `void`：

```java
@Idempotent(
        scene = IdempotentScene.MQ,
        type = IdempotentType.SPEL,
        key = "#message.eventId",
        uniqueKeyPrefix = "order:created"
)
public void consume(OrderCreatedMessage message) {
    ticketService.handle(message);
}
```

`type` 决定如何识别同一次业务调用：`TOKEN` 读取 HTTP 请求头，`PARAM` 对全部方法参数 JSON 计算 SHA-256，`SPEL` 根据 `key` 表达式提取业务字段。`scene` 决定重复后的行为：REST 抛出 `ClientException`，MQ 跳过方法。底层统一使用 Redis `PROCESSING:<UUID>/COMPLETED` 状态；完成和失败清理都通过 Lua 校验执行令牌，旧请求不能覆盖或删除后来请求的状态。

完整 Redis Key 格式为 `全局前缀:场景:业务前缀:唯一值`。未设置 `uniqueKeyPrefix` 时使用方法签名摘要作为业务前缀；`keyTimeout` 默认 1 小时，业务异常时释放自己的标记，成功后从完成时刻重新计算 TTL。

可选全局配置包括开关、Key 前缀和 TOKEN 请求头名称：

```yaml
railway:
  idempotent:
    enabled: true
    key-prefix: railway:idempotent
    token-header: Idempotency-Key
```

该组件降低重复执行概率，不保证分布式系统的严格 exactly-once。数据库唯一索引、业务状态机和事务消息仍应作为最终一致性保障；同一个 Bean 内通过 `this` 发起的自调用也不会触发 Spring AOP。

## Log Starter

业务服务引入 `railway-log-spring-boot-starter` 后，在由 Spring 管理的 Bean 方法上添加 `@ILog`：

```java
@ILog("查询车次")
public TrainDTO queryTrain(String trainNumber) {
    return trainService.query(trainNumber);
}
```

切面会在一条成功日志中记录操作描述、`类名#方法名`、入参 JSON、返回值 JSON 和执行耗时；方法抛出异常时记录入参、耗时和异常堆栈，然后原样抛出异常。密码、Token 等敏感接口应关闭相应内容：

```java
@ILog(value = "用户登录", recordArgs = false, recordResult = false)
public LoginResponse login(LoginRequest request) {
    return loginService.login(request);
}
```

全局配置：

```yaml
railway:
  log:
    enabled: true
    max-content-length: 4096
```

该组件基于 Spring 代理，只拦截从 Bean 外部进入代理对象的调用；同一个 Bean 内通过 `this` 发起的自调用不会触发 `@ILog`。超长参数和结果会按 `max-content-length` 截断，JSON 格式化失败会回退为普通字符串，不影响业务方法执行。

## Web Starter

业务服务引入 `railway-web-spring-boot-starter` 后，会自动注册全局异常处理器，并可以通过 `Results` 快速构造统一响应：

```java
return Results.success(trainDTO);
return Results.failure(TrainErrorCode.TRAIN_NOT_FOUND);
```

`Results` 会为每个响应生成请求 ID。全局异常处理器只保留三个统一入口：

| 异常 | 响应错误码 |
| --- | --- |
| 参数绑定、类型转换或请求体错误 | `BaseErrorCode.CLIENT_ERROR` |
| `AbstractException` 及其子类 | 异常携带的错误码和消息 |
| 其他 `Exception` | `BaseErrorCode.SERVICE_ERROR` |

未知异常不会把内部异常消息返回给客户端，但会在服务端日志中保留堆栈。异常响应通过 `Result.code` 表达结果，HTTP 响应状态保持 200。

## Persistence Starter

业务服务引入 `railway-persistence-spring-boot-starter` 后，会得到 MySQL 分页插件和基础字段自动填充能力：

```xml
<dependency>
    <groupId>com.lzj.railway</groupId>
    <artifactId>railway-persistence-spring-boot-starter</artifactId>
</dependency>
```

数据库实体继承 `BaseDO`，只定义自己的主键和业务字段：

```java
@TableName("t_train")
public class TrainDO extends BaseDO {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private String trainNumber;
}
```

`createTime`、`updateTime` 和 `deleted` 由 `PersistenceMetaObjectHandler` 维护。分页查询时，使用 `PageUtil` 隔离接口规约与 ORM 类型：

```java
Page<TrainDO> queryPage = PageUtil.convert(request);
IPage<TrainDO> result = trainMapper.selectPage(queryPage, queryWrapper);
PageResponse<TrainDTO> response = PageUtil.convert(result, this::toDTO);
```

生产环境引入 Redis Starter 并正常配置 `spring.data.redis` 后，ID 组件会创建 Redis 节点分配器，持久层 Starter 随即替换 MyBatis-Plus 默认 ID 生成器。无需 Redis 的单机或测试环境可显式提供固定节点：

```java
@Bean
WorkerNodeAssigner workerNodeAssigner() {
    return new FixedWorkerNodeAssigner(1);
}
```

如果业务没有提供任何 `WorkerNodeAssigner`，持久层 Starter 不接管 `IdentifierGenerator`，MyBatis-Plus 保持其默认行为。业务自定义 `MybatisPlusInterceptor`、`MetaObjectHandler` 或 `IdentifierGenerator` 时，组件的对应默认 Bean 会自动退让。

## User Starter

业务服务引入 `railway-user-spring-boot-starter` 后，通过环境变量配置至少 32 个 UTF-8 字节的签名密钥即可启用：

```yaml
railway:
  user:
    jwt:
      secret: ${USER_JWT_SECRET}
      expiration: 2h
      issuer: railway-platform
      header-name: Authorization
      token-prefix: "Bearer "
```

登录成功后使用 `JwtTokenGenerator.generateToken(UserInfoDTO)` 生成凭证。请求使用 `Authorization: Bearer <token>`，过滤器会校验签名、过期时间和签发方，并在当前请求内通过 `UserContext.getUser()` 或 `UserContext.getUserId()` 读取用户；请求完成后上下文会自动清理。

`UserContext` 使用 Alibaba TransmittableThreadLocal。需要把上下文传入线程池任务时，应使用 `TtlExecutors` 包装执行器或接入 TTL Agent；普通线程池不会自动获得安全的上下文传播。非 HTTP 场景手动绑定用户后，也必须在 `finally` 中调用 `UserContext.removeUser()`。

## 验证

```bash
cd backend
./mvnw validate
```

## 服务模块骨架

`backend/services` 已聚合以下 Spring Cloud Alibaba 服务模块：

- `gateway-service`：统一入口，通过 Nacos 路由到业务服务，并在路由层校验 JWT。
- `user-service`：用户域模块，预置 Spring MVC、Nacos 服务发现和配置中心依赖。
- `ticket-service`：票务域模块，预置 Spring MVC、Nacos 服务发现和配置中心依赖。
- `order-service`：订单域模块，预置 Spring MVC、Nacos 服务发现和配置中心依赖。
- `pay-service`：支付域模块，预置 Spring MVC、Nacos 服务发现和配置中心依赖。

当前已实现用户服务注册、登录以及用户域网关路由；票务、订单和支付服务仍只保留模块骨架，后续按服务逐个实现。

## API Gateway

`gateway-service` 默认监听 `8080`，通过 Nacos 将 `/api/user/**` 原样转发到 `user-service`，不需要 `StripPrefix` 或其他路径改写。

以下请求不要求登录：

- `POST /api/user/register`
- `POST /api/user/login`
- 浏览器发起的 `OPTIONS` 预检请求

其余用户域路径需要携带有效的 `Authorization: Bearer <access-token>`。网关和用户服务复用同一份 `railway.user.jwt` 签名配置；网关只校验并转发原始 Token，不注入 `userId`、`username` 等身份请求头，也不查询 Redis 会话或 Token 黑名单。

网关本地配置只负责导入 Nacos 的 `gateway-service.yaml`。端口、路由、公开路径和 JWT 参数均在 Nacos 中维护；JWT 密钥通过 `USER_JWT_SECRET` 环境变量注入，生产环境不得使用本地默认值。

## Nacos 本地开发环境

Nacos 用作 Spring Cloud Alibaba 的服务注册与配置中心。复制本地环境变量后，只启动 Nacos：

```powershell
Copy-Item deploy/.env.example deploy/.env
docker compose --env-file deploy/.env -f deploy/compose.yaml up -d nacos
docker compose --env-file deploy/.env -f deploy/compose.yaml ps nacos
```

控制台地址为 `http://localhost:8848/nacos`，本地开发的默认账号为 `nacos` / `nacos`。服务端 HTTP 地址为 `localhost:8848`，Nacos 2.x gRPC 客户端端口为 `9848`。

Windows 命令行启动服务时需确保 JVM 使用 UTF-8，否则当前 Spring Cloud Alibaba 版本解析带中文注释的 Nacos YAML 可能抛出 `MalformedInputException`：

```powershell
$env:JAVA_TOOL_OPTIONS='-Dfile.encoding=UTF-8'
```

该 Compose 服务使用带持久卷的单机 Derby 存储，并已启用鉴权；只适用于本地开发。`.env.example` 中的 token 是公开的本地示例，生产环境必须替换为独立的 Base64 密钥（原文至少 32 字节）并部署 Nacos 集群与外部存储。

## Redis 本地开发环境

Redis 为缓存、分布式 ID 和幂等控制提供本地中间件环境。它启用了认证、AOF 持久化，并使用 `redis-data` Docker 命名卷保存数据：

```powershell
docker compose --env-file deploy/.env -f deploy/compose.yaml up -d redis
docker compose --env-file deploy/.env -f deploy/compose.yaml ps redis
docker compose --env-file deploy/.env -f deploy/compose.yaml exec -T redis sh -c 'redis-cli --no-auth-warning -a "$REDIS_PASSWORD" ping'
```

本机地址为 `localhost:6379`。业务服务后续应通过环境变量或 Nacos 获取 Redis 密码，不能将本地示例密码用于生产环境。
