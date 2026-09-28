# Railway Platform

这是一个逐步建设的前后端 Monorepo。目前已建立后端 Maven 多模块结构、`base`、`convention` 和 `user` 组件，业务服务与前端工程将继续按模块演进。

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
│   ├── convention/
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
- `backend/components/convention`：错误码、异常、分页和公共响应契约，不依赖 Web 或 ORM。
- `backend/components/user`：JWT 登录凭证、TTL 用户上下文和请求 Token 过滤器。
- `backend/services`：后续按业务服务名称增加独立微服务模块。
- `frontend/web`：仅保留前端项目边界，开发范式后续确认。

详细设计见 [docs/architecture/project-structure.md](docs/architecture/project-structure.md)。

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
