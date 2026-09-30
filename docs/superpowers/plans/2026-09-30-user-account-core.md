# 用户账户核心能力实施计划

> **给 agentic workers：** 必须使用 superpowers:executing-plans 按任务逐项执行本计划，并使用 checkbox（`- [ ]`）跟踪状态。

**目标：** 在现有注册、登录基础上，实现当前用户资料查询、Refresh Token 轮换与登出、乘车人 CRUD，形成后续购票所需的最小用户域闭环。

**架构：** Access Token 继续由网关和业务服务公共过滤器双重校验，业务代码只从 `UserContext` 获取当前用户。Refresh Token 作为不透明凭证存储在 Redis，刷新时消费旧 Token 并签发新的一对 Token，登出只撤销 Refresh Token。乘车人始终使用当前用户名作为分片键查询和写入，不提供按任意用户名访问的公开接口，也不引入缓存和跨库扫描。

**技术栈：** Java 17、Spring Boot 3.0.7、Spring MVC、MyBatis-Plus、ShardingSphere、Redis、JWT、Jakarta Validation、JUnit 5、Mockito。

---

### 任务 1：建立功能分支与认证错误码

**文件：**
- 修改：`backend/services/user-service/src/main/java/com/lzj/railway/user/common/errorcode/UserErrorCode.java`

- [x] **步骤 1：创建功能分支**

运行：

```powershell
git switch -c codex/user-account-core
```

- [x] **步骤 2：补充认证与乘车人错误码**

在现有枚举后增加：

```java
AUTHENTICATION_REQUIRED("U000011", "请先登录"),
REFRESH_TOKEN_INVALID("U000012", "Refresh Token 无效或已过期"),
PASSENGER_ALREADY_EXISTS("U000013", "该乘车人已存在");
```

- [x] **步骤 3：编译用户服务**

运行：

```powershell
cd backend
.\mvnw.cmd -U -pl services/user-service -am -DskipTests compile
```

预期：`BUILD SUCCESS`。

### 任务 2：实现当前用户资料查询

**文件：**
- 新建：`backend/services/user-service/src/main/java/com/lzj/railway/user/dto/response/UserProfileResponse.java`
- 新建：`backend/services/user-service/src/main/java/com/lzj/railway/user/service/UserProfileService.java`
- 新建：`backend/services/user-service/src/main/java/com/lzj/railway/user/service/impl/UserProfileServiceImpl.java`
- 新建：`backend/services/user-service/src/main/java/com/lzj/railway/user/controller/UserProfileController.java`
- 新建：`backend/services/user-service/src/test/java/com/lzj/railway/user/service/impl/UserProfileServiceImplTest.java`

- [x] **步骤 1：编写资料服务失败测试**

测试覆盖：从 `UserContext` 取得用户名并按 `username/deletionTime/delFlag` 查询；返回脱敏手机号、邮箱和证件号；没有登录上下文时抛出 `U000011`。

核心断言：

```java
UserContext.setUser(UserInfoDTO.builder().userId("1001").username("railway_user").build());
UserProfileResponse response = service.getCurrentProfile();
assertEquals("railway_user", response.username());
assertEquals("138****8000", response.phone());
```

- [x] **步骤 2：运行测试并确认失败**

运行：

```powershell
.\mvnw.cmd -U -pl services/user-service -am test `
  '-Dtest=UserProfileServiceImplTest' `
  '-Dsurefire.failIfNoSpecifiedTests=false'
```

预期：因资料服务尚不存在而编译失败。

- [x] **步骤 3：实现资料响应与查询服务**

响应只包含当前阶段需要的字段：

```java
public record UserProfileResponse(
        Long userId,
        String username,
        String realName,
        String region,
        Integer idType,
        String idCard,
        String phone,
        String email,
        Integer userType,
        Integer verifyStatus) {
}
```

服务接口：

```java
public interface UserProfileService {
    UserProfileResponse getCurrentProfile();
}
```

实现必须按当前用户名直达分片，并在响应前脱敏敏感字段；不得接受客户端传入的 `userId` 或 `username`。

- [x] **步骤 4：新增资料接口**

```java
@GetMapping("/profile")
@SecurityRequirement(name = "bearerAuth")
public Result<UserProfileResponse> getCurrentProfile() {
    return Results.success(userProfileService.getCurrentProfile());
}
```

- [x] **步骤 5：运行资料服务测试**

预期：测试全部通过。

### 任务 3：完成 Refresh Token 轮换与登出

**文件：**
- 修改：`backend/services/user-service/src/main/java/com/lzj/railway/user/session/RefreshTokenService.java`
- 新建：`backend/services/user-service/src/main/java/com/lzj/railway/user/dto/request/RefreshTokenRequest.java`
- 修改：`backend/services/user-service/src/main/java/com/lzj/railway/user/service/UserAuthService.java`
- 修改：`backend/services/user-service/src/main/java/com/lzj/railway/user/service/impl/UserAuthServiceImpl.java`
- 修改：`backend/services/user-service/src/main/java/com/lzj/railway/user/controller/UserAuthController.java`
- 修改：`backend/services/user-service/src/test/java/com/lzj/railway/user/service/impl/UserAuthServiceImplTest.java`
- 新建：`backend/services/user-service/src/test/java/com/lzj/railway/user/session/RefreshTokenServiceTest.java`

- [x] **步骤 1：编写 Refresh Token 服务失败测试**

覆盖：有效 Token 被消费并删除 Redis Key；不存在或已经消费的 Token 返回空；登出撤销操作幂等。

服务新增签名：

```java
public Optional<RefreshSession> consume(String token);
public void revoke(String token);
```

- [x] **步骤 2：实现 Refresh Token 消费和撤销**

`consume` 先读取哈希会话，再删除对应 Key；只有删除成功的调用才能获得会话，从而避免同一 Token 重复换发。

```java
Map<Object, Object> values = redisTemplate.opsForHash().entries(key(token));
if (values.isEmpty() || !Boolean.TRUE.equals(redisTemplate.delete(key(token)))) {
    return Optional.empty();
}
return Optional.of(new RefreshSession(
        Long.valueOf(String.valueOf(values.get("userId"))),
        String.valueOf(values.get("username")),
        Instant.parse(String.valueOf(values.get("createdAt")))));
```

- [x] **步骤 3：实现刷新与登出业务**

接口新增：

```java
LoginResponse refresh(RefreshTokenRequest request);
void logout(RefreshTokenRequest request);
```

刷新流程：消费旧 Refresh Token、按会话用户名查询有效用户、签发新 Access Token 和新 Refresh Token。登出只撤销传入的 Refresh Token，不维护 Access Token 黑名单。

- [x] **步骤 4：新增认证接口**

```java
@PostMapping("/token/refresh")
public Result<LoginResponse> refresh(@Valid @RequestBody RefreshTokenRequest request) {
    return Results.success(userAuthService.refresh(request));
}

@PostMapping("/logout")
public Result<Void> logout(@Valid @RequestBody RefreshTokenRequest request) {
    userAuthService.logout(request);
    return Results.success();
}
```

- [x] **步骤 5：运行认证测试**

预期：登录、刷新、旧 Token 失效、登出撤销测试全部通过。

### 任务 4：接入乘车人分片表与加密规则

**文件：**
- 修改：`backend/services/user-service/src/main/resources/shardingsphere-config.yaml`
- 新建：`backend/services/user-service/src/main/java/com/lzj/railway/user/dao/entity/PassengerDO.java`
- 新建：`backend/services/user-service/src/main/java/com/lzj/railway/user/dao/mapper/PassengerMapper.java`

- [x] **步骤 1：增加乘车人分片规则**

```yaml
t_passenger:
  actualDataNodes: ds_${0..1}.t_passenger_${0..31}
  databaseStrategy:
    standard:
      shardingColumn: username
      shardingAlgorithmName: passenger_database_hash_mod
  tableStrategy:
    standard:
      shardingColumn: username
      shardingAlgorithmName: passenger_table_hash_mod
```

数据库算法复用 `CustomDbHashModShardingAlgorithm`，表算法使用 `HASH_MOD` 和 `sharding-count: 32`。

- [x] **步骤 2：增加敏感字段加密规则**

为 `t_passenger.id_card`、`t_passenger.phone` 增加 AES 加密；同时补齐 `t_user.id_card` 和 `t_user.address` 的加密规则。

- [x] **步骤 3：建立实体与 Mapper**

`PassengerDO` 映射：`id`、`username`、`realName`、`idType`、`idCard`、`discountType`、`phone`、`createDate`、`verifyStatus`、`createTime`、`updateTime`、`delFlag`。

### 任务 5：实现乘车人 CRUD

**文件：**
- 新建：`backend/services/user-service/src/main/java/com/lzj/railway/user/dto/request/PassengerCreateRequest.java`
- 新建：`backend/services/user-service/src/main/java/com/lzj/railway/user/dto/request/PassengerUpdateRequest.java`
- 新建：`backend/services/user-service/src/main/java/com/lzj/railway/user/dto/response/PassengerResponse.java`
- 新建：`backend/services/user-service/src/main/java/com/lzj/railway/user/service/PassengerService.java`
- 新建：`backend/services/user-service/src/main/java/com/lzj/railway/user/service/impl/PassengerServiceImpl.java`
- 新建：`backend/services/user-service/src/main/java/com/lzj/railway/user/controller/PassengerController.java`
- 新建：`backend/services/user-service/src/test/java/com/lzj/railway/user/service/impl/PassengerServiceImplTest.java`

- [x] **步骤 1：编写乘车人服务失败测试**

覆盖：按当前用户名列出乘车人；新增时写入当前用户名和默认状态；重复证件号返回 `U000013`；修改和删除必须同时匹配 `username` 与 `id`；非本人数据统一返回 `U000009`，避免泄露乘车人是否属于其他账户。

- [x] **步骤 2：实现请求和响应 DTO**

新增请求字段：`realName`、`idType`、`idCard`、`discountType`、`phone`。修改请求在此基础上增加必填 `id`。响应返回脱敏后的证件号和手机号，不返回真实敏感字段。

- [x] **步骤 3：实现乘车人服务**

服务签名：

```java
List<PassengerResponse> listCurrentUserPassengers();
PassengerResponse create(PassengerCreateRequest request);
PassengerResponse update(Long passengerId, PassengerUpdateRequest request);
void delete(Long passengerId);
```

所有 SQL 条件都必须包含 `username`，确保 ShardingSphere 能按分片键定向路由。

- [x] **步骤 4：新增 REST 接口**

```text
GET    /api/user/passengers
POST   /api/user/passengers
PUT    /api/user/passengers/{passengerId}
DELETE /api/user/passengers/{passengerId}
```

四个接口均声明 `bearerAuth`，请求身份只来自 `UserContext`。

- [x] **步骤 5：运行乘车人测试**

预期：CRUD、归属校验、脱敏和重复校验测试全部通过。

### 任务 6：更新 OpenAPI、网关公开路径与联调说明

**文件：**
- 修改：`backend/services/user-service/src/main/java/com/lzj/railway/user/config/OpenApiConfiguration.java`
- 修改：`README.md`
- 外部配置：Nacos `gateway-service.yaml`

- [x] **步骤 1：声明 Bearer JWT 安全方案**

```java
@SecurityScheme(
        name = "bearerAuth",
        type = SecuritySchemeType.HTTP,
        scheme = "bearer",
        bearerFormat = "JWT")
```

- [x] **步骤 2：更新网关公开路径**

`publicPaths` 增加：

```yaml
- /api/user/token/refresh
- /api/user/logout
```

资料与乘车人路径继续受 JWT 保护。

- [x] **步骤 3：运行完整测试**

```powershell
cd backend
.\mvnw.cmd -U -pl services/user-service,services/gateway-service -am test
```

预期：`BUILD SUCCESS`，所有测试无失败。

- [x] **步骤 4：运行接口联调**

依次验证：登录获得 Token、携带 Access Token 查询资料、使用 Refresh Token 换发、旧 Refresh Token 无法再次使用、登出后 Refresh Token 失效、乘车人 CRUD 只能操作当前用户数据。

- [x] **步骤 5：检查工作区**

```powershell
git diff --check
git status --short
```

预期：无空白错误，仅包含本计划相关改动。
