# 铁路微服务模块骨架实施计划

> **给 agentic workers：** 使用 `superpowers:executing-plans` 逐项执行，并用 `- [ ]` 跟踪状态。

**目标：** 只创建 gateway、user、ticket、order、pay 五个服务的 Maven 模块边界，以及 Spring Cloud Alibaba 所需的统一依赖管理；不添加任何业务代码、启动类、配置文件、路由或接口。

**架构：** `backend/services` 继承已有 parent POM 并聚合五个服务。所有服务预置 Nacos Discovery 与 Nacos Config 依赖；gateway 额外预置 Spring Cloud Gateway。Nacos 地址、命名空间、路由与业务配置将在服务实现阶段再单独确定，避免把本地或生产环境参数写死在骨架中。

**技术栈：** Java 17、Spring Boot 3.0.7、Spring Cloud 2022.0.3、Spring Cloud Alibaba 2022.0.0.0-RC2、Nacos、Spring Cloud Gateway、Maven。

---

### 任务 1：接入 Spring Cloud 与 Spring Cloud Alibaba BOM

**文件：**
- 修改：`backend/dependencies/pom.xml`

- [x] **步骤 1：导入两个 Spring Cloud BOM**

在已有 Spring Boot BOM 之后依次添加：

```xml
<dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-dependencies</artifactId>
    <version>${spring-cloud.version}</version>
    <type>pom</type>
    <scope>import</scope>
</dependency>
<dependency>
    <groupId>com.alibaba.cloud</groupId>
    <artifactId>spring-cloud-alibaba-dependencies</artifactId>
    <version>${spring-cloud-alibaba.version}</version>
    <type>pom</type>
    <scope>import</scope>
</dependency>
```

- [x] **步骤 2：验证 BOM 可解析**

Run: `./backend/mvnw.cmd -f backend/dependencies/pom.xml validate`

Expected: `BUILD SUCCESS`。

### 任务 2：创建服务聚合模块

**文件：**
- 修改：`backend/pom.xml`
- 新建：`backend/services/pom.xml`

- [x] **步骤 1：注册服务聚合**

在 `backend/pom.xml` 的 `<modules>` 中增加：

```xml
<module>services</module>
```

- [x] **步骤 2：创建 `backend/services/pom.xml`**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>com.lzj.railway</groupId>
        <artifactId>railway-parent</artifactId>
        <version>0.1.0-SNAPSHOT</version>
        <relativePath>../parent/pom.xml</relativePath>
    </parent>
    <artifactId>railway-services</artifactId>
    <packaging>pom</packaging>
    <modules>
        <module>gateway-service</module>
        <module>user-service</module>
        <module>ticket-service</module>
        <module>order-service</module>
        <module>pay-service</module>
    </modules>
</project>
```

### 任务 3：创建五个服务 POM

**文件：**
- 新建：`backend/services/gateway-service/pom.xml`
- 新建：`backend/services/user-service/pom.xml`
- 新建：`backend/services/ticket-service/pom.xml`
- 新建：`backend/services/order-service/pom.xml`
- 新建：`backend/services/pay-service/pom.xml`

- [x] **步骤 1：创建 `gateway-service` POM**

继承 `railway-services`，`artifactId` 为 `gateway-service`，仅声明以下依赖：

```xml
<dependency><groupId>org.springframework.cloud</groupId><artifactId>spring-cloud-starter-gateway</artifactId></dependency>
<dependency><groupId>com.alibaba.cloud</groupId><artifactId>spring-cloud-starter-alibaba-nacos-discovery</artifactId></dependency>
<dependency><groupId>com.alibaba.cloud</groupId><artifactId>spring-cloud-starter-alibaba-nacos-config</artifactId></dependency>
```

- [x] **步骤 2：创建四个业务服务 POM**

每个 POM 继承 `railway-services`，并只声明：

```xml
<dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-web</artifactId></dependency>
<dependency><groupId>com.alibaba.cloud</groupId><artifactId>spring-cloud-starter-alibaba-nacos-discovery</artifactId></dependency>
<dependency><groupId>com.alibaba.cloud</groupId><artifactId>spring-cloud-starter-alibaba-nacos-config</artifactId></dependency>
```

除 `artifactId` 外，四个 POM 不做领域差异化；公共 Starter、数据库、缓存、消息队列、支付 SDK 和测试依赖均推迟至逐服务实现阶段。

### 任务 4：验证模块骨架

**文件：**
- 修改：`README.md`

- [x] **步骤 1：更新 README 项目结构**

在后端目录树中列出 `services/` 以及五个服务 POM，明确当前阶段只建立模块边界，未包含 Java 源码、应用配置、Nacos 地址或业务能力。

- [x] **步骤 2：执行全量 Maven 校验**

Run: `./backend/mvnw.cmd -f backend/pom.xml validate`

Expected: `BUILD SUCCESS`。

Run: `git diff --check`

Expected: 无输出且退出码为 0。
