# 生产级票务查询读模型实施计划

> **给执行 agent：** 必须使用 `superpowers:executing-plans` 按任务逐项执行本计划，并使用 checkbox（`- [ ]`）跟踪状态。
**目标：** 将 `/api/ticket/query` 改造为与参考项目查询侧一致的 Redis 读模型：责任链校验、站点/地区映射缓存、区间车次与票价缓存、余票 Hash、缓存击穿锁与 Redis Pipeline。

**架构：** 请求使用参考项目同义的 `fromStation`、`toStation` 车站编码和 `departureDate`。责任链先校验并从 Redis Hash 读取车站/地区映射；车次与票价采用 cache-aside + Redisson 双检锁；余票使用 `TRAIN_STATION_REMAINING_TICKET` 风格的 Redis Hash。查询服务只读取余票，不扣减、不锁座、不创建订单。缓存未命中回源 MySQL 后回填，Redis 故障不静默降级为错误余票。

**技术栈：** Java 17、Spring Boot 3.0.7、MyBatis-Plus、Spring Data Redis、Redisson、Fastjson2、Nacos、JUnit 5、Mockito。

---

## 固定接口与 Redis 约定

保留请求路径 `GET /api/ticket/query`，将查询参数改为：

```text
fromStation=VNP&toStation=NKH&departureDate=2026-10-02
```

`VNP` 和 `NKH` 是当前样例数据中北京南、南京南的车站编码。响应仍为 `Result<List<TicketQueryResponse>>`，其中 `departure`、`arrival` 由缓存的区间记录返回展示名称。

Redis Key 与参考项目对应：

```text
railway:ticket:station_region                 Hash: stationCode -> regionName
railway:ticket:station_name                   Hash: stationCode -> stationName
railway:ticket:region_route:{fromRegion}:{toRegion}
                                                Hash: trainId_departure_arrival -> TicketRouteCacheDTO JSON
railway:ticket:train:{trainId}                 String: TrainDO JSON
railway:ticket:price:{trainId}:{departure}:{arrival}
                                                String: List<TrainStationPriceDO> JSON
railway:ticket:remaining:{trainId}:{departure}:{arrival}
                                                Hash: seatType -> remainingTickets
railway:ticket:lock:station_region
railway:ticket:lock:region_route:{fromRegion}:{toRegion}
railway:ticket:lock:remaining:{trainId}:{departure}:{arrival}
```

本轮严格遵循参考项目余票 key 的维度，因此 `departureDate` 仅参与责任链日期校验；不得伪装为已按日期隔离的库存。

### 任务 1：接入查询侧 Redis 依赖与 Nacos 配置

**文件：**
- 修改：`backend/services/ticket-service/pom.xml`
- 修改：Nacos `ticket-service.yaml`（`DEFAULT_GROUP`）
- 测试：`backend/services/ticket-service/src/test/java/com/lzj/railway/ticket/config/TicketRedisConfigurationTest.java`

- [x] **步骤 1：编写失败的自动配置测试**

```java
class TicketRedisConfigurationTest {

    @Test
    void shouldExposeRedisInfrastructureBeans() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(CacheAutoConfiguration.class))
                .withBean(StringRedisTemplate.class, () -> mock(StringRedisTemplate.class))
                .withBean(RedissonClient.class, () -> mock(RedissonClient.class))
                .run(context -> assertThat(context).hasSingleBean(DistributedCache.class));
    }
}
```

- [x] **步骤 2：运行测试，确认当前 ticket-service 无缓存依赖而无法编译**

Run: `cd backend; .\mvnw.cmd -pl services/ticket-service -am test "-Dtest=TicketRedisConfigurationTest" "-Dsurefire.failIfNoSpecifiedTests=false"`

Expected: 编译失败，提示 `CacheAutoConfiguration`、`RedissonClient` 或 `DistributedCache` 不可解析。

- [x] **步骤 3：加入与 user-service 相同的基础设施依赖**

在 `ticket-service/pom.xml` 的 `railway-persistence-spring-boot-starter` 后加入：

```xml
<dependency>
    <groupId>com.lzj.railway</groupId>
    <artifactId>railway-cache-spring-boot-starter</artifactId>
</dependency>
<dependency>
    <groupId>com.lzj.railway</groupId>
    <artifactId>railway-design-pattern</artifactId>
</dependency>
```

将以下配置发布到 Nacos 的 `ticket-service.yaml`，保留已有 datasource 与 Nacos discovery 配置：

```yaml
spring:
  data:
    redis:
      host: ${REDIS_HOST:127.0.0.1}
      port: ${REDIS_PORT:6379}
      password: ${REDIS_PASSWORD:12306-redis}
      database: ${REDIS_DATABASE:0}
      timeout: 3s
```

- [x] **步骤 4：运行缓存自动配置测试**

Run: `cd backend; .\mvnw.cmd -pl services/ticket-service -am test "-Dtest=TicketRedisConfigurationTest" "-Dsurefire.failIfNoSpecifiedTests=false"`

Expected: `BUILD SUCCESS`，测试确认 `DistributedCache` 可由组件库自动装配。

- [x] **步骤 5：提交基础设施依赖**

```powershell
git add backend/services/ticket-service/pom.xml backend/services/ticket-service/src/test/java/com/lzj/railway/ticket/config/TicketRedisConfigurationTest.java
git commit -m "feat: add ticket query cache dependencies"
```

### 任务 2：建立车站映射与查询责任链

**文件：**
- 新建：`backend/services/ticket-service/src/main/java/com/lzj/railway/ticket/dao/entity/StationDO.java`
- 新建：`backend/services/ticket-service/src/main/java/com/lzj/railway/ticket/dao/mapper/StationMapper.java`
- 新建：`backend/services/ticket-service/src/main/java/com/lzj/railway/ticket/service/query/TicketQueryValidationChain.java`
- 新建：`backend/services/ticket-service/src/main/java/com/lzj/railway/ticket/service/query/TicketQueryContext.java`
- 新建：`backend/services/ticket-service/src/main/java/com/lzj/railway/ticket/service/query/TicketQueryParameterValidationHandler.java`
- 新建：`backend/services/ticket-service/src/main/java/com/lzj/railway/ticket/service/query/TicketQueryStationValidationHandler.java`
- 新建：`backend/services/ticket-service/src/main/java/com/lzj/railway/ticket/service/query/StationRegionCache.java`
- 新建：`backend/services/ticket-service/src/main/java/com/lzj/railway/ticket/service/query/TicketStationCacheDTO.java`
- 修改：`backend/services/ticket-service/src/main/java/com/lzj/railway/ticket/dto/request/TicketQueryRequest.java`
- 修改：`backend/services/ticket-service/src/main/java/com/lzj/railway/ticket/common/errorcode/TicketErrorCode.java`
- 测试：`backend/services/ticket-service/src/test/java/com/lzj/railway/ticket/service/query/TicketQueryValidationChainTest.java`

- [x] **步骤 1：编写责任链失败测试**

```java
@Test
void shouldRejectUnknownStationCode() {
    when(stationRegionCache.getStations("VNP", "UNKNOWN"))
            .thenThrow(new ClientException(TicketErrorCode.STATION_NOT_FOUND));

    assertThatThrownBy(() -> chain.validate(new TicketQueryRequest(
            "VNP", "UNKNOWN", LocalDate.now().plusDays(1))))
            .hasFieldOrPropertyWithValue("errorCode", "T000003");
}

@Test
void shouldRejectPastDateBeforeStationCacheLookup() {
    assertThatThrownBy(() -> chain.validate(new TicketQueryRequest(
            "VNP", "NKH", LocalDate.now().minusDays(1))))
            .hasFieldOrPropertyWithValue("errorCode", "T000002");
    verifyNoInteractions(stationRegionCache);
}
```

- [x] **步骤 2：运行测试，确认类型尚不存在**

Run: `cd backend; .\mvnw.cmd -pl services/ticket-service -am test "-Dtest=TicketQueryValidationChainTest" "-Dsurefire.failIfNoSpecifiedTests=false"`

Expected: 编译失败，提示 `StationRegionCache` 与 `TicketQueryValidationChain` 不存在。

- [x] **步骤 3：将请求 DTO 固定为参考项目的站点编码语义**

```java
public record TicketQueryRequest(
        @NotBlank @Pattern(regexp = "^[A-Za-z0-9]{2,16}$") String fromStation,
        @NotBlank @Pattern(regexp = "^[A-Za-z0-9]{2,16}$") String toStation,
        @NotNull @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate departureDate) {
}
```

增加错误码：

```java
STATION_NOT_FOUND("T000003", "出发站或到达站不存在");
```

`StationDO` 映射 `t_station` 的 `id`、`code`、`name`、`region`、`regionName`、`delFlag` 字段；`StationMapper extends BaseMapper<StationDO>`。

- [x] **步骤 4：实现站点地区 Hash 与责任链**

`StationRegionCache` 必须实现以下行为。参考项目分别维护站点到地区、站点到展示名称的映射；本项目同样用两个 Hash，避免将展示名称误当成地区：

```java
public Map<String, TicketStationCacheDTO> getStations(String fromStation, String toStation) {
    List<Object> regions = redisTemplate.opsForHash().multiGet(
            STATION_REGION_KEY, List.of(fromStation, toStation));
    List<Object> names = redisTemplate.opsForHash().multiGet(
            STATION_NAME_KEY, List.of(fromStation, toStation));
    if (regions.stream().allMatch(Objects::nonNull) && names.stream().allMatch(Objects::nonNull)) {
        return Map.of(
                fromStation, new TicketStationCacheDTO(fromStation, names.get(0).toString(), regions.get(0).toString()),
                toStation, new TicketStationCacheDTO(toStation, names.get(1).toString(), regions.get(1).toString()));
    }
    RLock lock = redissonClient.getLock(STATION_REGION_LOCK_KEY);
    lock.lock();
    try {
        // 持锁后二次读取；仍缺失时 selectList 全量加载 t_station，分别按 code -> regionName、code -> name 写入两个 Hash。
        // 写入后任一请求站点仍不存在时抛出 T000003。
    } finally {
        if (lock.isHeldByCurrentThread()) {
            lock.unlock();
        }
    }
}
```

```java
public record TicketStationCacheDTO(String code, String name, String regionName) {
}
```

`TicketQueryContext` 固定为：

```java
public record TicketQueryContext(
        String fromStationCode,
        String toStationCode,
        String fromStationName,
        String toStationName,
        String fromRegion,
        String toRegion) {
}
```

`TicketQueryValidationChain` 采用现有 `RegisterValidationChain` 的模式：注入 `List<ChainHandler<TicketQueryRequest>>`，构造 `ResponsibilityChain`，并公开 `validate(request)`；链执行成功后调用 `stationRegionCache.getStations(request.fromStation(), request.toStation())`，返回包含编码、展示名称和地区的 `TicketQueryContext`。

处理顺序固定为：

```text
order 0   参数 Bean Validation
order 10  日期不早于今天、fromStation != toStation
order 20  StationRegionCache.getStations，确认两个站点编码存在
```

- [x] **步骤 5：运行责任链测试**

Run: `cd backend; .\mvnw.cmd -pl services/ticket-service -am test "-Dtest=TicketQueryValidationChainTest" "-Dsurefire.failIfNoSpecifiedTests=false"`

Expected: `BUILD SUCCESS`，未知站点返回 `T000003`，过去日期不读取 Redis。

- [ ] **步骤 6：提交责任链与车站映射**

```powershell
git add backend/services/ticket-service/src/main/java/com/lzj/railway/ticket backend/services/ticket-service/src/test/java/com/lzj/railway/ticket/service/query
git commit -m "feat: validate ticket queries with station cache"
```

### 任务 3：建立参考项目同类的区间、车次、票价与余票读模型

**文件：**
- 新建：`backend/services/ticket-service/src/main/java/com/lzj/railway/ticket/common/constant/TicketCacheKey.java`
- 新建：`backend/services/ticket-service/src/main/java/com/lzj/railway/ticket/service/query/TicketQueryReadModel.java`
- 新建：`backend/services/ticket-service/src/main/java/com/lzj/railway/ticket/service/query/TicketRouteCacheDTO.java`
- 修改：`backend/services/ticket-service/src/main/java/com/lzj/railway/ticket/dao/entity/TrainStationRelationDO.java`
- 修改：`backend/services/ticket-service/src/main/java/com/lzj/railway/ticket/dao/mapper/SeatMapper.java`
- 修改：`backend/services/ticket-service/src/main/resources/mapper/SeatMapper.xml`
- 测试：`backend/services/ticket-service/src/test/java/com/lzj/railway/ticket/service/query/TicketQueryReadModelTest.java`

- [ ] **步骤 1：编写区间缓存与余票初始化失败测试**

```java
@Test
void shouldLoadRegionRouteOnlyOnceWhenRedisHashIsEmpty() {
    when(redisTemplate.opsForHash().entries("railway:ticket:region_route:{北京}:{南京}"))
            .thenReturn(Map.of());
    when(relationMapper.selectList(any())).thenReturn(List.of(relation(1L, "北京南", "南京南")));

    readModel.findRoutes("北京", "南京");

    verify(relationMapper).selectList(any());
    verify(redisTemplate.opsForHash()).putAll(eq("railway:ticket:region_route:{北京}:{南京}"), anyMap());
}

@Test
void shouldInitializeRemainingTicketHashFromSeatAggregation() {
    when(seatMapper.countAvailableSeatsByTrainIds(List.of(1L), "北京南", "南京南"))
            .thenReturn(List.of(new SeatRemainingDTO(1L, 2, 810)));

    assertThat(readModel.getRemainingTickets(List.of(1L), "北京南", "南京南"))
            .containsEntry(new TrainSeatKey(1L, 2), 810);
}
```

- [ ] **步骤 2：运行测试，确认读模型尚不存在**

Run: `cd backend; .\mvnw.cmd -pl services/ticket-service -am test "-Dtest=TicketQueryReadModelTest" "-Dsurefire.failIfNoSpecifiedTests=false"`

Expected: 编译失败，提示 `TicketQueryReadModel`、`TicketCacheKey` 或 `TicketRouteCacheDTO` 不存在。

- [ ] **步骤 3：补齐缓存 key 和关系表地区字段**

`TicketCacheKey` 必须提供以下静态方法，所有 key 使用 `{fromRegion}:{toRegion}` 或 `{trainId}` hash tag：

```java
public static String route(String fromRegion, String toRegion) {
    return "railway:ticket:region_route:{" + fromRegion + "}:{" + toRegion + "}";
}

public static String train(Long trainId) {
    return "railway:ticket:train:{" + trainId + "}";
}

public static String price(Long trainId, String departure, String arrival) {
    return "railway:ticket:price:{" + trainId + "}:" + departure + ":" + arrival;
}

public static String remaining(Long trainId, String departure, String arrival) {
    return "railway:ticket:remaining:{" + trainId + "}:" + departure + ":" + arrival;
}
```

在 `TrainStationRelationDO` 增加 `startRegion`、`endRegion`，映射 `t_train_station_relation` 既有字段。

- [ ] **步骤 4：实现缓存回源与余票 Hash 初始化**

`TicketQueryReadModel.findRoutes(fromRegion, toRegion)`：

```text
先 HGETALL route key
  ├─ 命中：反序列化全部 TicketRouteCacheDTO
  └─ 未命中：获取 route lock，二次 HGETALL，仍为空时
       查询 start_region/end_region 对应的 t_train_station_relation
       将 trainId_departure_arrival -> TicketRouteCacheDTO 批量 HSET
```

`getTrain(trainId)` 与 `getPrices(trainId, departure, arrival)`：先读 String Redis，未命中后分别查询 `t_train`、`t_train_station_price` 并写回 24 小时 TTL；回源锁分别使用对应 key 的 `:lock` 后缀并二次读取。

`getRemainingTickets(trainIds, departure, arrival)`：对每个 trainId 读取 `remaining` Hash；任一 Hash 不存在时，在 `remaining lock` 内调用现有 `SeatMapper.countAvailableSeatsByTrainIds`，并以 `seatType -> remainingTickets` 写入对应 Hash。余票 Hash 不设 TTL，防止查询过期导致库存值被旧座位状态覆盖。

不得使用 `DistributedCache.safeGet`：该方法依赖已预热 Bloom Filter，而本读模型使用 Redis Hash 且需按参考项目方式自己管理 HGETALL/HSET 与细粒度锁。

- [ ] **步骤 5：运行读模型测试**

Run: `cd backend; .\mvnw.cmd -pl services/ticket-service -am test "-Dtest=TicketQueryReadModelTest" "-Dsurefire.failIfNoSpecifiedTests=false"`

Expected: `BUILD SUCCESS`，区间缓存只回源一次，余票 Hash 首次由 `SeatMapper` 聚合初始化。

- [ ] **步骤 6：提交 Redis 读模型**

```powershell
git add backend/services/ticket-service/src/main/java/com/lzj/railway/ticket backend/services/ticket-service/src/main/resources/mapper backend/services/ticket-service/src/test/java/com/lzj/railway/ticket/service/query
git commit -m "feat: add ticket query redis read model"
```

### 任务 4：通过 Pipeline 组装生产级查询响应

**文件：**
- 修改：`backend/services/ticket-service/src/main/java/com/lzj/railway/ticket/service/impl/TicketQueryServiceImpl.java`
- 修改：`backend/services/ticket-service/src/main/java/com/lzj/railway/ticket/controller/TicketQueryController.java`
- 修改：`backend/services/ticket-service/src/test/java/com/lzj/railway/ticket/service/impl/TicketQueryServiceImplTest.java`
- 修改：`backend/services/ticket-service/src/test/java/com/lzj/railway/ticket/controller/TicketQueryControllerTest.java`

- [ ] **步骤 1：编写服务失败测试，确认不再直接调用四个 Mapper**

```java
@Test
void shouldBuildResponsesFromReadModel() {
    when(validationChain.validate(request("VNP", "NKH")))
            .thenReturn(new TicketQueryContext("VNP", "NKH", "北京南", "南京南", "北京", "南京"));
    when(readModel.findRoutes("北京", "南京")).thenReturn(List.of(route(1L)));
    when(readModel.getTrains(List.of(1L))).thenReturn(Map.of(1L, train(1L, "G35")));
    when(readModel.getPricesPipelined(List.of(route(1L)))).thenReturn(Map.of(1L, List.of(price(2, 53300))));
    when(readModel.getRemainingTicketsPipelined(List.of(route(1L))))
            .thenReturn(Map.of(new TrainSeatKey(1L, 2), 810));

    assertThat(service.query(request("VNP", "NKH"))).singleElement()
            .satisfies(result -> assertThat(result.trainNumber()).isEqualTo("G35"));
    verifyNoInteractions(trainStationRelationMapper, trainMapper, trainStationPriceMapper, seatMapper);
}
```

- [ ] **步骤 2：运行测试，确认旧服务实现不满足新依赖**

Run: `cd backend; .\mvnw.cmd -pl services/ticket-service -am test "-Dtest=TicketQueryServiceImplTest" "-Dsurefire.failIfNoSpecifiedTests=false"`

Expected: 编译失败，提示 `TicketQueryContext`、`TicketQueryReadModel` 或新的验证链依赖不存在。

- [ ] **步骤 3：将服务改为读模型编排**

`TicketQueryServiceImpl.query` 固定为：

```java
TicketQueryContext context = validationChain.validate(request);
List<TicketRouteCacheDTO> routes = readModel.findRoutes(context.fromRegion(), context.toRegion());
List<TicketRouteCacheDTO> availableRoutes = routes.stream()
        .filter(route -> route.departure().equals(context.fromStationName()))
        .filter(route -> route.arrival().equals(context.toStationName()))
        .toList();
Map<Long, TrainDO> trains = readModel.getTrains(availableRoutes.stream().map(TicketRouteCacheDTO::trainId).toList());
Map<Long, List<TrainStationPriceDO>> prices = readModel.getPricesPipelined(availableRoutes);
Map<TrainSeatKey, Integer> remaining = readModel.getRemainingTicketsPipelined(availableRoutes);
return assembleAndSort(availableRoutes, trains, prices, remaining);
```

`getPricesPipelined` 必须使用 `StringRedisTemplate.executePipelined` 一次 GET 所有票价 key；`getRemainingTicketsPipelined` 必须在一次 `executePipelined` 中完成所有 `HGET(key, seatType)`。缓存缺失先由任务 3 的单 key 回源逻辑填充，再进入 Pipeline。

- [ ] **步骤 4：将 Controller 文档改为站点编码参数**

Controller 仍保持：

```java
@GetMapping("/query")
public Result<List<TicketQueryResponse>> query(@Valid @ModelAttribute TicketQueryRequest request) {
    return Results.success(ticketQueryService.query(request));
}
```

OpenAPI `@Operation` 描述明确填写：`fromStation`、`toStation` 为 `t_station.code`，样例 `VNP`、`NKH`。

- [ ] **步骤 5：运行服务层与 MVC 测试**

Run: `cd backend; .\mvnw.cmd -pl services/ticket-service -am test`

Expected: `BUILD SUCCESS`；服务层验证不直接访问 Mapper，MVC 测试使用 `fromStation=VNP&toStation=NKH` 返回成功响应。

- [ ] **步骤 6：提交查询读路径切换**

```powershell
git add backend/services/ticket-service/src/main/java/com/lzj/railway/ticket backend/services/ticket-service/src/test/java/com/lzj/railway/ticket
git commit -m "feat: query tickets from redis read model"
```

### 任务 5：初始化 Redis 读模型并完成运行验证

**文件：**
- 新建：`backend/services/ticket-service/src/main/java/com/lzj/railway/ticket/service/query/TicketQueryCacheInitializer.java`
- 修改：`README.md`
- 测试：`backend/services/ticket-service/src/test/java/com/lzj/railway/ticket/service/query/TicketQueryCacheInitializerTest.java`

- [ ] **步骤 1：编写缓存初始化失败测试**

```java
@Test
void shouldInitializeStationRegionAndRouteCachesOnApplicationReady() {
    initializer.onApplicationEvent(mock(ApplicationReadyEvent.class));

    verify(stationRegionCache).warmUp();
    verify(readModel).warmUpRoutes();
}
```

- [ ] **步骤 2：运行测试，确认初始化器不存在**

Run: `cd backend; .\mvnw.cmd -pl services/ticket-service -am test "-Dtest=TicketQueryCacheInitializerTest" "-Dsurefire.failIfNoSpecifiedTests=false"`

Expected: 编译失败，提示 `TicketQueryCacheInitializer` 不存在。

- [ ] **步骤 3：实现与参考项目 Job 同目标的启动预热器**

```java
@Component
@RequiredArgsConstructor
public class TicketQueryCacheInitializer implements ApplicationListener<ApplicationReadyEvent> {

    private final StationRegionCache stationRegionCache;
    private final TicketQueryReadModel readModel;

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        stationRegionCache.warmUp();
        readModel.warmUpRoutes();
    }
}
```

`warmUpRoutes()` 查询全部有效 `t_train_station_relation`，按 `startRegion/endRegion` 分组并写入对应 route Hash；随后对每个区间写入票价缓存与余票 Hash。预热失败必须抛出异常使服务启动失败，避免 V2 查询在部分读模型下返回不完整余票。

- [ ] **步骤 4：运行完整测试与格式检查**

Run: `cd backend; .\mvnw.cmd -pl services/ticket-service -am test`

Expected: `BUILD SUCCESS`。

Run: `git diff --check`

Expected: 无输出。

- [ ] **步骤 5：通过 Nacos、Redis、服务与网关验证**

启动 `ticket-service` 与 `gateway-service` 后执行：

```powershell
Invoke-RestMethod 'http://127.0.0.1:8080/api/ticket/query?fromStation=VNP&toStation=NKH&departureDate=2026-10-02'
```

Expected: `success=true`，返回 G35、G39；Redis 中存在：

```text
railway:ticket:station_region
railway:ticket:region_route:{北京}:{南京}
railway:ticket:price:{1}:北京南:南京南
railway:ticket:remaining:{1}:北京南:南京南
```

- [ ] **步骤 6：更新说明并提交**

在 `README.md` 的票务查询章节更新参数示例与读模型说明：

```text
GET /api/ticket/query?fromStation=VNP&toStation=NKH&departureDate=2026-10-02
```

明确本轮余票 Hash 与参考项目一致，未按乘车日期隔离，锁座实现前不能作为真实库存扣减依据。

```powershell
git add README.md backend/services/ticket-service
git commit -m "docs: document ticket query read model"
```

## 计划自检

- 查询责任链、站点地区缓存、路由 Hash、车次/票价缓存、余票 Hash、细粒度 Redisson 锁、Redis Pipeline、启动预热均对应参考项目已存在的查询侧机制。
- 没有加入参考项目之外的按日期库存、网关限流、订单、支付、锁座、令牌桶或 MQ 事件。
- 余票 Hash 的写入仅初始化；购票、取消订单和 binlog/事件更新将在参考项目对应的购票链路实现时接入。
