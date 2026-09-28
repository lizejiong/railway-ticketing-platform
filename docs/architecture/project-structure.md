# 项目 Monorepo 结构设计

## 1. 项目边界

整个仓库包含三个可以独立构建的主体：

1. `backend/components`：后端组件库，提供框架无关能力和 Spring Boot Starter。
2. `backend/services/xxx-service`：后端微服务，承载业务用例并消费组件库。
3. `frontend/web`：前端应用，只通过 HTTP API 与微服务通信。

推荐放在同一个 Monorepo 中，统一文档、CI、开发环境和版本提交，但不把 Java 与前端构建工具强行混在一起。

## 2. 推荐目录

```text
12306/
├── .github/
│   └── workflows/
│       ├── backend.yml
│       └── frontend.yml
├── backend/
│   ├── pom.xml
│   ├── dependencies/
│   │   └── pom.xml
│   ├── parent/
│   │   └── pom.xml
│   ├── components/
│   │   ├── pom.xml
│   │   ├── base/
│   │   │   └── pom.xml
│   │   ├── common/
│   │   │   └── pom.xml
│   │   ├── convention/
│   │   │   └── pom.xml
│   │   ├── designpattern/
│   │   │   └── pom.xml
│   │   ├── idgenerator/
│   │   │   └── pom.xml
│   │   ├── log/
│   │   │   └── pom.xml
│   │   ├── persistence/
│   │   │   └── pom.xml
│   │   ├── user/
│   │   │   └── pom.xml
│   │   └── web/
│   │       └── pom.xml
│   └── services/
│       └── xxx-service/
│           └── pom.xml
├── frontend/
│   └── web/
├── deploy/
│   ├── compose.yaml
│   ├── docker/
│   │   ├── backend.Dockerfile
│   │   └── frontend.Dockerfile
│   └── nginx/
│       └── default.conf
├── docs/
│   ├── architecture/
│   ├── api/
│   └── development/
├── scripts/
├── .editorconfig
├── .gitignore
└── README.md
```

当前阶段只创建聚合目录与 POM，不预先填充组件源码、业务源码、资源文件或测试文件。`xxx-service` 是服务目录占位示例，实际创建服务模块时替换为明确的业务名称。

## 3. 后端构建边界

`backend/pom.xml` 是所有 Java 模块的聚合入口，只声明 `<modules>`，不同时承担依赖版本管理：

```text
backend/pom.xml
├── dependencies
├── parent
├── components
└── services/xxx-service
```

`backend/dependencies/pom.xml` 是统一依赖版本管理文件，使用 `pom` packaging，通过 `dependencyManagement` 管理：

- Spring Boot BOM；
- 数据库、缓存、JSON、日志、测试等第三方依赖版本；
- 本仓库各个组件 Starter 的版本。

它是独立的纯 BOM，不作为仓库内模块的父 POM，也不保存构建插件配置。核心结构如下：

```xml
<groupId>com.example</groupId>
<artifactId>backend-dependencies</artifactId>
<version>0.1.0-SNAPSHOT</version>
<packaging>pom</packaging>

<properties>
    <spring-boot.version>3.0.7</spring-boot.version>
    <!-- 其他第三方依赖版本统一放在这里 -->
</properties>

<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-dependencies</artifactId>
            <version>${spring-boot.version}</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
        <!-- 数据库、缓存、工具库及内部 Starter -->
    </dependencies>
</dependencyManagement>
```

`backend/parent/pom.xml` 是仓库内 Java 模块的统一父 POM。它导入 dependencies BOM，并通过属性和 `pluginManagement` 管理 Java 版本、编码、编译、测试与代码检查等构建约定。

`backend/components/pom.xml` 继承 parent POM，同时作为组件库的总聚合 POM。业务服务也直接继承 parent POM。每一种组件占一个独立目录和 Maven 模块，组件自己的 API、自动配置、依赖与测试放在一起。纯契约组件不强制依赖 Spring Boot，详细设计见 [后端组件库结构](./spring-boot-starter-structure.md)。

当前 `user` Starter 单向依赖 `base`，复用用户字段和过滤器顺序常量；业务服务依赖 `user`，组件不反向依赖任何业务服务。

`designpattern` 是不依赖 Spring 的纯 Java 模块，提供构建者、责任链和策略选择器。业务服务负责按自身泛型类型组装处理器和策略实现，避免公共组件通过原始类型扫描全部 Bean 而失去编译期类型检查。

`log` 是基于 Spring AOP 的方法调用日志 Starter。业务方法通过 `@ILog` 显式选择记录范围，切面统一记录参数、返回值、执行耗时和异常，并提供全局开关与内容长度限制。

`persistence` 是 MyBatis-Plus Starter，依赖 `convention` 的分页契约和 `idgenerator` 的统一雪花算法。它提供 MySQL 分页插件、`BaseDO`、元数据自动填充、分页转换工具，并在存在工作节点分配器时替换 MyBatis-Plus 主键生成器。

`web` 是 Servlet Web Starter，复用 `convention` 的响应与异常契约，提供 `Results` 快捷响应构造和全局异常处理器。

`backend/services/xxx-service` 后续会成为可运行的 Spring Boot 应用。它按需依赖具体组件，但组件库绝不能反向依赖微服务。

## 4. 业务服务内部结构

当正式开始实现某个业务服务时，采用下面的分层目录。当前阶段不创建这些源码和资源文件：

```text
xxx-service/
├── pom.xml
└── src/
    ├── main/
    │   ├── java/.../biz/xxxservice/
    │   │   ├── controller/
    │   │   ├── service/
    │   │   ├── dao/
    │   │   │   ├── entity/
    │   │   │   └── mapper/
    │   │   ├── dto/
    │   │   ├── remote/
    │   │   ├── mq/
    │   │   ├── config/
    │   │   └── XxxApplication.java
    │   └── resources/
    │       ├── application.yaml
    │       ├── mapper/
    │       ├── shardingsphere-config.yaml
    │       └── lua/
    └── test/
```

目录职责：

- `controller`：HTTP 接口层。
- `service`：业务接口与实现。
- `dao/entity`：数据库实体（DO）。
- `dao/mapper`：MyBatis 数据访问接口。
- `dto`：接口请求和响应对象。
- `remote`：调用其他微服务的客户端。
- `mq`：RocketMQ 消息生产与消费。
- `config`：当前服务的配置类。
- `resources/mapper`：MyBatis XML SQL。
- `shardingsphere-config.yaml`：仅分库分表服务创建。
- `resources/lua`：仅需要 Redis Lua 脚本的服务创建。

这些目录按实际需要逐步创建，不为暂时没有的功能保留空目录。

## 5. 前端结构

当前只保留 `frontend/web` 项目边界，不创建 `package.json`、`src`、构建配置或页面目录。前端框架、包管理器、路由、状态管理、测试方案和目录范式在开始前端开发时再单独确认。

## 6. 部署与本地开发

`deploy/compose.yaml` 编排本地完整环境，包括后端、前端、数据库及真实需要的中间件。Dockerfile 与 Nginx 配置集中在 `deploy`，避免散落在业务源码中。

根目录脚本只负责跨项目动作，例如：

- 同时启动前后端开发环境；
- 执行完整检查；
- 初始化本地依赖；
- 生成或校验 API 客户端。

后端单独构建仍使用 `backend/mvnw`，前端单独构建仍使用自己的包管理器命令。

## 7. 依赖方向

```text
frontend/web
    │ HTTP/JSON
    ▼
backend/services/xxx-service
    │ Maven dependency
    ▼
backend/components/<component>
    └── <component>-spring-boot-starter

所有 Java 模块
    │ inherits
    ▼
backend/parent/pom.xml
    │ imports
    ▼
backend/dependencies/pom.xml
```

基础设施配置可以引用构建产物，但源码模块不得依赖 `deploy`。前端不得通过复制 Java 模型共享类型，API 类型应由 OpenAPI 等契约生成或独立维护。

## 8. 第一阶段范围

首轮只创建能够表达边界的最小骨架：

- 后端聚合 POM；
- 纯 dependencies BOM；
- 统一 parent POM；
- 组件聚合 POM；
- 已经确定名称的业务服务 POM。

暂不创建组件实现、业务类、Mapper、配置文件、前端代码、部署文件或 CI。后续每确定一个组件或服务，再单独设计和实现。
