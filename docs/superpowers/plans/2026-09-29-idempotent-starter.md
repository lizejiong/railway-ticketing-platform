# Idempotent 幂等 Starter 实施计划

**目标：** 通过 `@Idempotent` 为 REST API 与 MQ 消费提供基于 Redis 的尽力而为幂等控制，同时保持业务唯一键策略显式、易懂。

**公开语义：** `scene` 选择 REST/MQ 重复处理方式，`type` 选择 TOKEN/PARAM/SPEL 唯一键来源；Redis 状态机属于组件内部实现。

## 实施项

- [x] 注册 `railway-idempotent-spring-boot-starter` 模块和 BOM 版本。
- [x] 定义 `@Idempotent`、`IdempotentScene` 与 `IdempotentType`。
- [x] 支持 HTTP `Idempotency-Key` Token、全参数 SHA-256 和 SpEL 三种 Key 来源。
- [x] 使用 `uniqueKeyPrefix` 隔离业务操作，未配置时回退到方法签名摘要。
- [x] 按 REST/MQ 场景路由执行器：REST 重复抛客户端异常，MQ 重复跳过方法。
- [x] 使用 `PROCESSING:<UUID>` 和 `COMPLETED` 状态控制并发与 TTL 窗口内重试。
- [x] 使用 Lua 比较执行令牌后原子完成或释放，避免旧请求修改新请求状态。
- [x] 业务失败时释放标记；业务成功后 Redis 更新失败只记录日志，不诱发业务重试。
- [x] 注册 Servlet Token 与核心幂等自动配置，并支持业务 Bean 覆盖默认实现。
- [x] 补充 Key、Token、执行器、AOP 与自动配置测试。
- [x] 更新根 README 与架构文档。
- [x] 运行全后端 Maven 测试并执行 `git diff --check`。

## 边界

- 本组件不保证严格 exactly-once。
- MQ 业务仍需数据库唯一索引、消费记录表或事务消息兜底。
- MQ 重复消息会直接跳过，消费方法应返回 `void`。
- Spring AOP 自调用不会触发 `@Idempotent`。
