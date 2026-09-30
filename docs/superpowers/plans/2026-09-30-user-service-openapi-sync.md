# User Service OpenAPI 自动同步实施计划

> **给 agentic workers：** 必须使用 `superpowers:executing-plans` 按任务逐项执行本计划；步骤使用 checkbox（`- [ ]`）语法跟踪状态。
**目标：** 让 `user-service` 从 Controller 和 DTO 自动生成 OpenAPI 文档，并供 Apifox 通过 URL 定时同步。

**架构：** 在依赖管理模块固定与 Spring Boot 3.0.7 兼容的 Springdoc 2.1.0，在 `user-service` 引入仅提供 OpenAPI 文档端点的 WebMVC starter。运行中的服务暴露 `/v3/api-docs.yaml`，Apifox 以此 URL 为唯一导入源；Controller 和 DTO 上的 Swagger OpenAPI 注解补充业务语义与示例。手工维护的导入 YAML 在端点验证及 Apifox 切换 URL 导入后删除。

**技术栈：** Spring Boot 3.0.7、Spring MVC、Springdoc OpenAPI 2.1.0、Swagger OpenAPI 3 注解、Apifox URL / 定时导入。

---

### 任务 1：引入 Springdoc 并建立文档元数据

**文件：**
- 修改：`backend/dependencies/pom.xml`
- 修改：`backend/services/user-service/pom.xml`
- 新建：`backend/services/user-service/src/main/java/com/lzj/railway/user/config/OpenApiConfiguration.java`

- [ ] **步骤 1：在依赖管理模块添加与 Spring Boot 3.0.x 匹配的 Springdoc 版本和依赖声明。**

在 `<properties>` 中加入：

```xml
<springdoc-openapi.version>2.1.0</springdoc-openapi.version>
```

在 `<dependencyManagement><dependencies>` 中加入：

```xml
<dependency>
    <groupId>org.springdoc</groupId>
    <artifactId>springdoc-openapi-starter-webmvc-api</artifactId>
    <version>${springdoc-openapi.version}</version>
</dependency>
```

- [ ] **步骤 2：在 user-service 引入自动生成 OpenAPI JSON/YAML 端点的 starter。**

在 `backend/services/user-service/pom.xml` 的 `<dependencies>` 中加入：

```xml
<dependency>
    <groupId>org.springdoc</groupId>
    <artifactId>springdoc-openapi-starter-webmvc-api</artifactId>
</dependency>
```

- [ ] **步骤 3：创建服务级 OpenAPI 元数据配置。**

```java
package com.lzj.railway.user.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import org.springframework.context.annotation.Configuration;

/**
 * 定义用户服务自动生成的 OpenAPI 文档基础信息。
 */
@Configuration
@OpenAPIDefinition(info = @Info(
        title = "12306 铁路平台 - 用户服务",
        version = "0.1.0",
        description = "用户注册、登录与后续用户域接口的 OpenAPI 定义。"))
public class OpenApiConfiguration {
}
```

- [ ] **步骤 4：编译 user-service 及其依赖模块。**

Run: `backend\\mvnw.cmd -pl services/user-service -am -DskipTests compile`

Expected: Maven Reactor 中 `User Service` 显示 `SUCCESS`。

### 任务 2：标注文档中的认证接口与数据结构

**文件：**
- 修改：`backend/services/user-service/src/main/java/com/lzj/railway/user/controller/UserAuthController.java`
- 修改：`backend/services/user-service/src/main/java/com/lzj/railway/user/dto/request/RegisterRequest.java`
- 修改：`backend/services/user-service/src/main/java/com/lzj/railway/user/dto/request/LoginRequest.java`
- 修改：`backend/services/user-service/src/main/java/com/lzj/railway/user/dto/response/RegisterResponse.java`
- 修改：`backend/services/user-service/src/main/java/com/lzj/railway/user/dto/response/LoginResponse.java`

- [ ] **步骤 1：在 UserAuthController 添加认证分组与操作说明。**

在类上添加：

```java
@Tag(name = "用户认证", description = "账号注册与登录")
```

并分别在两个方法上添加：

```java
@Operation(summary = "账号注册", description = "校验用户名、手机号和邮箱唯一性后创建用户账号。")
```

```java
@Operation(summary = "账号登录", description = "支持用户名、手机号或邮箱加密码登录，并签发 Access Token 与 Refresh Token。")
```

- [ ] **步骤 2：在 RegisterRequest 与 LoginRequest 的字段添加 `@Schema` 说明和示例。**

注册字段使用如下语义：

```java
@Schema(description = "4-32 位字母、数字或下划线", example = "railway_user_01")
private String username;

@Schema(description = "8-72 位且同时包含字母和数字", example = "Railway123")
private String password;

@Schema(description = "中国大陆手机号", example = "13800138000")
private String phone;

@Schema(description = "邮箱地址", example = "railway_user_01@example.com")
private String email;
```

登录 DTO 对 `account` 使用 `railway_user_01` 示例并说明可传用户名、手机号或邮箱；对 `password` 使用 `Railway123` 示例。

- [ ] **步骤 3：在响应 record 及嵌套 UserSummary 上添加 `@Schema` 描述。**

`LoginResponse` 的令牌字段说明 Access Token、Refresh Token 和秒级有效期；`UserSummary.realName` 标注为未实名时可能为 `null`。`RegisterResponse` 的 `userId`、`username`、`phone`、`email` 分别标注文档字段含义。

- [ ] **步骤 4：编译并运行现有用户认证单元测试。**

Run: `backend\\mvnw.cmd -pl services/user-service -am test -Dtest=UserAuthServiceImplTest -Dsurefire.failIfNoSpecifiedTests=false`

Expected: `UserAuthServiceImplTest` 的两个测试均通过。

### 任务 3：验证运行时文档、切换 Apifox 数据源并清理临时文件

**文件：**
- 删除：`docs/12306-user-service.openapi.yaml`

- [ ] **步骤 1：启动 user-service 并请求自动生成的 YAML。**

Run: `Invoke-WebRequest http://127.0.0.1:8081/v3/api-docs.yaml -UseBasicParsing`

Expected: 返回内容包含：

```yaml
/api/user/register:
/api/user/login:
```

- [ ] **步骤 2：在 Apifox 将项目导入源切换为运行时 OpenAPI URL。**

导入地址必须为：

```text
http://127.0.0.1:8081/v3/api-docs.yaml
```

在 Apifox 的“项目设置 → 导入数据 → 定时导入”创建该 URL 数据源，并将目标目录设为“用户认证”。

- [ ] **步骤 3：确认 Apifox 中出现两个接口后，删除一次性手工文件。**

删除：`docs/12306-user-service.openapi.yaml`

- [ ] **步骤 4：检查变更并提交。**

Run: `git diff --check`

Expected: 无输出。

```bash
git add backend/dependencies/pom.xml backend/services/user-service/pom.xml \\
  backend/services/user-service/src/main/java/com/lzj/railway/user/config/OpenApiConfiguration.java \\
  backend/services/user-service/src/main/java/com/lzj/railway/user/controller/UserAuthController.java \\
  backend/services/user-service/src/main/java/com/lzj/railway/user/dto
git commit -m "feat: generate user service OpenAPI documentation"
```
