# Design Pattern 组件实施计划

> **给 agentic workers：** 执行本计划时使用 `superpowers:executing-plans`，按任务逐项实现并在每个检查点运行测试。

**目标：** 新增框架无关的 `railway-design-pattern` 模块，提供可复用的构建者、责任链和策略模式基础设施。

**架构：** `components/designpattern` 作为纯 Java JAR，由组件总 POM 聚合并在 dependencies BOM 中管理版本。构建者支持用构造函数和有序修改器创建新对象；责任链以明确的 `CONTINUE/STOP` 决策控制流程，并按处理器顺序稳定执行；策略选择器以唯一标识建立不可变注册表，提供查找、支持判断和直接执行能力。模块不依赖 Spring，业务服务可手动组装，也可用 Spring 注入实现集合后构造注册表。

**技术栈：** Java 17、Maven 多模块、JUnit 5、AssertJ。

---

### 任务 1：接入 designpattern Maven 模块

**文件：**
- 新建：`backend/components/designpattern/pom.xml`
- 修改：`backend/components/pom.xml`
- 修改：`backend/dependencies/pom.xml`

- [x] **步骤 1：新建纯 Java 模块**

创建 `railway-design-pattern` JAR，只引入测试依赖，不引入 Spring、Web 或业务模块。

- [x] **步骤 2：接入聚合与 BOM**

在组件总 POM 增加 `<module>designpattern</module>`，并在 dependencies BOM 中管理内部组件版本。

- [x] **步骤 3：验证 Maven 模型**

运行 `./mvnw -pl components/designpattern -am validate`，预期新模块进入 reactor 且构建成功。

### 任务 2：实现构建者模式

**文件：**
- 新建：`backend/components/designpattern/src/main/java/com/lzj/railway/framework/designpattern/builder/Builder.java`

- [x] **步骤 1：实现构建契约**

`Builder<T>` 只暴露 `build()`。具体对象按需要在所属模块实现专用 Builder，避免提供缺少业务场景的通用构建器。

### 任务 3：实现责任链模式

**文件：**
- 新建：`backend/components/designpattern/src/main/java/com/lzj/railway/framework/designpattern/chain/ChainDecision.java`
- 新建：`backend/components/designpattern/src/main/java/com/lzj/railway/framework/designpattern/chain/ChainHandler.java`
- 新建：`backend/components/designpattern/src/main/java/com/lzj/railway/framework/designpattern/chain/ResponsibilityChain.java`
- 测试：`backend/components/designpattern/src/test/java/com/lzj/railway/framework/designpattern/chain/ResponsibilityChainTest.java`

- [x] **步骤 1：编写责任链测试**

覆盖按 `order()` 升序执行、相同顺序保持添加顺序、处理器返回 `STOP` 后中断、全部通过返回 `CONTINUE`、空责任链拒绝构建。

- [x] **步骤 2：实现不可变责任链**

`ChainHandler<T>` 定义 `handle(T)` 与默认顺序；`ResponsibilityChain<T>` 通过 Builder 收集处理器，构建时稳定排序并复制为不可变列表，执行时遇到 `STOP` 立即返回。

- [x] **步骤 3：运行责任链测试**

预期顺序、中断和不可变性测试通过。

### 任务 4：实现策略模式

**文件：**
- 新建：`backend/components/designpattern/src/main/java/com/lzj/railway/framework/designpattern/strategy/Strategy.java`
- 新建：`backend/components/designpattern/src/main/java/com/lzj/railway/framework/designpattern/strategy/StrategySelector.java`
- 新建：`backend/components/designpattern/src/main/java/com/lzj/railway/framework/designpattern/strategy/StrategyNotFoundException.java`
- 测试：`backend/components/designpattern/src/test/java/com/lzj/railway/framework/designpattern/strategy/StrategySelectorTest.java`

- [x] **步骤 1：编写策略测试**

覆盖按标识选择并执行策略、查询支持状态、未知标识抛出明确异常、重复标识和空策略集合拒绝创建。

- [x] **步骤 2：实现策略契约和选择器**

`Strategy<REQUEST,RESPONSE>` 定义 `mark()` 与 `execute(REQUEST)`；`StrategySelector<REQUEST,RESPONSE>` 从策略集合建立按标识查找的映射，并只暴露 `execute`。

- [x] **步骤 3：运行策略测试**

预期选择、执行和错误分支测试全部通过。

### 任务 5：同步文档并完整验证

**文件：**
- 修改：`README.md`
- 修改：`docs/architecture/project-structure.md`
- 修改：`docs/architecture/spring-boot-starter-structure.md`

- [x] **步骤 1：记录模块结构与三种模式用法**

说明模块是框架无关 JAR，给出三种模式的最小示例，并记录责任链排序/中断和策略标识唯一性约束。

- [x] **步骤 2：运行完整验证**

运行 `./mvnw clean test` 和 `git diff --check`。预期全部 reactor 模块和所有新旧测试通过，且无格式错误。
