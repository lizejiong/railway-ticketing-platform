# 提交购票与锁座实施计划

> 目标：完成可经网关调用的“提交购票、锁座、创建订单、取消回补”闭环。

## 已确认的边界

- 使用现有静态车次数据模型：`t_seat` 以“实体座位 × 可乘区间”存储，`seat_status` 为全局状态。
- **暂不新增按 `departureDate` 隔离的库存**。相同车次的不同日期会共享库存，只适合作为当前演示/学习环境，不可当作真实运营库存方案。
- 建立购票责任链、Redis 令牌桶预扣、Redisson 细粒度锁、沿途区间锁座、订单服务创建订单，以及取消订单时回补座位与令牌桶。
- 本阶段不实现：验证码、风控、选座偏好算法、支付、跨服务分布式事务、延迟关单。
- 订单服务当前无源码，需在本轮创建最小可用模块；用户服务需提供仅供内部调用的“按 ID 批量读取当前用户乘车人”能力。

## 现有数据基础

- `12306_ticket.t_train_station`：有列车站序 `sequence`、出发/到达站编码与时刻，可计算 `departure → arrival` 的连续区间。
- `12306_ticket.t_seat`：有 `train_id`、车厢、座位号、席别、`start_station`、`end_station`、价格、`seat_status`；同一实体座位已按可乘区间展开，适合区间锁定算法。
- `12306_order_0/1`：已有 `t_order_*`、`t_order_item_*`、`t_order_item_passenger_*` 分表及必要字段。

## 对外接口

### 提交购票

`POST /api/ticket/purchase`

请求字段：

```json
{
  "trainId": 3,
  "departure": "VNP",
  "arrival": "NKH",
  "passengers": [
    { "passengerId": 1, "seatType": 1 }
  ],
  "chooseSeats": []
}
```

- 需要 JWT；用户名与用户 ID 从 `UserContext` 取得，不能由客户端传入。
- 需要 `Idempotency-Key` 请求头；使用已有 `@Idempotent(type = TOKEN)`，避免网络重试造成重复购票。
- 返回订单号及已分配的车厢、座位号、乘车人、票价与席别。

### 取消订单

`POST /api/order/{orderSn}/cancel`

- 需要 JWT，只允许订单所属用户取消。
- 取消成功后，订单服务通过内部服务调用请求票务服务释放对应的区间座位，并回补 Redis 令牌桶与余票缓存。

## 实施步骤

### 1. 票务路径与购票责任链

文件范围：`backend/services/ticket-service/...`

1. 增加站序实体/Mapper，按 `trainId + departure + arrival` 从 `t_train_station` 计算连续区间列表，例如 `A→B→C` 的 `A→C` 请求得到 `[A→B, B→C]`。
2. 新增购票请求、乘车人项、响应 DTO 与 `TicketPurchaseController`；为 DTO 和业务入口编写中文注释。
3. 新增购票责任链：
   - 参数校验：必填、乘车人非空、席别合法、始发和终到不同；
   - 业务校验：车次存在、站点顺序合法、行程在可售范围内；
   - 余票前置校验：从 Redis 令牌桶读取/预扣；
   - 乘车人校验：请求中的乘车人均属于当前用户。
4. 使用已有 `railway-design-pattern` 责任链基础设施，保留清晰顺序和中文注释。
5. 扩展 `TicketErrorCode`：参数非法、车次不存在、行程非法、余票不足、座位锁定失败、乘车人无权操作、重复提交、订单创建失败等。

### 2. 令牌桶与余票缓存

文件范围：`ticket/service/handler/ticket/tokenbucket`、`resources/lua`

1. 新建 `TicketAvailabilityTokenBucket`：以 `trainId` 为 Redis Hash Key，Field 为 `startStation_endStation_seatType`。
2. 懒加载初始化：对每一个可乘区间和席别，调用 `SeatMapper` 聚合可用座位数后写入 Hash；初始化使用 Redisson 锁防止缓存击穿。
3. Lua 先检查指定行程和席别是否足够，再将所有途经区间的对应 Field 同步减一；若任一不足，整个脚本不修改库存。
4. 编写逆向 Lua 回补脚本；锁座、订单创建任一步失败都回补令牌桶。
5. 让现有查询余票的 `TicketQueryReadModel` 改为读取同一套区间余票数据，避免“查询显示有票、购票令牌桶没票”的双口径。

### 3. 实体座位选择与区间锁座

文件范围：`ticket/dao`、`ticket/service/seat`、`ticket/service/impl`

1. 补齐 `SeatMapper`：
   - 根据列车、席别和首段区间查询可用实体座位；
   - 按 `trainId + carriageNumber + seatNumber + startStation + endStation + AVAILABLE` 条件更新为 LOCKED；
   - 按同一精确条件释放为 AVAILABLE。
2. 新增 `SeatService`，对已选中的每个实体座位遍历全部受影响的可售区间并更新；任何一段乐观更新失败即视为锁座失败并回滚。
3. 按席别分组后选座；当前请求保留 `chooseSeats` 字段，但仅在选座策略落地后开放指定/相邻选座能力。
4. 购票流程按席别获取 Redisson 公平锁：`lock:purchase:{trainId}:{seatType}`。令牌桶负责高并发前置削峰，数据库条件更新作为最终正确性保障。
5. 在独立的 `@Transactional(rollbackFor = Throwable.class)` 事务服务中写入 `t_ticket` 锁座记录、创建订单并确保事务注解生效。

### 4. 订单服务与服务间调用

文件范围：`backend/services/order-service/...`、相关父 POM/Nacos 配置

1. 创建 order-service 的启动类、Nacos 配置、复合分片数据源配置、订单主表、订单明细和乘车人关联实体与 Mapper；订单和明细按 `userId/orderSn` 复合分片，关联表按证件号分片，匹配现有 32 张表。
2. 提供创建、归属查询、待支付取消接口：创建时写入完整行程和乘车人快照；取消时以订单粒度分布式锁和状态条件更新防止重复释放库存。
3. 订单号保留用户路由片段，使仅携带订单号的查询仍可精确落到目标分片。
4. 使用 OpenFeign + Nacos 调用用户服务读取乘车人快照、调用订单服务创建和查询订单；订单服务不配置公开网关路由。
5. 创建 user-service 内部批量乘车人查询接口，以用户名作为分片键和归属约束，返回创建订单需要的姓名、证件和手机号快照。
6. 票务与订单使用本地事务边界：订单调用失败时票务侧回滚实体座位并补令牌；订单成功后的跨服务补偿失败需要由后续可靠消息/任务补偿机制处理，本阶段不接入 Seata。

### 5. 取消回补与缓存一致性

1. 订单取消成功后，按订单明细中的车厢、座位、席别和行程调用票务释放座位。
2. 票务服务在释放成功后执行令牌桶逆向脚本，并更新/删除相关查询缓存。
3. 把所有 Redis Key、锁 Key、状态常量收敛到票务模块常量类，添加中文说明和过期策略。

### 6. 文档、测试与验证

1. 为购票、取消接口补充 OpenAPI 注解；更新/重新生成 Apifox 可导入 OpenAPI JSON。
2. 单元测试：路径区间计算、Lua 参数构造、席别分组、座位条件更新失败回滚、责任链错误码。
3. 集成测试：
   - 同一座位的重叠行程不能重复购买；
   - 不重叠行程可售（由区间化 `t_seat` 验证）；
   - 令牌不足不会写订单或锁座；
   - 重复 `Idempotency-Key` 不生成第二笔订单；
   - 取消后可再次购票。
4. 以 Maven 全量测试与本地 Nacos/Docker 环境进行手工验证；不启动或停止用户现有 IDEA 进程。

## 预期调用链

```text
客户端（JWT + Idempotency-Key）
  → gateway-service
  → ticket-service /purchase
       → 购票责任链
       → Redis Lua：令牌桶预扣所有途经区间
       → Redisson（车次 + 席别）公平锁
       → MySQL 条件更新：锁定每段 t_seat
       → 写入 t_ticket
       → Feign 调用 order-service：创建订单/明细/乘车人快照
  ← 订单号 + 已分配座位

取消订单
  → order-service 更新订单状态
  → ticket-service 释放每段 t_seat
  → Redis Lua 回补所有途经区间令牌
```

## 非目标与风险记录

- 静态库存不支持真实多日期售卖，后续若进入生产级设计，需要引入 `train_date` 维度的余票和座位占用记录，不能只给当前 Redis Key 加日期。
- Lua 令牌桶是高并发预扣，不代替 MySQL 条件更新；两者都必须保留。
- 跨服务调用仍可能在网络超时下出现“订单已创建但票务未收到结果”的不确定状态。本阶段只保留清晰的异常与补偿入口，不宣称强一致。
