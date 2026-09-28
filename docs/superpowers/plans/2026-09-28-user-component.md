# User 基础组件实施计划

> **给 agentic workers：** 执行本计划时使用 `superpowers:executing-plans`，按任务逐项实现并在每个检查点运行测试。

**目标：** 新增 `railway-user-spring-boot-starter`，统一提供 JWT 登录凭证、可跨受控异步任务传递的当前用户上下文，以及自动解析请求 Token 的 Servlet 过滤器。

**架构：** `components/user` 作为独立 Spring Boot Starter，依赖 `base` 复用用户字段和过滤器顺序常量。JWT 是进入服务的可信身份载体，过滤器只从可配置的 Token Header 中解析已签名 Claims，不直接信任可伪造的独立用户 Header；解析成功后绑定不可变 `UserInfoDTO`，并在请求结束时始终清理上下文。

**技术栈：** Java 17、Spring Boot 3.0.7、JJWT 0.9.1、Alibaba TransmittableThreadLocal 2.14.2、Lombok、JUnit 5、AssertJ、Spring MockMvc 测试工具。

---

### 任务 1：接入 user Maven 模块

**文件：**
- 新建：`backend/components/user/pom.xml`
- 修改：`backend/components/pom.xml`
- 修改：`backend/dependencies/pom.xml`

- [x] **步骤 1：新建 Starter POM**

声明 `railway-user-spring-boot-starter`，引入 `railway-base-spring-boot-starter`、`jjwt`、`transmittable-thread-local`、Spring Web 与可选 Lombok；为 Java 17 补充 JJWT 0.9.1 运行时需要的 JAXB API。

- [x] **步骤 2：接入聚合与 BOM**

在 `components/pom.xml` 增加 `<module>user</module>`，在 dependencies BOM 中管理内部 Starter 和 JAXB 版本。

- [x] **步骤 3：验证 Maven 模型**

运行 `./mvnw -pl components/user -am validate`，预期 reactor 包含 Base Starter 和 User Starter 且构建成功。

### 任务 2：实现用户参数与 TTL 上下文

**文件：**
- 新建：`backend/components/user/src/main/java/com/lzj/railway/framework/starter/user/core/UserInfoDTO.java`
- 新建：`backend/components/user/src/main/java/com/lzj/railway/framework/starter/user/core/UserContext.java`
- 测试：`backend/components/user/src/test/java/com/lzj/railway/framework/starter/user/core/UserContextTest.java`

- [x] **步骤 1：编写用户上下文测试**

覆盖当前线程绑定/读取/清理，以及使用 `TtlExecutors` 包装线程池后用户快照可传递到异步任务。

- [x] **步骤 2：实现不可变用户参数**

`UserInfoDTO` 保存 `userId`、`username`、`realName` 和当前原始 `token`。对象不可变，避免父子线程共享同一用户对象时被修改。

- [x] **步骤 3：实现 TTL 上下文**

`UserContext` 使用私有静态 `TransmittableThreadLocal<UserInfoDTO>`，提供 `setUser`、`getUser`、字段快捷读取和 `removeUser`。TTL 只负责受控线程池任务的上下文捕获与恢复，请求过滤器仍必须在 `finally` 中清理。

- [x] **步骤 4：运行上下文测试**

运行 `./mvnw -pl components/user -am -Dtest=UserContextTest -Dsurefire.failIfNoSpecifiedTests=false test`，预期测试通过。

### 任务 3：实现 JWT 生成与解析

**文件：**
- 新建：`backend/components/user/src/main/java/com/lzj/railway/framework/starter/user/config/UserProperties.java`
- 新建：`backend/components/user/src/main/java/com/lzj/railway/framework/starter/user/token/JwtTokenGenerator.java`
- 测试：`backend/components/user/src/test/java/com/lzj/railway/framework/starter/user/token/JwtTokenGeneratorTest.java`

- [x] **步骤 1：编写 JWT 契约测试**

覆盖生成后还原三个身份字段、原始 Token 回填、篡改 Token 拒绝、密钥过短拒绝，以及自动去除 `Bearer ` 前缀。

- [x] **步骤 2：定义配置属性**

使用 `railway.user.jwt.*` 配置 `secret`、`expiration`、`issuer`、`header-name` 和 `token-prefix`；默认 Header 为标准 `Authorization`，默认前缀为 `Bearer `。

- [x] **步骤 3：实现 Token 生成器**

使用 HS256 写入唯一 `jti`、签发方、签发时间、过期时间和用户 Claims；解析时校验签名、过期时间及签发方，返回 `UserInfoDTO`。密钥要求至少 32 个 UTF-8 字节。

- [x] **步骤 4：运行 JWT 测试**

预期所有 Token 测试通过，Java 17 下无 JAXB 类缺失错误。

### 任务 4：实现用户上下文过滤器与自动配置

**文件：**
- 新建：`backend/components/user/src/main/java/com/lzj/railway/framework/starter/user/filter/UserContextFilter.java`
- 新建：`backend/components/user/src/main/java/com/lzj/railway/framework/starter/user/config/UserAutoConfiguration.java`
- 新建：`backend/components/user/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`
- 新建：`backend/components/user/src/main/resources/META-INF/additional-spring-configuration-metadata.json`
- 测试：`backend/components/user/src/test/java/com/lzj/railway/framework/starter/user/filter/UserContextFilterTest.java`
- 测试：`backend/components/user/src/test/java/com/lzj/railway/framework/starter/user/config/UserAutoConfigurationTest.java`

- [x] **步骤 1：编写过滤器测试**

覆盖无 Token 直接放行、有效 Token 绑定上下文、请求完成后清理上下文、非法 Token 返回 401 且不继续过滤器链。

- [x] **步骤 2：实现过滤器**

继承 `OncePerRequestFilter`，读取配置 Header，调用 `JwtTokenGenerator` 校验并解析；使用 `try/finally` 清理上下文。实现 `Ordered` 并复用 `USER_TRANSMIT_FILTER_ORDER`。

- [x] **步骤 3：实现自动配置**

注册 `UserProperties`、`JwtTokenGenerator` 与 `UserContextFilter`，仅在配置了 `railway.user.jwt.secret` 时启用，允许业务服务通过同类型 Bean 覆盖默认实现。

- [x] **步骤 4：验证自动配置**

使用 `ApplicationContextRunner` 验证缺少密钥时不启用，配置有效密钥时 Bean 完整创建，自定义生成器可覆盖默认 Bean。

### 任务 5：同步文档并完整验证

**文件：**
- 修改：`README.md`
- 修改：`docs/architecture/project-structure.md`
- 修改：`docs/architecture/spring-boot-starter-structure.md`

- [x] **步骤 1：记录组件结构与使用配置**

说明 JWT Header 约定、配置样例、`UserContext` 生命周期、TTL 使用原因，以及异步执行器必须通过 `TtlExecutors` 包装或接入 TTL Agent。

- [x] **步骤 2：运行完整验证**

运行 `./mvnw clean test` 和 `git diff --check`。预期全部 reactor 模块、旧测试和 user 组件新测试通过，且无格式错误。
