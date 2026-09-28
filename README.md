# Railway Platform

这是一个逐步建设的前后端 Monorepo。目前已建立后端 Maven 多模块结构、`base`、`convention`、`designpattern` 和 `user` 组件，业务服务与前端工程将继续按模块演进。

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
│   ├── common/
│   │   └── pom.xml
│   ├── convention/
│   │   └── pom.xml
│   ├── designpattern/
│   │   └── pom.xml
│   ├── idgenerator/
│   │   └── pom.xml
│   └── user/
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
- `backend/components/common`：通用码值枚举、断言、对象复制、环境和线程工具。
- `backend/components/convention`：错误码、异常、分页和公共响应契约，不依赖 Web 或 ORM。
- `backend/components/designpattern`：框架无关的构建者、责任链和策略模式实现。
- `backend/components/idgenerator`：分布式唯一 Snowflake ID 生成器、节点分配策略和 ID 解析工具。
- `backend/components/user`：JWT 登录凭证、TTL 用户上下文和请求 Token 过滤器。
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

实现会为 Redis 租约 owner 附加随机 UUID，因此不会仅依赖重复的主机名判定实例唯一性。后续持久化组件接入 MyBatis-Plus 时，可将这个 `SnowflakeIdGenerator` 适配为 `IdentifierGenerator`。

## User Starter

业务服务引入 `railway-user-spring-boot-starter` 后，通过环境变量配置至少 32 个 UTF-8 字节的签名密钥即可启用：

```yaml
railway:
  user:
    jwt:
      secret: ${RAILWAY_USER_JWT_SECRET}
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
