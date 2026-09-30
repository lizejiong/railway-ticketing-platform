# 网关路由与 JWT 校验实施计划

> **给 agentic workers：** 必须使用 superpowers:executing-plans 按任务逐项执行本计划，并使用 checkbox（`- [ ]`）跟踪状态。

**目标：** 建立可注册到 Nacos 的 `gateway-service`，把 `/api/user/**` 转发到 `user-service`，放行注册、登录和预检请求，并对其余用户域请求校验现有 JWT。

**架构：** 网关使用 Spring Cloud Gateway 的 Reactive 路由，通过 Nacos Discovery 和 `lb://user-service` 转发请求。鉴权实现为路由级 `AbstractGatewayFilterFactory`，直接复用组件库 `JwtTokenGenerator`；不注入身份请求头、不查询 Redis、不实现 Token 黑名单。配置由本地 `application.yaml` 引导加载 Nacos 的 `gateway-service.yaml`。

**技术栈：** Java 17、Spring Boot 3.0.7、Spring Cloud Gateway 2022.0.3、Spring Cloud Alibaba Nacos、JUnit 5、Mockito、WebFlux 测试工具。

---

### 任务 1：补齐网关运行依赖

**文件：**
- 修改：`backend/services/gateway-service/pom.xml`

- [x] **步骤 1：添加负载均衡、JWT 组件、统一响应和测试依赖**

在现有 Gateway、Nacos 依赖基础上添加：

```xml
<dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-starter-loadbalancer</artifactId>
</dependency>
<dependency>
    <groupId>com.lzj.railway</groupId>
    <artifactId>railway-user-spring-boot-starter</artifactId>
</dependency>
<dependency>
    <groupId>com.lzj.railway</groupId>
    <artifactId>railway-convention</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-test</artifactId>
    <scope>test</scope>
</dependency>
```

- [x] **步骤 2：解析依赖树**

运行：

```powershell
cd backend
.\mvnw.cmd -U -pl services/gateway-service -am dependency:tree
```

预期：构建成功，依赖树包含 `spring-cloud-gateway-server`、`spring-cloud-loadbalancer` 和 `railway-user-spring-boot-starter`。

### 任务 2：用测试固定 JWT 路由过滤行为

**文件：**
- 新建：`backend/services/gateway-service/src/test/java/com/lzj/railway/gateway/filter/TokenValidateGatewayFilterFactoryTest.java`

- [x] **步骤 1：编写失败测试**

测试直接构造 `TokenValidateGatewayFilterFactory`，覆盖：

```java
@Test
void publicPathShouldPassWithoutToken() {
    Config config = config("/api/user/register", "/api/user/login");
    MockServerWebExchange exchange = exchange(HttpMethod.POST, "/api/user/login");
    GatewayFilterChain chain = mock(GatewayFilterChain.class);
    when(chain.filter(exchange)).thenReturn(Mono.empty());

    factory.apply(config).filter(exchange, chain).block();

    verify(chain).filter(exchange);
}

@Test
void protectedPathShouldRejectMissingToken() {
    MockServerWebExchange exchange = exchange(HttpMethod.GET, "/api/user/profile");

    factory.apply(config("/api/user/login", "/api/user/register"))
            .filter(exchange, mock(GatewayFilterChain.class))
            .block();

    assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    assertThat(exchange.getResponse().getBodyAsString().block()).contains("G000001");
}

@Test
void validTokenShouldPassProtectedPath() {
    String token = tokenGenerator.generateToken(UserInfoDTO.builder()
            .userId("1001")
            .username("lisi")
            .build());
    MockServerWebExchange exchange = exchangeWithAuthorization(
            HttpMethod.GET, "/api/user/profile", "Bearer " + token);
    GatewayFilterChain chain = mock(GatewayFilterChain.class);
    when(chain.filter(exchange)).thenReturn(Mono.empty());

    factory.apply(config("/api/user/login", "/api/user/register"))
            .filter(exchange, chain)
            .block();

    verify(chain).filter(exchange);
}

@Test
void invalidTokenShouldReturnUnauthorized() {
    MockServerWebExchange exchange = exchangeWithAuthorization(
            HttpMethod.GET, "/api/user/profile", "Bearer invalid-token");

    factory.apply(config("/api/user/login", "/api/user/register"))
            .filter(exchange, mock(GatewayFilterChain.class))
            .block();

    assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
}

@Test
void optionsRequestShouldPassWithoutToken() {
    MockServerWebExchange exchange = exchange(HttpMethod.OPTIONS, "/api/user/profile");
    GatewayFilterChain chain = mock(GatewayFilterChain.class);
    when(chain.filter(exchange)).thenReturn(Mono.empty());

    factory.apply(config("/api/user/login", "/api/user/register"))
            .filter(exchange, chain)
            .block();

    verify(chain).filter(exchange);
}
```

- [x] **步骤 2：运行测试并确认失败**

运行：

```powershell
cd backend
.\mvnw.cmd -U -pl services/gateway-service -am test '-Dtest=TokenValidateGatewayFilterFactoryTest' '-Dsurefire.failIfNoSpecifiedTests=false'
```

预期：测试编译失败，因为 `TokenValidateGatewayFilterFactory` 尚不存在。

### 任务 3：实现最小 Reactive JWT 路由过滤器

**文件：**
- 新建：`backend/services/gateway-service/src/main/java/com/lzj/railway/gateway/common/errorcode/GatewayErrorCode.java`
- 新建：`backend/services/gateway-service/src/main/java/com/lzj/railway/gateway/filter/TokenValidateGatewayFilterFactory.java`

- [x] **步骤 1：定义唯一的网关鉴权错误码**

```java
public enum GatewayErrorCode implements ErrorCode {

    UNAUTHORIZED("G000001", "登录状态无效或已过期");

    private final String code;
    private final String message;

    GatewayErrorCode(String code, String message) {
        this.code = code;
        this.message = message;
    }

    @Override
    public String code() {
        return code;
    }

    @Override
    public String message() {
        return message;
    }
}
```

- [x] **步骤 2：实现路由级 JWT 过滤器**

实现要求：

```java
@Component
public class TokenValidateGatewayFilterFactory
        extends AbstractGatewayFilterFactory<TokenValidateGatewayFilterFactory.Config> {

    private final JwtTokenGenerator tokenGenerator;
    private final ObjectMapper objectMapper;

    public TokenValidateGatewayFilterFactory(
            JwtTokenGenerator tokenGenerator,
            ObjectMapper objectMapper) {
        super(Config.class);
        this.tokenGenerator = tokenGenerator;
        this.objectMapper = objectMapper;
    }

    @Override
    public GatewayFilter apply(Config config) {
        return (exchange, chain) -> {
            ServerHttpRequest request = exchange.getRequest();
            String path = request.getURI().getPath();
            if (HttpMethod.OPTIONS.equals(request.getMethod()) || config.isPublicPath(path)) {
                return chain.filter(exchange);
            }
            String authorization = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
            try {
                tokenGenerator.parseToken(authorization);
                return chain.filter(exchange);
            } catch (JwtException | IllegalArgumentException exception) {
                return unauthorized(exchange.getResponse());
            }
        };
    }

    private Mono<Void> unauthorized(ServerHttpResponse response) {
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        try {
            byte[] body = objectMapper.writeValueAsBytes(
                    Result.failure(GatewayErrorCode.UNAUTHORIZED));
            DataBuffer buffer = response.bufferFactory().wrap(body);
            return response.writeWith(Mono.just(buffer));
        } catch (JsonProcessingException exception) {
            return response.setComplete();
        }
    }

    public static class Config {
        private List<String> publicPaths = List.of();

        public List<String> getPublicPaths() {
            return publicPaths;
        }

        public void setPublicPaths(List<String> publicPaths) {
            this.publicPaths = publicPaths == null ? List.of() : List.copyOf(publicPaths);
        }

        boolean isPublicPath(String path) {
            return publicPaths.contains(path);
        }
    }
}
```

过滤器仅校验 Token 并原样转发请求，不写入任何身份请求头。

- [x] **步骤 3：运行单元测试**

运行：

```powershell
cd backend
.\mvnw.cmd -U -pl services/gateway-service -am test '-Dtest=TokenValidateGatewayFilterFactoryTest' '-Dsurefire.failIfNoSpecifiedTests=false'
```

预期：5 个过滤器测试全部通过。

### 任务 4：建立网关启动与 Nacos 引导配置

**文件：**
- 新建：`backend/services/gateway-service/src/main/java/com/lzj/railway/gateway/GatewayServiceApplication.java`
- 新建：`backend/services/gateway-service/src/main/resources/application.yaml`

- [x] **步骤 1：新增启动类**

```java
@SpringBootApplication
public class GatewayServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(GatewayServiceApplication.class, args);
    }
}
```

- [x] **步骤 2：新增 Nacos 引导配置**

```yaml
spring:
  config:
    import:
      - nacos:gateway-service.yaml?group=${NACOS_GROUP:DEFAULT_GROUP}
  application:
    name: gateway-service
  cloud:
    nacos:
      server-addr: ${NACOS_SERVER_ADDR:127.0.0.1:8848}
      username: ${NACOS_USERNAME:nacos}
      password: ${NACOS_PASSWORD:nacos}
      discovery:
        server-addr: ${NACOS_SERVER_ADDR:127.0.0.1:8848}
      config:
        server-addr: ${NACOS_SERVER_ADDR:127.0.0.1:8848}
```

- [x] **步骤 3：编译网关模块**

运行：

```powershell
cd backend
.\mvnw.cmd -U -pl services/gateway-service -am -DskipTests compile
```

预期：`BUILD SUCCESS`。

### 任务 5：发布 Nacos 路由与 JWT 配置

**外部配置：**
- Nacos Data ID：`gateway-service.yaml`
- Group：`DEFAULT_GROUP`

- [x] **步骤 1：读取 `user-service.yaml` 中的 JWT 配置**

确认 `railway.user.jwt.secret`、`issuer`、`token-prefix` 与用户服务完全一致，不创建第二套签名参数。

- [x] **步骤 2：发布网关配置**

```yaml
server:
  port: 8080

spring:
  cloud:
    gateway:
      routes:
        - id: user-service
          uri: lb://user-service
          predicates:
            - Path=/api/user/**
          filters:
            - name: TokenValidate
              args:
                publicPaths:
                  - /api/user/register
                  - /api/user/login

railway:
  user:
    jwt:
      # 与 user-service.yaml 使用同一环境变量和同一本地默认值。
      secret: ${USER_JWT_SECRET:local-development-user-service-jwt-secret-change-me}
      expiration: 15m
      issuer: railway-platform
```

### 任务 6：验证完整链路并更新说明

**文件：**
- 修改：`README.md`

- [x] **步骤 1：记录网关入口和鉴权边界**

README 增加以下信息：

```markdown
## API Gateway

`gateway-service` 默认监听 `8080`，通过 Nacos 将 `/api/user/**` 转发到 `user-service`。
`/api/user/register`、`/api/user/login` 和浏览器 `OPTIONS` 预检请求公开；其余用户域路径需要有效的 `Authorization: Bearer <access-token>`。
网关与用户服务复用相同的 `railway.user.jwt` 配置，网关只校验并转发原始 Token，不注入用户身份请求头。
```

- [x] **步骤 2：运行网关测试和全模块编译**

运行：

```powershell
cd backend
.\mvnw.cmd -U -pl services/gateway-service -am test
```

预期：所有相关测试通过，输出 `BUILD SUCCESS`。

- [x] **步骤 3：联调路由**

启动 `user-service` 和 `gateway-service` 后验证：

```powershell
Invoke-WebRequest -Method Post -Uri http://127.0.0.1:8080/api/user/login -ContentType application/json -Body '{"account":"lisi","password":"Password123"}'
Invoke-WebRequest -Method Get -Uri http://127.0.0.1:8080/api/user/profile -SkipHttpErrorCheck
```

预期：登录请求被转发到用户服务；没有 Token 的资料请求由网关返回 HTTP 401 和 `G000001`。资料接口尚未实现时，携带有效 Token 的请求会通过鉴权并由用户服务返回 404，这证明鉴权和路由已经生效。
