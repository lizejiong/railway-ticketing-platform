# 公共组件库实施计划

> **给 agentic workers：** 必须使用 `superpowers:executing-plans` 按任务逐项执行本计划。步骤使用 checkbox（`- [ ]`）语法跟踪状态。
**目标：** 新增纯工具导向的 `railway-common` 模块，提供通用枚举码值、断言、对象复制、环境与线程工具。

**架构：** `common` 是普通 JAR，不包含自动配置或全局 Spring 容器。断言复用 `convention` 的 `ClientException`；对象复制和环境判断接受 Spring 提供的对象作为参数，不隐式读取应用上下文；线程工具不创建未受业务管理的线程池。

**技术栈：** Java 17、Spring `BeanUtils`、Spring `Environment`、JUnit 5、AssertJ、Lombok（仅由父 BOM 管理）。

---

### 任务 1：接入 Maven 模块

**文件：**
- 新建：`backend/components/common/pom.xml`
- 修改：`backend/components/pom.xml`
- 修改：`backend/dependencies/pom.xml`

- [x] **步骤 1：创建模块 POM**

```xml
<artifactId>railway-common</artifactId>
<name>Railway Common</name>
<description>Shared enums and utilities for railway services</description>
<dependencies>
    <dependency>
        <groupId>com.lzj.railway</groupId>
        <artifactId>railway-convention</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework</groupId>
        <artifactId>spring-beans</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-test</artifactId>
        <scope>test</scope>
    </dependency>
</dependencies>
```

- [x] **步骤 2：将 `common` 加入组件聚合与 BOM**

在 `backend/components/pom.xml` 的 `<modules>` 中加入：

```xml
<module>common</module>
```

在 `backend/dependencies/pom.xml` 的 `<dependencyManagement>` 中加入：

```xml
<dependency>
    <groupId>com.lzj.railway</groupId>
    <artifactId>railway-common</artifactId>
    <version>${railway-components.version}</version>
</dependency>
```

- [x] **步骤 3：验证 Maven reactor**

运行：`cd backend && .\mvnw.cmd -pl components/common -am validate`

预期：`railway-parent`、`railway-components`、`railway-convention`、`railway-common` 构建成功。

### 任务 2：实现通用码值枚举

**文件：**
- 新建：`backend/components/common/src/main/java/com/lzj/railway/framework/common/enums/CodeEnum.java`
- 新建：`backend/components/common/src/main/java/com/lzj/railway/framework/common/enums/DeleteEnum.java`
- 新建：`backend/components/common/src/main/java/com/lzj/railway/framework/common/enums/FlagEnum.java`
- 新建：`backend/components/common/src/main/java/com/lzj/railway/framework/common/enums/OperationTypeEnum.java`
- 新建：`backend/components/common/src/main/java/com/lzj/railway/framework/common/enums/StatusEnum.java`
- 测试：`backend/components/common/src/test/java/com/lzj/railway/framework/common/enums/CommonEnumTest.java`

- [x] **步骤 1：编写枚举码值测试**

```java
assertThat(DeleteEnum.NORMAL.code()).isZero();
assertThat(DeleteEnum.DELETED.code()).isEqualTo(1);
assertThat(FlagEnum.NO.code()).isZero();
assertThat(FlagEnum.YES.code()).isEqualTo(1);
assertThat(OperationTypeEnum.CREATE.code()).isEqualTo(1);
assertThat(OperationTypeEnum.DELETE.code()).isEqualTo(3);
assertThat(StatusEnum.DISABLED.code()).isZero();
assertThat(StatusEnum.ENABLED.code()).isEqualTo(1);
```

- [x] **步骤 2：实现枚举契约和枚举**

```java
public interface CodeEnum {
    Integer code();
}

public enum DeleteEnum implements CodeEnum {
    NORMAL(0), DELETED(1);
    private final Integer code;
    DeleteEnum(Integer code) { this.code = code; }
    public Integer code() { return code; }
}
```

其余枚举使用相同契约：`FlagEnum` 为 `NO(0)`、`YES(1)`；`OperationTypeEnum` 为 `CREATE(1)`、`UPDATE(2)`、`DELETE(3)`、`QUERY(4)`；`StatusEnum` 为 `DISABLED(0)`、`ENABLED(1)`。

- [x] **步骤 3：运行枚举测试**

运行：`cd backend && .\mvnw.cmd -pl components/common -am -Dtest=CommonEnumTest test`

预期：全部断言通过。

### 任务 3：实现断言与对象复制工具

**文件：**
- 新建：`backend/components/common/src/main/java/com/lzj/railway/framework/common/toolkit/Assert.java`
- 新建：`backend/components/common/src/main/java/com/lzj/railway/framework/common/toolkit/BeanUtil.java`
- 测试：`backend/components/common/src/test/java/com/lzj/railway/framework/common/toolkit/AssertTest.java`
- 测试：`backend/components/common/src/test/java/com/lzj/railway/framework/common/toolkit/BeanUtilTest.java`

- [x] **步骤 1：编写断言与复制测试**

```java
assertThatThrownBy(() -> Assert.notBlank(" ", "用户名不能为空"))
        .isInstanceOf(ClientException.class)
        .hasMessage("用户名不能为空");

Target target = BeanUtil.copyProperties(new Source("G1", 2), Target.class);
assertThat(target.getTrainNumber()).isEqualTo("G1");
assertThat(target.getSeatCount()).isEqualTo(2);
assertThat(BeanUtil.copyToList(List.of(new Source("G2", 1)), Target.class))
        .extracting(Target::getTrainNumber)
        .containsExactly("G2");
```

- [x] **步骤 2：实现 `Assert`**

```java
public final class Assert {
    public static void isTrue(boolean expression, String message) {
        if (!expression) throw new ClientException(message);
    }
    public static <T> T notNull(T value, String message) {
        isTrue(value != null, message);
        return value;
    }
    public static String notBlank(String value, String message) {
        isTrue(value != null && !value.isBlank(), message);
        return value;
    }
}
```

- [x] **步骤 3：实现 `BeanUtil`**

```java
public final class BeanUtil {
    public static <T> T copyProperties(Object source, Class<T> targetType) {
        T target = BeanUtils.instantiateClass(targetType);
        BeanUtils.copyProperties(source, target);
        return target;
    }
    public static <T> List<T> copyToList(Collection<?> sources, Class<T> targetType) {
        return sources.stream().map(source -> copyProperties(source, targetType)).toList();
    }
}
```

实现还应提供 `copyProperties(Object source, T target)`，所有入口使用 `Objects.requireNonNull` 拒绝空源对象、空目标对象和空目标类型。

- [x] **步骤 4：运行工具测试**

运行：`cd backend && .\mvnw.cmd -pl components/common -am -Dtest=AssertTest,BeanUtilTest test`

预期：参数非法时得到 `ClientException`，属性复制和集合复制成功。

### 任务 4：实现环境与线程工具

**文件：**
- 新建：`backend/components/common/src/main/java/com/lzj/railway/framework/common/toolkit/EnvironmentUtil.java`
- 新建：`backend/components/common/src/main/java/com/lzj/railway/framework/common/toolkit/ThreadUtil.java`
- 测试：`backend/components/common/src/test/java/com/lzj/railway/framework/common/toolkit/EnvironmentUtilTest.java`
- 测试：`backend/components/common/src/test/java/com/lzj/railway/framework/common/toolkit/ThreadUtilTest.java`

- [x] **步骤 1：编写环境与线程测试**

```java
MockEnvironment environment = new MockEnvironment().withProperty("spring.profiles.active", "dev");
environment.setActiveProfiles("dev");
assertThat(EnvironmentUtil.isDevelopment(environment)).isTrue();
assertThat(EnvironmentUtil.isProduction(environment)).isFalse();

ThreadFactory factory = ThreadUtil.newThreadFactory("ticket-worker");
assertThat(factory.newThread(() -> {}).getName()).startsWith("ticket-worker-");
Thread.currentThread().interrupt();
ThreadUtil.sleep(1L);
assertThat(Thread.currentThread().isInterrupted()).isTrue();
Thread.interrupted();
```

- [x] **步骤 2：实现 `EnvironmentUtil`**

```java
public final class EnvironmentUtil {
    public static boolean isDevelopment(Environment environment) {
        return isActiveProfile(environment, "dev");
    }
    public static boolean isTest(Environment environment) {
        return isActiveProfile(environment, "test");
    }
    public static boolean isProduction(Environment environment) {
        return isActiveProfile(environment, "prod");
    }
    public static boolean isActiveProfile(Environment environment, String profile) {
        return Arrays.asList(Objects.requireNonNull(environment).getActiveProfiles())
                .contains(Objects.requireNonNull(profile));
    }
}
```

- [x] **步骤 3：实现 `ThreadUtil`**

```java
public final class ThreadUtil {
    public static ThreadFactory newThreadFactory(String prefix) {
        AtomicInteger sequence = new AtomicInteger(1);
        return runnable -> new Thread(runnable, prefix + "-" + sequence.getAndIncrement());
    }
    public static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }
}
```

`newThreadFactory` 还需拒绝空白前缀；`sleep` 需拒绝负数毫秒值。

- [x] **步骤 4：运行环境与线程测试**

运行：`cd backend && .\mvnw.cmd -pl components/common -am -Dtest=EnvironmentUtilTest,ThreadUtilTest test`

预期：Profile 判断、命名序号、参数校验和中断状态恢复全部通过。

### 任务 5：更新文档并完整验证

**文件：**
- 修改：`README.md`
- 修改：`docs/architecture/project-structure.md`
- 修改：`docs/architecture/spring-boot-starter-structure.md`

- [x] **步骤 1：补充模块树和职责说明**

在组件目录树增加 `common/`，说明 artifactId 为 `railway-common`，列出枚举、断言、对象复制、环境与线程工具，并明确它没有自动配置。

- [x] **步骤 2：运行完整验证**

运行：`cd backend && .\mvnw.cmd clean test`

预期：所有 Maven 模块构建成功，所有测试通过。

- [x] **步骤 3：检查提交内容**

运行：`git diff --check` 和 `git status --short`。

预期：不存在空白错误，改动仅包含 `common` 模块、聚合与 BOM、文档和本实施计划。
