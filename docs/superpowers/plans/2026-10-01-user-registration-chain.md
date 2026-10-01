# 用户注册责任链与实名校验实施计划

> **执行要求：** 使用 `superpowers:executing-plans` 按任务逐项执行并在每个阶段运行测试；当前环境缺少配套的 worktree/finishing skill，因此使用独立功能分支执行，不自动提交或合并。

**目标：** 将账号注册改为“注册即实名”，增加参数、用户名可用性、证件注销次数三段责任链，并保留锁内数据库复核和唯一索引作为并发最终保障。

**架构：** 复用组件库的 `ResponsibilityChain`，由 Spring 收集按顺序实现的注册处理器并构建链。Bloom Filter 与 Redis 复用集合只做快速判断，数据库查询负责兜底；注册事务使用 `TransactionTemplate` 放在用户名分布式锁内部，确保数据库提交完成后再释放锁。

**技术栈：** Java 17、Spring Boot 3、Jakarta Validation、MyBatis-Plus、ShardingSphere-JDBC 5.3.2、Redisson、JUnit 5、Mockito。

---

### 任务 1：扩展注册契约与错误码

**文件：**
- 修改：`backend/services/user-service/pom.xml`
- 修改：`backend/services/user-service/src/main/java/com/lzj/railway/user/dto/request/RegisterRequest.java`
- 修改：`backend/services/user-service/src/main/java/com/lzj/railway/user/dto/response/RegisterResponse.java`
- 修改：`backend/services/user-service/src/main/java/com/lzj/railway/user/common/errorcode/UserErrorCode.java`

- [ ] 在 `user-service` 中显式依赖 `railway-design-pattern`。
- [ ] 为注册请求增加 `realName`、`idType`、`idCard`，使用中文 Jakarta Validation 提示；当前阶段支持居民身份证 `idType=0`，身份证号支持 18 位格式。
- [ ] 注册响应增加实名状态需要的基础字段，但不返回完整证件号。
- [ ] 增加“注册参数错误”和“证件注销次数超限”稳定错误码。
- [ ] 运行 `backend\\mvnw.cmd -pl services/user-service -am -DskipTests compile`，预期编译通过。

### 任务 2：增加注销记录持久化与单表路由

**文件：**
- 新建：`backend/services/user-service/src/main/java/com/lzj/railway/user/dao/entity/UserDeletionDO.java`
- 新建：`backend/services/user-service/src/main/java/com/lzj/railway/user/dao/mapper/UserDeletionMapper.java`
- 修改：`backend/services/user-service/src/main/resources/shardingsphere-config.yaml`

- [ ] 映射已有 `t_user_deletion` 表，字段为 `id`、`idType`、`idCard`、审计时间和 `delFlag`。
- [ ] 在 ShardingSphere 中将 `ds_0.t_user_deletion`、`ds_0.t_user_reuse` 声明为单表，避免多数据源随机路由。
- [ ] 将 `t_user_deletion.id_card` 纳入 AES 加密规则，使精确条件查询与存储都使用密文。
- [ ] 运行 user-service 编译，预期配置和映射类可加载。

### 任务 3：实现三段注册责任链

**文件：**
- 新建：`backend/services/user-service/src/main/java/com/lzj/railway/user/service/registration/RegisterContext.java`
- 新建：`backend/services/user-service/src/main/java/com/lzj/railway/user/service/registration/RegisterChainConfiguration.java`
- 新建：`backend/services/user-service/src/main/java/com/lzj/railway/user/service/registration/RegisterParameterValidationHandler.java`
- 新建：`backend/services/user-service/src/main/java/com/lzj/railway/user/service/registration/RegisterUsernameAvailabilityHandler.java`
- 新建：`backend/services/user-service/src/main/java/com/lzj/railway/user/service/registration/RegisterDeletionLimitHandler.java`
- 测试：`backend/services/user-service/src/test/java/com/lzj/railway/user/service/registration/RegisterResponsibilityChainTest.java`

- [ ] 先写测试：非法参数在第一段拒绝，Bloom 阴性直接放行，Bloom 阳性但复用集合不存在且数据库有用户时拒绝，注销记录达到 5 次时拒绝。
- [ ] 运行该测试并确认因处理器不存在而失败。
- [ ] 实现不可变注册上下文，统一 trim 用户名/姓名/证件号并将邮箱转为小写。
- [ ] 参数处理器调用 Jakarta `Validator`，保证非 HTTP 调用同样执行 DTO 约束。
- [ ] 用户名处理器采用 Bloom + “可复用用户名集合”语义；Bloom 阳性且集合未命中时查数据库兜底，避免 Bloom 假阳性误拒绝。
- [ ] 注销次数处理器按 `idType + idCard` 查询 `t_user_deletion`，达到 5 次抛出统一错误码。
- [ ] 配置类按 `order()` 构建不可变责任链，顺序固定为参数 0、可用性 100、注销 200。
- [ ] 运行责任链测试，预期全部通过。

### 任务 4：重构注册事务和缓存维护

**文件：**
- 修改：`backend/services/user-service/src/main/java/com/lzj/railway/user/service/impl/UserAuthServiceImpl.java`
- 修改：`backend/services/user-service/src/test/java/com/lzj/railway/user/service/impl/UserAuthServiceImplTest.java`

- [ ] 先扩展测试，验证责任链在加锁前运行、实名字段写入 `t_user`、事务完成后才更新 Bloom/复用集合并释放锁。
- [ ] 运行测试并确认新断言失败。
- [ ] 注册入口先创建标准化上下文并执行责任链。
- [ ] 持有用户名锁期间通过 `TransactionTemplate.execute` 完成数据库权威复核和三表写入，事务返回后再释放锁。
- [ ] 写入 `realName`、`idType`、`idCard`、`verifyStatus=1`，并使用标准化手机号和邮箱。
- [ ] 提交成功后删除资料缓存、向 Bloom 添加用户名、从复用集合移除用户名；缓存失败只记录警告，不回滚已提交注册。
- [ ] 保留数据库唯一约束异常到业务错误码的转换。
- [ ] 运行 `UserAuthServiceImplTest`，预期全部通过。

### 任务 5：全量验证与接口文档检查

**文件：**
- 修改：`backend/services/user-service/src/main/java/com/lzj/railway/user/controller/UserAuthController.java`

- [ ] 更新 OpenAPI 注册接口说明，明确注册即实名及注销次数上限。
- [ ] 运行 `backend\\mvnw.cmd -pl services/user-service -am test`，预期相关模块全部测试通过。
- [ ] 运行 `git diff --check`，预期无空白符错误。
- [ ] 检查 `git status --short`，确保只包含本计划涉及的代码和计划文档。
