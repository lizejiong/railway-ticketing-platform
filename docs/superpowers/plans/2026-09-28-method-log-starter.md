# 方法调用日志 Starter 实施计划

> **给 agentic workers：** 使用 `superpowers:executing-plans` 按任务逐项执行本计划，并用 checkbox 跟踪状态。

**目标：** 新增一个基于 Spring AOP 的日志 Starter，业务方法使用 `@ILog` 后统一记录方法标识、入参、返回值、执行耗时和异常。

**架构：** `@ILog` 是业务侧公开契约，`ILogAspect` 只拦截带注解的 Spring Bean 方法，Fastjson2 负责将参数和结果格式化为 JSON。`LogAutoConfiguration` 通过 Spring Boot 3 自动配置注册切面，支持全局关闭和内容长度限制，并允许业务服务提供自己的切面 Bean 覆盖默认实现。

**技术栈：** Java 17、Spring Boot 3.0.7、Spring AOP/AspectJ、SLF4J、Fastjson2、JUnit 5、AssertJ、Logback。

---

### 任务 1：注册日志组件模块和依赖

**文件：**
- 修改：`backend/components/pom.xml`
- 修改：`backend/dependencies/pom.xml`
- 新建：`backend/components/log/pom.xml`

- [ ] 在组件聚合 POM 中加入 `log` 模块。
- [ ] 在 BOM 中管理 `railway-log-spring-boot-starter` 和 `com.alibaba.fastjson2:fastjson2`。
- [ ] 日志模块依赖 `spring-boot-starter-aop`、Fastjson2 和测试 Starter。
- [ ] 执行 `backend/mvnw.cmd -pl components/log -am -DskipTests compile`，预期模块能够解析并编译。

### 任务 2：定义 `@ILog` 与配置属性

**文件：**
- 新建：`backend/components/log/src/main/java/com/lzj/railway/framework/starter/log/annotation/ILog.java`
- 新建：`backend/components/log/src/main/java/com/lzj/railway/framework/starter/log/config/LogProperties.java`
- 测试：`backend/components/log/src/test/java/com/lzj/railway/framework/starter/log/annotation/ILogTest.java`

- [ ] 定义仅作用于方法、运行时保留且可生成文档的 `@ILog`。
- [ ] 注解提供 `value`、`recordArgs`、`recordResult`，后两项默认开启。
- [ ] 定义 `railway.log.enabled=true` 和 `railway.log.max-content-length=4096`。
- [ ] 测试注解默认值和属性绑定默认值。

### 任务 3：实现方法调用日志切面

**文件：**
- 新建：`backend/components/log/src/main/java/com/lzj/railway/framework/starter/log/aspect/ILogAspect.java`
- 测试：`backend/components/log/src/test/java/com/lzj/railway/framework/starter/log/aspect/ILogAspectTest.java`

- [ ] 使用 `@Around("@annotation(iLog)")` 只拦截显式标注的方法。
- [ ] 使用 `System.nanoTime()` 计算耗时，避免系统时间校准影响持续时间。
- [ ] 成功时记录业务描述、`类名#方法名`、参数 JSON、返回值 JSON 和毫秒耗时。
- [ ] 异常时记录参数、毫秒耗时和异常堆栈，然后原样重新抛出。
- [ ] `recordArgs=false` 或 `recordResult=false` 时输出 `<disabled>`，避免敏感字段进入日志。
- [ ] Fastjson2 格式化失败时回退到 `String.valueOf`；内容超过配置长度时截断，日志功能不得改变业务结果。
- [ ] 使用 `AspectJProxyFactory` 测试成功、异常、关闭参数/结果和截断行为。

### 任务 4：实现 Spring Boot 自动配置

**文件：**
- 新建：`backend/components/log/src/main/java/com/lzj/railway/framework/starter/log/config/LogAutoConfiguration.java`
- 新建：`backend/components/log/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`
- 测试：`backend/components/log/src/test/java/com/lzj/railway/framework/starter/log/config/LogAutoConfigurationTest.java`

- [ ] 使用 `@AutoConfiguration`、`@ConditionalOnClass(Aspect.class)` 与 `@EnableConfigurationProperties`。
- [ ] `railway.log.enabled=false` 时不注册切面。
- [ ] 使用 `@ConditionalOnMissingBean(ILogAspect.class)` 允许业务覆盖。
- [ ] 使用 `ApplicationContextRunner` 验证默认装配、全局关闭和业务覆盖。

### 任务 5：补充使用文档并回归验证

**文件：**
- 修改：`README.md`
- 修改：`docs/architecture/project-structure.md`
- 修改：`docs/architecture/spring-boot-starter-structure.md`

- [ ] 在组件目录和说明中加入 `log` Starter。
- [ ] 给出 `@ILog("查询车次")`、关闭入参/返回值记录和全局配置示例。
- [ ] 说明只有经过 Spring 代理的外部方法调用会被拦截，自调用不会触发 Spring AOP。
- [ ] 执行 `backend/mvnw.cmd -pl components/log -am test`，预期日志模块及依赖模块测试全部通过。
- [ ] 执行 `backend/mvnw.cmd test`，预期后端全量测试通过。
- [ ] 执行 `git diff --check`，预期没有空白符错误。
