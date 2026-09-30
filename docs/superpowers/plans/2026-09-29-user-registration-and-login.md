# 用户注册与登录实施计划

> **给 agentic workers：** 使用 `superpowers:executing-plans` 按任务逐项执行，并用 `- [ ]` 跟踪状态。

**目标：** 交付 `user-service` 的用户名/手机号/邮箱注册与密码登录、JWT Access Token、Refresh Token 轮换和 Redis Session；不实现验证码、登录锁定、账号冻结/注销、登出、找回密码、实名信息或乘车人接口。

**架构：** 用户、手机号索引和邮箱索引使用 ShardingSphere-JDBC 的 `2 库 × 32 表` 规则。注册获取用户名 Redisson 锁后使用普通 Spring `@Transactional` 写入三个逻辑表；由于手机号/邮箱可能位于不同实际库，该选择不保证跨库原子性。登录先解析账号类型，手机号/邮箱索引先解析 username，再按 username 查询 `t_user`；密码用 BCrypt 校验，Access Token 沿用公共 JWT Starter，Refresh Token 为随机值，Redis 仅保存其 SHA-256 哈希对应的 Session。

**技术栈：** Spring Boot、Spring Cloud Alibaba Nacos、MyBatis-Plus、ShardingSphere-JDBC 5.3.2、MySQL、Redis/Redisson、BCrypt、JUnit 5。

---

### 任务 1：提供可复用的分库算法和依赖

**文件：**
- 修改：`backend/dependencies/pom.xml`
- 修改：`backend/components/persistence/pom.xml`
- 新建：`backend/components/persistence/src/main/java/com/lzj/railway/framework/starter/persistence/sharding/CustomDbHashModShardingAlgorithm.java`
- 修改：`backend/services/user-service/pom.xml`

- [ ] **步骤 1：管理并引入 ShardingSphere JDBC Starter**

在 BOM 管理 `org.apache.shardingsphere:shardingsphere-jdbc-core-spring-boot-starter:${shardingsphere.version}`；persistence Starter 依赖该 Starter 并提供算法类。user-service 依赖 web、user、cache、persistence、idempotent、Spring Security Crypto、MySQL Connector/J 与测试依赖。

- [ ] **步骤 2：实现 `CustomDbHashModShardingAlgorithm`**

在 persistence Starter 中实现用户给出的 `StandardShardingAlgorithm<Comparable<?>>`：精确分片使用 `Math.abs((long) value.hashCode()) % shardingCount / tableShardingCount` 选择数据源，范围查询返回全部目标。类的全限定名写入后续 `CLASS_BASED` 配置。

- [ ] **步骤 3：验证算法行为**

添加 JUnit 测试，给定 `sharding-count=32`、`table-sharding-count=16` 时，`"alice"` 路由到名字后缀 `0` 或 `1` 的实际数据源，范围查询返回两个数据源。

### 任务 2：配置可启动的 user-service

**文件：**
- 新建：`backend/services/user-service/src/main/java/com/lzj/railway/user/UserServiceApplication.java`
- 新建：`backend/services/user-service/src/main/resources/application.yaml`
- 新建：`backend/services/user-service/src/main/resources/shardingsphere-config.yaml`

- [ ] **步骤 1：创建应用启动类**

使用 `@SpringBootApplication` 与 `@MapperScan("com.lzj.railway.user.dao.mapper")`。

- [ ] **步骤 2：配置 Nacos、Redis、JWT 和逻辑数据源**

默认服务名为 `user-service`、端口为 `8081`；Nacos、Redis、JWT 密钥、两个 MySQL 数据源均从环境变量读取，并提供仅本地开发使用的默认地址。JWT Access Token 过期时间固定为 15 分钟，Refresh Token TTL 固定为 30 天。

- [ ] **步骤 3：定义 ShardingSphere 规则**

写入 `t_user`、`t_user_phone`、`t_user_mail` 的实际节点和标准分片规则；用户名用 `CustomDbHashModShardingAlgorithm` 决定 `ds_0/ds_1`，逻辑表用 HASH_MOD 取 32 个后缀。手机号/邮箱索引按其自身列路由；`t_user` 的手机号、邮箱等加密规则遵循已给参考配置。

### 任务 3：建立注册领域模型和错误契约

**文件：**
- 新建：`backend/services/user-service/src/main/java/com/lzj/railway/user/common/errorcode/UserErrorCode.java`
- 新建：`backend/services/user-service/src/main/java/com/lzj/railway/user/dto/request/RegisterRequest.java`
- 新建：`backend/services/user-service/src/main/java/com/lzj/railway/user/dto/request/LoginRequest.java`
- 新建：`backend/services/user-service/src/main/java/com/lzj/railway/user/dto/response/RegisterResponse.java`
- 新建：`backend/services/user-service/src/main/java/com/lzj/railway/user/dto/response/LoginResponse.java`
- 新建：`backend/services/user-service/src/main/java/com/lzj/railway/user/dao/entity/UserDO.java`
- 新建：`backend/services/user-service/src/main/java/com/lzj/railway/user/dao/entity/UserPhoneDO.java`
- 新建：`backend/services/user-service/src/main/java/com/lzj/railway/user/dao/entity/UserMailDO.java`
- 新建：`backend/services/user-service/src/main/java/com/lzj/railway/user/dao/mapper/UserMapper.java`
- 新建：`backend/services/user-service/src/main/java/com/lzj/railway/user/dao/mapper/UserPhoneMapper.java`
- 新建：`backend/services/user-service/src/main/java/com/lzj/railway/user/dao/mapper/UserMailMapper.java`

- [ ] **步骤 1：定义输入校验和响应模型**

注册请求要求用户名 `4–32` 位字母、数字或下划线，密码 `8–72` 位且至少含字母和数字，中国大陆手机号，以及标准邮箱。登录请求接受 `account` 和 `password`。

- [ ] **步骤 2：定义稳定业务错误码**

定义 `USER_NAME_ALREADY_EXISTS`、`PHONE_ALREADY_BOUND`、`EMAIL_ALREADY_BOUND`、`PASSWORD_INCORRECT`、`ACCOUNT_NOT_FOUND`、`INVALID_USERNAME`、`INVALID_PASSWORD`、`INVALID_PHONE`、`INVALID_EMAIL`，并为后续接口预留 `VERIFICATION_CODE_INVALID`、`PASSENGER_NOT_FOUND`、`PASSENGER_ACCESS_DENIED`、`ACCOUNT_FROZEN`。

- [ ] **步骤 3：映射现有逻辑表**

`UserDO` 映射 `t_user`；手机号、邮箱索引映射 `t_user_phone`、`t_user_mail`。三个实体显式写入 `deletion_time=0` 与 `del_flag=0`，并不继承字段名不兼容的 `BaseDO`。

### 任务 4：实现注册与登录会话

**文件：**
- 新建：`backend/services/user-service/src/main/java/com/lzj/railway/user/service/UserAuthService.java`
- 新建：`backend/services/user-service/src/main/java/com/lzj/railway/user/service/impl/UserAuthServiceImpl.java`
- 新建：`backend/services/user-service/src/main/java/com/lzj/railway/user/session/RefreshSession.java`
- 新建：`backend/services/user-service/src/main/java/com/lzj/railway/user/session/RefreshTokenService.java`
- 新建：`backend/services/user-service/src/main/java/com/lzj/railway/user/controller/UserAuthController.java`

- [ ] **步骤 1：实现注册**

以 `railway:user:register:lock:{username}` 获取 Redisson 锁；检查用户名、手机号和邮箱唯一性，BCrypt 哈希密码，在 `@Transactional` 方法中写入 `t_user`、`t_user_phone`、`t_user_mail`，提交后删除用户缓存并将用户名加入 Bloom Filter（如果可用）。返回 userId、username、phone、email。

- [ ] **步骤 2：实现登录**

按正则识别手机号、邮箱或用户名；索引查询先获得 username，再查询用户记录；用 BCrypt 校验密码。成功时生成公共 JWT Access Token，生成 32 字节随机 Refresh Token，Redis 以 SHA-256 哈希为 key 保存 30 天 `RefreshSession`。返回两个 token、过期秒数和用户基础信息。

- [ ] **步骤 3：公开 REST API**

提供 `POST /api/user/register` 与 `POST /api/user/login`，用已有 `Results.success` 响应和 `ClientException` 交付统一错误码。对注册接口添加参数型 `@Idempotent`，业务唯一前缀为 `user:register`。

### 任务 5：测试和运行验证

**文件：**
- 新建：`backend/services/user-service/src/test/java/com/lzj/railway/user/service/UserAuthServiceImplTest.java`
- 新建：`backend/services/user-service/src/test/java/com/lzj/railway/user/sharding/CustomDbHashModShardingAlgorithmTest.java`
- 修改：`README.md`

- [ ] **步骤 1：编写服务单元测试**

覆盖用户名已存在、手机号已绑定、邮箱已绑定、成功注册、用户名/手机号/邮箱三种登录、密码错误、Refresh Token 只存哈希。

- [ ] **步骤 2：运行模块测试与 Maven 校验**

Run: `backend/mvnw.cmd -f backend/pom.xml -pl services/user-service,components/persistence -am test`

Expected: `BUILD SUCCESS`。

- [ ] **步骤 3：更新 README**

记录两个端点、环境变量、普通本地事务的跨库限制和 Redis Session 的 TTL。

- [ ] **步骤 4：检查格式**

Run: `git diff --check`

Expected: 无输出且退出码为 0。
