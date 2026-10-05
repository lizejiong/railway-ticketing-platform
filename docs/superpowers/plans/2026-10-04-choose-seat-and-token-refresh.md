# 选座与余票令牌桶受控刷新实施计划

> **给执行者：** 按任务顺序实现；每个任务完成后先运行指定测试，再提交对应阶段。

**目标：** 让 `chooseSeats` 真正参与实体座位分配，并在 Redis 余票令牌与座位库存不一致时按列车受控地失效并重建令牌桶。

**架构：** 选座以当前 `t_seat` 数据模型为准：座位号表示车厢内位置，`chooseSeats` 非空时要求乘车人同席别且数量一一对应，优先在同一车厢锁定这些位置；不传时保留现有自动分配。令牌桶在预扣失败时仅允许每列车一个后台任务延迟核验数据库；若数据库仍有足够实体座位，就删除该列车 Hash，使下一次请求通过既有 `ensureInitialized` 重新构建。

**技术栈：** Spring Boot、MyBatis-Plus、Redis Lua、Redisson、Caffeine、JUnit 5、Mockito。

---

### 任务 1：让选座请求进入实体座位分配

**文件：**
- 修改：`backend/services/ticket-service/src/main/java/com/lzj/railway/ticket/dto/request/PurchaseTicketRequest.java`
- 修改：`backend/services/ticket-service/src/main/java/com/lzj/railway/ticket/service/purchase/SeatAllocationService.java`
- 修改：`backend/services/ticket-service/src/main/java/com/lzj/railway/ticket/service/purchase/TicketPurchaseTransactionService.java`

- [x] **步骤 1：先调整请求契约和服务方法签名**

将 `chooseSeats` 文档改为“车厢内座位号偏好，例如 `01A`、`01C`；为空时自动分配”。将 `allocateAndLock` 增加 `List<String> chooseSeats` 参数，并从事务服务传入 `context.getRequest().chooseSeats()`。

- [x] **步骤 2：编写选座失败测试**

在 `SeatAllocationServiceImplTest` 增加：

```java
@Test
void shouldLockChosenSeatNumbersInSameCarriage() {
    when(seatMapper.selectAvailableSeatsForSegments(3L, 1, segments, 2))
            .thenReturn(List.of(seat("03", "01A"), seat("03", "01C")));

    List<AllocatedSeat> result = service.allocateAndLock(3L, passengers, segments, List.of("01A", "01C"));

    assertThat(result).extracting(AllocatedSeat::seatNumber).containsExactly("01A", "01C");
}
```

再增加乘车人跨席别、选座数量不等、候选座位不在同一车厢、指定位置不可用时均抛出 `TICKET_SOLD_OUT` 或参数错误的测试。

- [x] **步骤 3：实现最小选座算法**

在 `SeatAllocationServiceImpl` 中：

```java
if (CollectionUtils.isEmpty(chooseSeats)) {
    return allocateAutomatically(...);
}
return allocateChosenSeats(...);
```

`allocateChosenSeats` 校验所有乘车人为同一 `seatType`、选座号无重复且数量与乘车人一致；从已查询的可用候选座位中按车厢分组，寻找一个包含全部指定座位号的车厢，按请求顺序映射乘车人并调用既有 `lockAllAffectedSegments`。找不到即抛出 `TICKET_SOLD_OUT`。

- [x] **步骤 4：运行选座测试**

Run：`mvn -pl services/ticket-service -am -Dtest=SeatAllocationServiceImplTest -Dsurefire.failIfNoSpecifiedTests=false test`

预期：所有自动选座与新增选座测试通过。

- [x] **步骤 5：提交第一个阶段**

```bash
git add backend/services/ticket-service
git commit -m "feat: support ticket seat selection"
```

### 任务 2：增加令牌不足后的受控刷新

**文件：**
- 修改：`backend/services/ticket-service/src/main/java/com/lzj/railway/ticket/service/purchase/TicketAvailabilityTokenBucket.java`
- 修改：`backend/services/ticket-service/src/main/java/com/lzj/railway/ticket/service/purchase/TicketPurchaseService.java`
- 修改：`backend/services/ticket-service/src/test/java/com/lzj/railway/ticket/service/purchase/TicketAvailabilityTokenBucketTest.java`
- 修改：`backend/services/ticket-service/src/test/java/com/lzj/railway/ticket/service/purchase/TicketPurchaseServiceTest.java`

- [x] **步骤 1：编写受控刷新的失败测试**

测试同一列车令牌预扣失败两次时，只调度一次刷新；数据库统计对请求席别仍足够时删除 `TicketCacheKey.remaining(trainId)`；不足时不删除。测试刷新路径使用 `seatMapper.countAvailableSeatsByTrainIds` 的直接行程统计。

- [x] **步骤 2：实现受控刷新方法**

在 `TicketAvailabilityTokenBucket` 增加 Caffeine 标记缓存和单线程 `ScheduledExecutorService`。新增：

```java
public void refreshOnTokenInsufficient(
        Long trainId, String departure, String arrival, Map<Integer, Long> seatTypeCounts)
```

方法以 trainId 作为刷新去重键，在短时间窗口内只调度一次；任务获取 `TicketCacheKey.lock("refresh:" + trainId)` 的 Redisson 锁，查询数据库可用实体座位。仅当数据库对全部请求席别仍足够时删除该列车令牌桶 Key，供下一次 `ensureInitialized` 重建。使用 `@PreDestroy` 关闭线程池。

- [x] **步骤 3：在预扣失败处触发刷新**

在 `TicketPurchaseService.purchase` 中：

```java
if (!tokenBucket.takeTokenFromBucket(...)) {
    tokenBucket.refreshOnTokenInsufficient(request.trainId(), context.getDepartureName(),
            context.getArrivalName(), seatTypeCounts);
    throw new ClientException(TicketErrorCode.TICKET_SOLD_OUT);
}
```

请求本身仍立即返回余票不足；刷新只修复后续请求的缓存状态，不能将当前失败请求变成重复扣减。

- [x] **步骤 4：运行令牌桶与编排测试**

Run：`mvn -pl services/ticket-service -am -Dtest=TicketAvailabilityTokenBucketTest,TicketPurchaseServiceTest -Dsurefire.failIfNoSpecifiedTests=false test`

预期：令牌预扣、回补、刷新去重及购票失败测试通过。

- [x] **步骤 5：运行票务模块完整测试并提交**

Run：`mvn -pl services/ticket-service -am test`

预期：`BUILD SUCCESS`。

```bash
git add backend/services/ticket-service
git commit -m "feat: refresh stale ticket availability tokens"
```
