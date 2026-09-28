# 后端组件库结构设计

> 本文描述总项目中 `backend/components` 子目录。仓库整体结构见 [项目 Monorepo 结构设计](./project-structure.md)。

## 1. 设计原则

组件库采用按能力纵向拆分的结构：每一种组件占一个独立目录、一个 Maven 模块和一个可发布的 JAR。组件自己的 API、自动配置、配置属性、资源和测试全部放在同一目录中。

顶层 `components/pom.xml` 是所有组件的父 POM 和聚合 POM，负责组件模块清单；它直接继承 `backend/parent/pom.xml`。

这种结构适合当前项目，因为每个组件都可以单独开发、测试、引用和发布，不需要为了修改一个 Redis 组件，同时在 core、autoconfigure、starter 三个横向目录之间跳转。

## 2. 当前阶段目录

```text
backend/components/
├── pom.xml
├── base/
│   ├── pom.xml
│   └── src/
├── common/
│   ├── pom.xml
│   └── src/
├── convention/
│   ├── pom.xml
│   └── src/
├── designpattern/
│   ├── pom.xml
│   └── src/
└── user/
    ├── pom.xml
    └── src/
```

当前已经创建 `base` Starter、`common`、纯 Java 的 `convention` 与 `designpattern` 组件，以及 `user` Starter。后续确定要开发其他组件时，再新增对应目录、POM、源码与测试，并把它加入总 POM。

`common` 是普通 JAR，artifactId 为 `railway-common`，依赖 `convention` 提供一致的客户端参数异常，并提供：

- `CodeEnum`、`DeleteEnum`、`FlagEnum`、`OperationTypeEnum` 和 `StatusEnum`：稳定的通用整数码值；
- `Assert`：失败时抛出 `ClientException` 的参数断言；
- `BeanUtil`：使用 Spring `BeanUtils` 复制同名 JavaBean 属性；
- `EnvironmentUtil`：基于调用方传入的 Spring `Environment` 判断活动 Profile；
- `ThreadUtil`：命名线程工厂和保留中断状态的休眠方法。

该模块不包含自动配置，也不保存全局应用上下文或全局线程池。

`convention` 只提供错误码、异常、分页和响应契约，不包含自动配置，因此 artifactId 使用 `railway-convention`，不使用 `spring-boot-starter` 后缀。它不依赖 Web、Spring 或 MyBatis-Plus；业务服务负责在边界层完成框架对象与规约对象之间的转换。

`designpattern` 也是纯 Java JAR，artifactId 为 `railway-design-pattern`，提供：

- `Builder<T>`：定义 `build()` 构建契约，具体对象在所属模块实现专用 Builder；
- `ResponsibilityChain`：按处理器顺序稳定执行，通过 `CONTINUE` 或 `STOP` 显式控制链路；
- `StrategySelector<REQUEST, RESPONSE>`：按唯一业务标识执行同一请求、响应类型的策略。

该模块不扫描 Spring Bean。需要容器集成时，由业务服务注入具有明确泛型的处理器或策略列表后完成组装。

`user` 提供以下能力：

- `JwtTokenGenerator`：使用 HS256 生成和校验登录凭证，校验签名、过期时间与签发方；
- `UserInfoDTO`：不可变的当前用户参数，包含用户 ID、用户名、真实姓名和原始 Token；
- `UserContext`：基于 Alibaba TransmittableThreadLocal 保存当前执行上下文的用户；
- `UserContextFilter`：从配置的 HTTP Header 解析 JWT，在请求期间绑定用户，并在请求结束后清理；
- `UserAutoConfiguration`：配置 `railway.user.jwt.secret` 后自动注册生成器，并且只在 Servlet Web 应用中注册过滤器。

过滤器默认只信任 `Authorization: Bearer <token>` 中经过签名校验的 Claims，不直接信任客户端提交的独立 `userId`、`username` 或 `realName` Header。若未来由网关解析 JWT 并传递用户 Header，必须同时建立网关到微服务的可信网络边界、清除外部同名 Header，并另行设计内部请求签名。

TransmittableThreadLocal 解决的是线程池复用时普通 `InheritableThreadLocal` 无法按任务传播的问题。TTL 在任务提交时捕获上下文、执行前安装、执行后恢复；执行器必须通过 `TtlExecutors` 包装或由 TTL Agent 增强。它不能替代生命周期清理，因此过滤器始终在 `finally` 中调用 `UserContext.removeUser()`，防止容器线程复用时串号。

## 3. 总 POM

`backend/components/pom.xml` 同时承担组件父 POM 和聚合 POM 职责：

```xml
<packaging>pom</packaging>

<modules>
    <module>web</module>
    <module>persistence</module>
    <module>redis</module>
    <module>security</module>
    <module>observability</module>
</modules>
```

它负责：

- 聚合实际存在的组件模块；
- 作为所有组件模块的直接父 POM；
- 声明组件共同需要、但与版本无关的构建约定；
- 从 `backend/parent/pom.xml` 继承构建插件，并通过 parent 导入统一依赖版本。

Spring Boot、第三方库和组件自身的版本不写在这里，统一写入 `backend/dependencies/pom.xml`。

没有实际代码的组件不能提前写入 `<modules>`。新增组件时，一次提交中同时新增组件目录和总 POM 的 `<module>` 条目。

## 4. 后续新增单个组件时

简单组件不强制拆成三个 Maven 模块。以未来可能增加的 `redis` 为例，它自身就是完整的 Starter；下面只是后续结构约定，不在当前阶段创建：

```text
redis/
├── pom.xml
└── src/
    ├── main/java/com/example/components/redis/
    │   ├── api/
    │   ├── autoconfigure/
    │   │   ├── RedisComponentAutoConfiguration.java
    │   │   └── RedisComponentProperties.java
    │   └── support/
    ├── main/resources/META-INF/spring/
    │   └── org.springframework.boot.autoconfigure.AutoConfiguration.imports
    └── test/java/com/example/components/redis/
        └── RedisComponentAutoConfigurationTests.java
```

推荐 Maven 坐标：

```xml
<groupId>com.example.components</groupId>
<artifactId>redis-spring-boot-starter</artifactId>
```

目录名保持简短，artifactId 明确表达这是一个 Starter。

## 5. 组件内部包结构

每个组件内部按职责分包，但不跨组件建立全局 `controller/service/util`：

- `api`：允许微服务直接调用的公开类型、接口和注解。
- `autoconfigure`：`@AutoConfiguration`、配置属性、条件装配。
- `support`：组件内部实现，默认不作为稳定 API 承诺。

当组件只有少量类时，可以直接放在组件根包下，不必机械创建空包。

自动配置仍遵循 Spring Boot 约定：

1. 使用 `@AutoConfiguration`。
2. 使用 `@ConditionalOnClass` 判断依赖是否存在。
3. 使用 `@ConditionalOnMissingBean` 允许业务微服务覆盖默认实现。
4. 在 `AutoConfiguration.imports` 中显式注册。
5. 使用独立配置前缀，例如 `project.components.redis.*`。
6. 使用 `ApplicationContextRunner` 测试启用、禁用、覆盖和缺少依赖场景。

## 6. 组件依赖规则

组件默认互不依赖：

```text
xxx-service
├── web-spring-boot-starter
├── persistence-spring-boot-starter
├── redis-spring-boot-starter
└── security-spring-boot-starter
```

如果 `security` 的实现必须依赖 `web`，可以在 `security/pom.xml` 中显式依赖 web 组件，但要保持依赖方向单向并防止循环。

不要创建无明确职责的 `common` 或 `utils` 组件。只有至少两个组件确实需要共享稳定契约时，才新增类似 `foundation` 的基础模块；其中只能保存框架无关类型，不能成为任意代码的堆放处。

## 7. 统一依赖版本管理

`backend/dependencies/pom.xml` 是纯 BOM，只管理依赖版本；`backend/parent/pom.xml` 是仓库内 Java 模块的父 POM，导入该 BOM 并管理构建插件。发布后，其他仓库可以只 import dependencies BOM：

```xml
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>com.example.components</groupId>
            <artifactId>web-spring-boot-starter</artifactId>
            <version>${project.version}</version>
        </dependency>
        <dependency>
            <groupId>com.example.components</groupId>
            <artifactId>redis-spring-boot-starter</artifactId>
            <version>${project.version}</version>
        </dependency>
    </dependencies>
</dependencyManagement>
```

dependencies BOM 不包含 Java 源码、运行时依赖或插件配置，只保存依赖版本属性与 `<dependencyManagement>`。依赖版本在 dependencies 中升级，Java 与 Maven 插件版本在 parent 中升级。

## 8. 复杂组件的升级路径

只有单个组件出现以下情况时，才在该组件目录内部进一步拆分：

- 存在多个互斥实现；
- 自动配置需要支持大量可选依赖；
- 核心 API 需要脱离 Spring 单独使用；
- 各部分需要独立发布。

例如 Redis 组件变复杂后，可以演进为：

```text
redis/
├── pom.xml
├── redis-core/
├── redis-spring-boot-autoconfigure/
└── redis-spring-boot-starter/
```

这时 `redis/pom.xml` 聚合该组件的子模块，而 `components/pom.xml` 仍然只聚合 `redis`。复杂性被限制在组件自己的目录内。

## 9. 命名约定

| 位置 | 示例 |
| --- | --- |
| 目录 | `redis` |
| artifactId | `redis-spring-boot-starter` |
| Java 包 | `com.example.components.redis` |
| 配置前缀 | `project.components.redis` |
| 自动配置类 | `RedisComponentAutoConfiguration` |
| 测试类 | `RedisComponentAutoConfigurationTests` |

第三方 Starter 不使用 `spring-boot-*` 作为 artifactId 前缀，避免与 Spring 官方模块混淆。

## 10. 社区依据

Spring Boot 官方允许简单 Starter 将自动配置与依赖描述合并在同一个模块中；只有存在多种可选能力、需要单独消费自动配置等情况时，才需要拆分 autoconfigure 和 starter。这正适合当前按组件独立目录、逐步演进的设计。

- https://docs.spring.io/spring-boot/reference/features/developing-auto-configuration.html
- https://github.com/spring-projects/spring-boot/tree/main/starter
