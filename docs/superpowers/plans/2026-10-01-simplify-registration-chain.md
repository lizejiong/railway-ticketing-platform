# 注册责任链结构精简实施计划

> **执行要求：** 使用 `superpowers:executing-plans` 在当前功能分支内执行，不自动提交或合并。

**目标：** 在不改变注册行为的前提下，移除仅用于包装 DTO 的 `RegisterContext` 和纯装配类 `RegisterChainConfiguration`，用一个业务入口 `RegisterValidationChain` 统一参数标准化与责任链执行。

**架构：** 三个校验处理器继续独立存在并直接处理标准化后的 `RegisterRequest`。`RegisterValidationChain` 负责复制和标准化请求、构建通用 `ResponsibilityChain<RegisterRequest>`、执行校验并返回标准化请求；`UserAuthServiceImpl` 仅依赖该业务入口。

**技术栈：** Java 17、Spring Boot 3、Jakarta Validation、项目内 `ResponsibilityChain`、JUnit 5、Mockito。

---

### 任务 1：用业务责任链入口替代上下文和配置类

**文件：**
- 新建：`backend/services/user-service/src/main/java/com/lzj/railway/user/service/registration/RegisterValidationChain.java`
- 删除：`backend/services/user-service/src/main/java/com/lzj/railway/user/service/registration/RegisterContext.java`
- 删除：`backend/services/user-service/src/main/java/com/lzj/railway/user/service/registration/RegisterChainConfiguration.java`
- 修改：三个注册校验 Handler

- [ ] 新增 `RegisterValidationChain`，构造时按 `order()` 创建不可变责任链。
- [ ] 将原 `RegisterContext.from` 的标准化逻辑迁入 `validateAndNormalize(RegisterRequest)`。
- [ ] 将三个 Handler 的泛型和入参改为 `RegisterRequest`，保持顺序和校验行为不变。
- [ ] 删除不再使用的上下文和配置文件。

### 任务 2：收敛服务依赖与测试

**文件：**
- 修改：`backend/services/user-service/src/main/java/com/lzj/railway/user/service/impl/UserAuthServiceImpl.java`
- 修改：`backend/services/user-service/src/test/java/com/lzj/railway/user/service/impl/UserAuthServiceImplTest.java`
- 修改：`backend/services/user-service/src/test/java/com/lzj/railway/user/service/registration/RegisterResponsibilityChainTest.java`

- [ ] 将认证服务改为依赖 `RegisterValidationChain`，使用其返回的标准化请求进入锁和事务。
- [ ] 更新责任链测试，覆盖标准化、执行顺序及原有三段校验。
- [ ] 更新认证服务测试，验证责任链入口在加锁前执行且注册结果不变。
- [ ] 运行 `backend\\mvnw.cmd -pl services/user-service -am test`，预期全部通过。
- [ ] 运行 `git diff --check` 和引用搜索，确认无 `RegisterContext`、`RegisterChainConfiguration` 残留。
