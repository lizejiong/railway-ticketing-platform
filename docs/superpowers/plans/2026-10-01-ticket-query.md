# 票务查询实施计划

> **给执行 agent：** 必须使用 `superpowers:executing-plans` 按任务逐项执行本计划，并使用 checkbox（`- [ ]`）跟踪状态。

**目标：** 建立可通过网关访问的 `ticket-service`，提供公开的车次区间查询接口，返回车次、发到站时间、席别票价和数据库实时聚合的余票。

**架构：** 首版只查询 `12306_ticket` 中的 `t_train_station_relation`、`t_train`、`t_train_station_price` 和 `t_seat`。请求按出发站、到达站与乘车日期验证；先取可售车次区间，再按车次批量读取价格和席别余票，最后在服务层装配响应。网关将 `/api/ticket/**` 转发到 `ticket-service`，该查询接口公开，无 JWT 过滤。刻意不引入参考项目的区域缓存、Redis 余票缓存、令牌桶、选座和购票扣减；这些在订单创建时再实现。

**技术栈：** Java 17、Spring Boot 3.0.7、Spring Cloud Alibaba Nacos、MyBatis-Plus、MySQL 8、Springdoc OpenAPI、JUnit 5、Mockito。

---

## API 与数据契约

公开接口：

```text
GET /api/ticket/query?departure=北京南&arrival=上海虹桥&departureDate=2026-10-02
```

响应 `data` 为数组；每一项固定包含：

```json
{
  "trainId": 1,
  "trainNumber": "G1",
  "departure": "北京南",
  "arrival": "上海虹桥",
  "departureTime": "07:00",
  "arrivalTime": "11:28",
  "durationMinutes": 268,
  "departureFlag": true,
  "arrivalFlag": true,
  "trainType": 0,
  "trainBrand": "0,6",
  "saleStatus": 0,
  "seatClasses": [
    { "seatType": 0, "remainingTickets": 20, "price": 553.0 }
  ]
}
```

金额由数据库“分”转换为元 `BigDecimal`；没有可用座位的席别仍会返回，余票为 `0`，方便前端显示售罄。`departureDate` 只用于校验乘车日期不能早于当天；当前基础数据为固定线路时刻，响应只返回时分，不伪造跨日运行图。

错误码：

```java
QUERY_PARAMETER_INVALID("T000001", "车票查询参数不正确"),
DEPARTURE_DATE_INVALID("T000002", "乘车日期不能早于当天");
```

### 任务 1：补齐票务服务运行依赖与 Nacos 引导配置

**文件：**
- 修改：`backend/services/ticket-service/pom.xml`
- 新建：`backend/services/ticket-service/src/main/java/com/lzj/railway/ticket/TicketServiceApplication.java`
- 新建：`backend/services/ticket-service/src/main/resources/application.yaml`

- [ ] **步骤 1：写一个会失败的启动类编译检查**

先执行：

```powershell
cd backend
.\mvnw.cmd -pl services/ticket-service -am -DskipTests compile
```

预期：空模块本身可以编译通过；该命令用于确认后续加入运行依赖前的 Maven 基线正常。

- [ ] **步骤 2：增加与 user-service 一致的最小依赖**

在现有 Nacos 依赖之外加入以下依赖，不加入 Redis、Redisson 或 ShardingSphere：

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-validation</artifactId>
</dependency>
<dependency>
    <groupId>org.springdoc</groupId>
    <artifactId>springdoc-openapi-starter-webmvc-api</artifactId>
</dependency>
<dependency>
    <groupId>com.mysql</groupId>
    <artifactId>mysql-connector-j</artifactId>
</dependency>
<dependency>
    <groupId>com.lzj.railway</groupId>
    <artifactId>railway-web-spring-boot-starter</artifactId>
</dependency>
<dependency>
    <groupId>com.lzj.railway</groupId>
    <artifactId>railway-persistence-spring-boot-starter</artifactId>
</dependency>
<dependency>
    <groupId>org.projectlombok</groupId>
    <artifactId>lombok</artifactId>
    <optional>true</optional>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-test</artifactId>
    <scope>test</scope>
</dependency>
```

同时在 `build.plugins` 声明 `org.springframework.boot:spring-boot-maven-plugin:3.0.7`，以支持 `spring-boot:run` 和可执行 Jar 打包。

- [ ] **步骤 3：建立启动入口和 Nacos 配置引导**

创建启动类：

```java
package com.lzj.railway.ticket;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** 票务域服务启动入口。 */
@MapperScan("com.lzj.railway.ticket.dao.mapper")
@SpringBootApplication
public class TicketServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(TicketServiceApplication.class, args);
    }
}
```

创建 `application.yaml`，仅保存定位 Nacos 所需的本地配置：

```yaml
spring:
  config:
    import:
      - nacos:ticket-service.yaml?group=${NACOS_GROUP:DEFAULT_GROUP}
  application:
    name: ticket-service
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

- [ ] **步骤 4：编译票务服务**

```powershell
cd backend
.\mvnw.cmd -pl services/ticket-service -am -DskipTests compile
```

预期：`Ticket Service` 输出 `BUILD SUCCESS`。

- [ ] **步骤 5：提交运行骨架**

```powershell
git add backend/services/ticket-service/pom.xml backend/services/ticket-service/src/main
git commit -m "feat: bootstrap ticket service"
```

### 任务 2：用测试固化查询参数与响应装配

**文件：**
- 新建：`backend/services/ticket-service/src/test/java/com/lzj/railway/ticket/service/impl/TicketQueryServiceImplTest.java`
- 新建：`backend/services/ticket-service/src/main/java/com/lzj/railway/ticket/common/errorcode/TicketErrorCode.java`
- 新建：`backend/services/ticket-service/src/main/java/com/lzj/railway/ticket/dto/request/TicketQueryRequest.java`
- 新建：`backend/services/ticket-service/src/main/java/com/lzj/railway/ticket/dto/response/TicketQueryResponse.java`
- 新建：`backend/services/ticket-service/src/main/java/com/lzj/railway/ticket/dto/response/SeatClassResponse.java`

- [ ] **步骤 1：先写失败的服务测试**

测试需构造一条 `G1` 区间关系、二等座价格 `55300` 分与二等座余票 `20`，并断言服务返回 `553.00` 元和 `20` 张余票；还必须覆盖过去日期与相同站点：

```java
@Test
void shouldAssembleTrainSeatPriceAndRemainingTickets() {
    when(trainStationRelationMapper.selectList(any())).thenReturn(List.of(relation()));
    when(trainMapper.selectBatchIds(List.of(1L))).thenReturn(List.of(train()));
    when(trainStationPriceMapper.selectList(any())).thenReturn(List.of(price(0, 55300)));
    when(seatMapper.countAvailableSeatsByTrainIds(any(), eq("北京南"), eq("上海虹桥")))
            .thenReturn(List.of(new SeatRemainingDTO(1L, 0, 20)));

    List<TicketQueryResponse> result = service.query(new TicketQueryRequest(
            "北京南", "上海虹桥", LocalDate.now().plusDays(1)));

    assertThat(result).singleElement().satisfies(train -> {
        assertThat(train.trainNumber()).isEqualTo("G1");
        assertThat(train.seatClasses()).containsExactly(new SeatClassResponse(0, 20, new BigDecimal("553.00")));
    });
}

@Test
void shouldRejectPastDate() {
    assertThatThrownBy(() -> service.query(new TicketQueryRequest(
            "北京南", "上海虹桥", LocalDate.now().minusDays(1))))
            .hasFieldOrPropertyWithValue("errorCode", "T000002");
}
```

- [ ] **步骤 2：运行测试确认失败**

```powershell
cd backend
.\mvnw.cmd -pl services/ticket-service -am test '-Dtest=TicketQueryServiceImplTest' '-Dsurefire.failIfNoSpecifiedTests=false'
```

预期：因为 `TicketQueryServiceImpl`、DTO 与 Mapper 尚未创建而编译失败。

- [ ] **步骤 3：定义稳定请求、响应与错误码**

`TicketQueryRequest` 必须使用以下约束，控制器接收 query string：

```java
public record TicketQueryRequest(
        @NotBlank(message = "出发站不能为空") @Size(max = 64) String departure,
        @NotBlank(message = "到达站不能为空") @Size(max = 64) String arrival,
        @NotNull(message = "乘车日期不能为空") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate departureDate) {
}
```

响应使用 Java record，避免为查询结果创建可变 DTO：

```java
public record SeatClassResponse(Integer seatType, Integer remainingTickets, BigDecimal price) {
}

public record TicketQueryResponse(
        Long trainId, String trainNumber, String departure, String arrival,
        String departureTime, String arrivalTime, Long durationMinutes,
        Boolean departureFlag, Boolean arrivalFlag, Integer trainType,
        String trainBrand, Integer saleStatus, List<SeatClassResponse> seatClasses) {
}
```

`TicketErrorCode` 按本计划顶部列出的两个错误码实现 `ErrorCode`。

### 任务 3：实现最小可用的数据库查询与服务层

**文件：**
- 新建：`backend/services/ticket-service/src/main/java/com/lzj/railway/ticket/dao/entity/TrainDO.java`
- 新建：`backend/services/ticket-service/src/main/java/com/lzj/railway/ticket/dao/entity/TrainStationRelationDO.java`
- 新建：`backend/services/ticket-service/src/main/java/com/lzj/railway/ticket/dao/entity/TrainStationPriceDO.java`
- 新建：`backend/services/ticket-service/src/main/java/com/lzj/railway/ticket/dao/mapper/TrainMapper.java`
- 新建：`backend/services/ticket-service/src/main/java/com/lzj/railway/ticket/dao/mapper/TrainStationRelationMapper.java`
- 新建：`backend/services/ticket-service/src/main/java/com/lzj/railway/ticket/dao/mapper/TrainStationPriceMapper.java`
- 新建：`backend/services/ticket-service/src/main/java/com/lzj/railway/ticket/dao/mapper/SeatMapper.java`
- 新建：`backend/services/ticket-service/src/main/java/com/lzj/railway/ticket/dao/mapper/dto/SeatRemainingDTO.java`
- 新建：`backend/services/ticket-service/src/main/resources/mapper/SeatMapper.xml`
- 新建：`backend/services/ticket-service/src/main/java/com/lzj/railway/ticket/service/TicketQueryService.java`
- 新建：`backend/services/ticket-service/src/main/java/com/lzj/railway/ticket/service/impl/TicketQueryServiceImpl.java`

- [ ] **步骤 1：映射三张只读领域表**

三个 DO 不继承组件库的 `BaseDO`：该基类固定映射 `deleted` 字段，而票务参考表使用 `del_flag`。三个 DO 分别映射 `t_train`、`t_train_station_relation`、`t_train_station_price`，并各自声明 `createTime`、`updateTime`、`delFlag` 字段。必须包含本查询实际使用的字段：

```java
@Data
@TableName("t_train_station_relation")
public class TrainStationRelationDO {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long trainId;
    private String departure;
    private String arrival;
    private Boolean departureFlag;
    private Boolean arrivalFlag;
    private LocalDateTime departureTime;
    private LocalDateTime arrivalTime;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private Integer delFlag;
}
```

`TrainDO` 包含 `id`、`trainNumber`、`trainType`、`trainBrand`、`saleStatus`；`TrainStationPriceDO` 包含 `trainId`、`departure`、`arrival`、`seatType`、`price`。所有类和字段均添加中文 Javadoc。

- [ ] **步骤 2：实现席别余票聚合 Mapper**

`SeatMapper` 定义：

```java
public interface SeatMapper {
    List<SeatRemainingDTO> countAvailableSeatsByTrainIds(
            @Param("trainIds") Collection<Long> trainIds,
            @Param("departure") String departure,
            @Param("arrival") String arrival);
}
```

`SeatMapper.xml` 固定按车次、席别聚合状态为 `0` 的座位：

```xml
<select id="countAvailableSeatsByTrainIds"
        resultType="com.lzj.railway.ticket.dao.mapper.dto.SeatRemainingDTO">
    SELECT train_id AS trainId, seat_type AS seatType, COUNT(*) AS remainingTickets
    FROM t_seat
    WHERE start_station = #{departure}
      AND end_station = #{arrival}
      AND seat_status = 0
      AND del_flag = 0
      AND train_id IN
    <foreach collection="trainIds" item="trainId" open="(" separator="," close=")">
        #{trainId}
    </foreach>
    GROUP BY train_id, seat_type
</select>
```

- [ ] **步骤 3：实现查询服务**

服务接口固定为：

```java
public interface TicketQueryService {
    List<TicketQueryResponse> query(TicketQueryRequest request);
}
```

`query` 的实现顺序必须保持：

1. `departure.equals(arrival)` 抛 `QUERY_PARAMETER_INVALID`；`departureDate.isBefore(LocalDate.now())` 抛 `DEPARTURE_DATE_INVALID`。
2. 用 `departure`、`arrival`、`deleted=0` 查询 `t_train_station_relation`；空结果直接返回 `List.of()`。
3. 批量通过 `selectBatchIds(trainIds)` 读取列车，并过滤 `saleStatus == 0`。
4. 一次查询当前区间全部车次的价格；一次调用 `countAvailableSeatsByTrainIds` 读取余票，按 `trainId + seatType` 建 Map。
5. 每条区间关系按 `departureTime` 升序转换为响应；`Duration.between(departureTime, arrivalTime).toMinutes()` 写入 `durationMinutes`；价格使用 `BigDecimal.valueOf(price, 2)`；缺失余票取 `0`。

- [ ] **步骤 4：运行单测确认通过**

```powershell
cd backend
.\mvnw.cmd -pl services/ticket-service -am test '-Dtest=TicketQueryServiceImplTest' '-Dsurefire.failIfNoSpecifiedTests=false'
```

预期：查询装配、过去日期和同站校验全部通过。

- [ ] **步骤 5：提交查询领域实现**

```powershell
git add backend/services/ticket-service
git commit -m "feat: add ticket query service"
```

### 任务 4：发布公开 HTTP 接口与 OpenAPI 描述

**文件：**
- 新建：`backend/services/ticket-service/src/main/java/com/lzj/railway/ticket/controller/TicketQueryController.java`
- 新建：`backend/services/ticket-service/src/main/java/com/lzj/railway/ticket/config/OpenApiConfiguration.java`
- 新建：`backend/services/ticket-service/src/test/java/com/lzj/railway/ticket/controller/TicketQueryControllerTest.java`

- [ ] **步骤 1：编写失败的 MVC 测试**

```java
@Test
void shouldReturnQueryResult() throws Exception {
    when(ticketQueryService.query(any())).thenReturn(List.of(response()));

    mockMvc.perform(get("/api/ticket/query")
                    .param("departure", "北京南")
                    .param("arrival", "上海虹桥")
                    .param("departureDate", LocalDate.now().plusDays(1).toString()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data[0].trainNumber").value("G1"));
}

@Test
void shouldRejectMissingDepartureDate() throws Exception {
    mockMvc.perform(get("/api/ticket/query")
                    .param("departure", "北京南")
                    .param("arrival", "上海虹桥"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(false));
}
```

- [ ] **步骤 2：实现控制器**

```java
@RestController
@RequestMapping("/api/ticket")
@RequiredArgsConstructor
@Tag(name = "票务查询", description = "公开的车次与余票查询接口")
public class TicketQueryController {

    private final TicketQueryService ticketQueryService;

    /** 按出发站、到达站和乘车日期查询可售车次。 */
    @GetMapping("/query")
    @Operation(summary = "查询车次和余票")
    public Result<List<TicketQueryResponse>> query(@Valid @ModelAttribute TicketQueryRequest request) {
        return Results.success(ticketQueryService.query(request));
    }
}
```

`OpenApiConfiguration` 沿用 user-service 的 `OpenAPI` Bean 写法，仅把 title 改成“12306 铁路平台 - 票务服务”。不要给本接口加 `@SecurityRequirement`。

- [ ] **步骤 3：运行控制器和模块测试**

```powershell
cd backend
.\mvnw.cmd -pl services/ticket-service -am test
```

预期：`TicketQueryControllerTest` 和服务层测试均通过，构建输出 `BUILD SUCCESS`。

### 任务 5：接入 Nacos、网关与本地联调

**外部配置：**
- Nacos Data ID：`ticket-service.yaml`
- Nacos Group：`DEFAULT_GROUP`
- Nacos Data ID：`gateway-service.yaml`
- Nacos Group：`DEFAULT_GROUP`

- [ ] **步骤 1：创建 ticket-service Nacos 配置**

在 Nacos 创建 `ticket-service.yaml`：

```yaml
server:
  port: 8082

spring:
  datasource:
    url: jdbc:mysql://127.0.0.1:3306/12306_ticket?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true
    username: ${MYSQL_USERNAME:root}
    password: ${MYSQL_ROOT_PASSWORD:12306-root}
    driver-class-name: com.mysql.cj.jdbc.Driver
  cloud:
    nacos:
      discovery:
        server-addr: ${NACOS_SERVER_ADDR:127.0.0.1:8848}

springdoc:
  api-docs:
    path: /v3/api-docs
```

- [ ] **步骤 2：给 gateway-service.yaml 增加公开路由**

在 `spring.cloud.gateway.routes` 追加：

```yaml
- id: ticket-service
  uri: lb://ticket-service
  predicates:
    - Path=/api/ticket/**
```

该路由不配置 `TokenValidate`，因为车次和余票是公开信息。保留既有 `user-service` 路由及其 JWT 过滤配置不变。

- [ ] **步骤 3：核验基础数据和启动服务**

确认 Docker MySQL 的 `12306_ticket` 中存在 `t_train`、`t_train_station_relation`、`t_train_station_price`、`t_seat`；若 Docker 数据卷在导入脚本添加前已创建，则手动执行参考项目的 schema 和 data SQL 一次。启动 `ticket-service` 后，Nacos 服务列表应出现 `ticket-service`。

初始化脚本必须通过 `mysql --default-character-set=utf8mb4` 导入 SQL；否则中文站名会被错误按 latin1 解释，导致“北京南”等正常 UTF-8 查询无法命中。已有数据卷需要在确认后仅重建 `12306_ticket` 的示例数据，不能通过改查询条件兼容乱码数据。

- [ ] **步骤 4：经网关联调**

将实际存在的车站名称替换进命令：

```powershell
Invoke-WebRequest -Uri 'http://127.0.0.1:8080/api/ticket/query?departure=北京南&arrival=上海虹桥&departureDate=2026-10-02' | Select-Object -ExpandProperty Content
```

预期：HTTP 200，响应 `success=true`；无匹配区间时返回空数组，而非错误。该请求不带 `Authorization` 头也应由网关转发成功。

- [ ] **步骤 5：更新 README 并提交配置说明**

在 `README.md` 增加票务查询接口、Nacos Data ID、端口 `8082` 和公开访问边界。然后提交：

```powershell
git add README.md backend/services/ticket-service
git commit -m "docs: document ticket query service"
```

## 计划自检

- 覆盖了用户要求的票务查询、车次、区间余票与座位类型；未实现购票、订单、支付、选座、缓存预热或令牌桶。
- 参考项目中真正复用的是表模型与“线路关系 + 价格 + 可用座位”的查询边界；被排除的复杂缓存链路在当前单实例开发阶段没有收益。
- 所有新增持久化类、服务方法和控制器方法要求添加中文注释；所有可测试行为都有明确的单测和命令。
