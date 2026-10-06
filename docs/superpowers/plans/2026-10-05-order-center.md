# 订单中心实施计划

> **给 agentic workers：** 必须使用 `superpowers:executing-plans` 按任务逐项执行本计划。
**目标：** 为当前登录用户提供可分页查询的订单列表与受归属校验的订单详情。

**架构：** 新增面向用户的订单控制器；查询永远携带 `userId`，使 ShardingSphere 可以精确路由 `t_order`、`t_order_item`。订单详情复用现有快照映射，列表仅展示必要摘要并避免 N+1 跨分片查询。

**技术栈：** Spring MVC、MyBatis-Plus、ShardingSphere、UserContext、JUnit 5。

---

### 任务 1：定义订单中心 DTO 与服务契约

**文件：**
- 新建：`backend/services/order-service/src/main/java/com/lzj/railway/order/dto/request/TicketOrderPageRequest.java`
- 新建：`backend/services/order-service/src/main/java/com/lzj/railway/order/dto/response/TicketOrderPageResponse.java`
- 修改：`backend/services/order-service/src/main/java/com/lzj/railway/order/service/OrderService.java`

- [ ] 定义 `pageNo`、`pageSize`、可选状态的请求参数，并限制页大小。
- [ ] 定义订单号、车次、行程、时间、状态、金额与乘车人摘要响应。
- [ ] 在 `OrderService` 添加按当前用户 ID 分页与详情读取的方法。

### 任务 2：实现按分片键精确路由的订单查询

**文件：**
- 修改：`backend/services/order-service/src/main/java/com/lzj/railway/order/service/impl/OrderServiceImpl.java`
- 测试：`backend/services/order-service/src/test/java/com/lzj/railway/order/service/impl/OrderServiceImplTest.java`

- [ ] 查询条件同时包含 `userId` 与订单状态，按下单时间倒序分页。
- [ ] 将订单金额由明细金额汇总，详情复用 `TicketOrderResponse` 并校验归属。
- [ ] 编写单元测试，覆盖归属校验、状态筛选和金额汇总。

### 任务 3：暴露用户订单接口并验证

**文件：**
- 新建：`backend/services/order-service/src/main/java/com/lzj/railway/order/controller/OrderCenterController.java`
- 修改：`docs/operations/payment-nacos-config.md`

- [ ] 新增 `GET /api/order/tickets` 与 `GET /api/order/tickets/{orderSn}`，从 `UserContext` 获取身份。
- [ ] 写明网关需将 `/api/order/**` 路由到 `order-service`，两个接口均需要 JWT。
- [ ] 运行订单服务测试和编译，通过后提交 `feat: add order center queries`。
