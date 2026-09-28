# Web 组件 Starter 实施计划

> **给 agentic workers：** 使用 `superpowers:executing-plans` 按任务逐项执行本计划，并用 checkbox 跟踪状态。

**目标：** 新增 Web Starter，提供统一异常响应和 `Results` 快捷构造器。

**架构：** Web Starter 复用 `railway-convention` 的 `Result`、错误码和异常体系。`GlobalExceptionHandler` 统一处理参数异常、`AbstractException` 和未知异常；`Results` 负责构造响应并补齐请求 ID。自动配置仅在 Servlet Web 应用中装配。

**技术栈：** Java 17、Spring Boot 3.0.7、Spring MVC、JUnit 5、MockMvc。

---

### 任务 1：注册 Web Starter 模块

**文件：**
- 修改：`backend/components/pom.xml`
- 修改：`backend/dependencies/pom.xml`
- 新建：`backend/components/web/pom.xml`

- [ ] 将 `web` 加入组件聚合 POM。
- [ ] 在统一 BOM 中管理 `railway-web-spring-boot-starter`。
- [ ] Web 模块依赖 `railway-convention`、`spring-boot-starter-web` 和测试 Starter。
- [ ] 执行 `backend/mvnw.cmd -pl components/web -am -DskipTests compile`，预期编译成功。

### 任务 2：实现 `Results` 快捷构造器

**文件：**
- 新建：`backend/components/web/src/main/java/com/lzj/railway/framework/starter/web/result/Results.java`
- 测试：`backend/components/web/src/test/java/com/lzj/railway/framework/starter/web/result/ResultsTest.java`

- [ ] 提供无数据和带数据的 `success` 方法。
- [ ] 提供基于 `ErrorCode` 和 `code/message` 的 `failure` 方法。
- [ ] 每个快捷方法都补齐请求 ID；调用者传入非空请求 ID 时沿用，否则生成无连字符 UUID。
- [ ] 测试成功码、失败码、数据、指定请求 ID 和自动请求 ID。

### 任务 3：实现全局异常处理器

**文件：**
- 新建：`backend/components/web/src/main/java/com/lzj/railway/framework/starter/web/handler/GlobalExceptionHandler.java`
- 测试：`backend/components/web/src/test/java/com/lzj/railway/framework/starter/web/handler/GlobalExceptionHandlerTest.java`

- [ ] 将参数校验、绑定、类型不匹配和请求体不可读异常统一映射为客户端错误。
- [ ] 通过一个处理器捕获所有 `AbstractException` 子类，并保留异常携带的错误码和消息。
- [ ] 将未知异常映射为通用服务端错误，不向客户端泄漏异常详情，同时在服务端保留堆栈。
- [ ] 使用 standalone MockMvc 验证三类异常的统一 JSON 响应。

### 任务 4：实现自动配置

**文件：**
- 新建：`backend/components/web/src/main/java/com/lzj/railway/framework/starter/web/config/WebAutoConfiguration.java`
- 新建：`backend/components/web/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`
- 测试：`backend/components/web/src/test/java/com/lzj/railway/framework/starter/web/config/WebAutoConfigurationTest.java`

- [ ] 自动配置仅在 Servlet Web 应用中生效。
- [ ] 默认注册全局异常处理器，并在业务提供同类型 Bean 时退让。
- [ ] 使用 `WebApplicationContextRunner` 验证默认装配、非 Web 环境和 Bean 覆盖。

### 任务 5：文档与完整验证

**文件：**
- 修改：`README.md`
- 修改：`docs/architecture/project-structure.md`
- 修改：`docs/architecture/spring-boot-starter-structure.md`

- [ ] 补充 Web Starter 目录、异常映射和 `Results` 使用示例。
- [ ] 执行 `backend/mvnw.cmd -pl components/web -am test`。
- [ ] 执行 `backend/mvnw.cmd test`，预期全部模块通过。
- [ ] 执行 `git diff --check`，预期无空白错误。
