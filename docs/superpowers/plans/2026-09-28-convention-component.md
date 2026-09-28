# Convention 规约组件实施计划

> **给 agentic workers：** 执行本计划时使用 `superpowers:executing-plans`，按任务逐项实现并在每个检查点运行测试。

**目标：** 新增不依赖 Web、Spring 和 ORM 的 `railway-convention` 模块，为所有业务服务提供统一错误码、异常、分页和响应契约。

**架构：** `components/convention` 作为纯 Java JAR 继承组件父 POM，由 `components/pom.xml` 聚合并在 dependencies BOM 中管理版本。错误码按阿里规约以 A/B/C 区分客户端、当前系统和第三方来源；分页对象仅表达 `current`、`size`、`total` 和 `records`，与 MyBatis-Plus 通过业务层适配而非类型依赖集成。

**技术栈：** Java 17、Maven 多模块、Lombok、JUnit 5、AssertJ。

---

### 任务 1：接入 convention Maven 模块

**文件：**
- 新建：`backend/components/convention/pom.xml`
- 修改：`backend/components/pom.xml`
- 修改：`backend/dependencies/pom.xml`

- [x] **步骤 1：新建模块 POM**

创建继承 `railway-components` 的 `railway-convention` JAR，只声明可选 Lombok 和测试依赖，不引入 Spring、Web 或 MyBatis-Plus。

- [x] **步骤 2：接入聚合与 BOM**

在组件总 POM 增加 `<module>convention</module>`，并在 dependencies BOM 中管理：

```xml
<dependency>
    <groupId>com.lzj.railway</groupId>
    <artifactId>railway-convention</artifactId>
    <version>${railway-components.version}</version>
</dependency>
```

- [x] **步骤 3：验证 Maven 模型**

运行：`cd backend && ./mvnw validate`

预期：reactor 中出现 `Railway Convention`，所有模块为 `SUCCESS`。

### 任务 2：实现错误码与异常体系

**文件：**
- 新建：`backend/components/convention/src/main/java/com/lzj/railway/framework/convention/errorcode/ErrorCode.java`
- 新建：`backend/components/convention/src/main/java/com/lzj/railway/framework/convention/errorcode/BaseErrorCode.java`
- 新建：`backend/components/convention/src/main/java/com/lzj/railway/framework/convention/exception/AbstractException.java`
- 新建：`backend/components/convention/src/main/java/com/lzj/railway/framework/convention/exception/ClientException.java`
- 新建：`backend/components/convention/src/main/java/com/lzj/railway/framework/convention/exception/ServiceException.java`
- 新建：`backend/components/convention/src/main/java/com/lzj/railway/framework/convention/exception/RemoteException.java`
- 测试：`backend/components/convention/src/test/java/com/lzj/railway/framework/convention/exception/ExceptionTest.java`

- [x] **步骤 1：先写异常契约测试**

覆盖默认错误码、业务自定义错误码、自定义消息覆盖默认消息、cause 保留四种行为。

- [x] **步骤 2：运行测试并确认失败**

运行：`./mvnw -pl components/convention test`

预期：类型尚不存在，测试编译失败。

- [x] **步骤 3：实现错误码接口与公共错误码**

```java
public interface ErrorCode {
    String code();
    String message();
}

public enum BaseErrorCode implements ErrorCode {
    CLIENT_ERROR("A000001", "客户端请求错误"),
    SERVICE_ERROR("B000001", "系统执行错误"),
    REMOTE_ERROR("C000001", "远程服务调用错误");
}
```

- [x] **步骤 4：实现统一异常基类和三类异常**

`AbstractException` 继承 `RuntimeException`，保存不可变的 `errorCode` 与 `errorMessage`；三类具体异常分别默认使用 A、B、C 公共错误码，并支持传入业务错误码、消息与 cause。

- [x] **步骤 5：运行异常测试**

预期：异常测试全部通过。

### 任务 3：实现分页契约

**文件：**
- 新建：`backend/components/convention/src/main/java/com/lzj/railway/framework/convention/page/PageRequest.java`
- 新建：`backend/components/convention/src/main/java/com/lzj/railway/framework/convention/page/PageResponse.java`
- 测试：`backend/components/convention/src/test/java/com/lzj/railway/framework/convention/page/PageResponseTest.java`

- [x] **步骤 1：先写分页测试**

验证默认页码为 1、默认页大小为 10、空记录为非空列表，以及 `convert` 返回新分页对象且不改变原对象。

- [x] **步骤 2：实现分页入参与出参**

```java
public class PageRequest implements Serializable {
    private long current = 1L;
    private long size = 10L;
}

public class PageResponse<T> implements Serializable {
    private long current = 1L;
    private long size = 10L;
    private long total;
    private List<T> records = Collections.emptyList();

    public <R> PageResponse<R> convert(Function<? super T, R> mapper) {
        return new PageResponse<>(current, size, total,
                records.stream().map(mapper).toList());
    }
}
```

- [x] **步骤 3：运行分页测试**

预期：分页测试全部通过，dependency tree 中不存在 MyBatis-Plus。

### 任务 4：实现公共响应对象并完成验证

**文件：**
- 新建：`backend/components/convention/src/main/java/com/lzj/railway/framework/convention/result/Result.java`
- 测试：`backend/components/convention/src/test/java/com/lzj/railway/framework/convention/result/ResultTest.java`
- 修改：`README.md`
- 修改：`docs/architecture/spring-boot-starter-structure.md`

- [x] **步骤 1：先写响应对象测试**

验证 `SUCCESS_CODE = "0"`、成功工厂、失败工厂、泛型数据、请求 ID 和 `isSuccess()`。

- [x] **步骤 2：实现响应对象**

```java
public class Result<T> implements Serializable {
    public static final String SUCCESS_CODE = "0";
    public static final String SUCCESS_MESSAGE = "success";

    private String code;
    private String message;
    private T data;
    private String requestId;

    public boolean isSuccess() {
        return SUCCESS_CODE.equals(code);
    }
}
```

同时提供 `success()`、`success(T)`、`failure(ErrorCode)` 静态工厂。

- [x] **步骤 3：同步组件文档**

记录 convention 是框架无关契约模块，业务错误码通过实现 `ErrorCode` 扩展，分页层通过业务适配器连接 MyBatis-Plus。

- [x] **步骤 4：运行完整验证**

运行：`cd backend && ./mvnw clean test`

预期：全部 reactor 模块和 convention 测试通过；`git diff --check` 无输出。
