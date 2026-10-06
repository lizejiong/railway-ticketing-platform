# 参考支付域实施计划

> **给执行者：** 按任务顺序实现。每个跨服务状态流转必须以单元测试和接口测试验证；每个可独立运行的阶段单独提交。

**目标：** 依据参考工程完成支付单、支付宝原生支付与回调、RocketMQ 支付/退款事件、订单延迟关闭、退款单及票务状态流转。

**架构：** `pay-service` 是支付聚合根，保存支付单和退款单并与支付宝 SDK 交互；支付/退款成功后发布 RocketMQ 事件，由 `order-service` 幂等消费并更新订单，再由票务服务同步更新车票或释放座位。订单创建时发送延迟关闭消息，超时仍待支付才执行现有关闭与库存回补流程。支付宝私钥、应用 ID、回调地址均仅从 Nacos 环境变量占位符读取。

**技术栈：** Spring Cloud Alibaba、ShardingSphere JDBC、MyBatis-Plus、Redis/Redisson、RocketMQ、支付宝 Java SDK、Nacos、JUnit 5、Mockito。

---

### 任务 1：运行基础设施与支付数据模型

**文件：**
- 修改：`deploy/compose.yaml`
- 修改：`deploy/.env.example`
- 修改：`deploy/mysql/init/00-create-databases.sql`
- 修改：`backend/services/pay-service/pom.xml`
- 新建：`backend/services/pay-service/src/main/resources/application.yaml`
- 新建：`backend/services/pay-service/src/main/resources/shardingsphere-config.yaml`
- 新建：`backend/services/pay-service/src/main/java/com/lzj/railway/pay/PayServiceApplication.java`
- 新建：`backend/services/pay-service/src/main/java/com/lzj/railway/pay/dao/{entity,mapper,algorithm}/...`

- [ ] 新增 RocketMQ NameServer 与 Broker 服务，暴露本地开发端口并配置健康检查；保留现有 MySQL、Redis、Nacos 服务。
- [ ] 创建 `12306_pay_0`、`12306_pay_1`，导入 `t_pay_0..31` 和 `t_refund_0..31`；支付表按 `order_sn,pay_sn` 路由，退款表按 `order_sn,pay_sn,refund_sn` 路由。
- [ ] 引入 persistence、cache、Nacos、RocketMQ、支付宝 SDK 依赖；支付服务从 Nacos 导入 `pay-service.yaml`。
- [ ] 为 `PayDO`、`RefundDO`、Mapper 与复合分片算法编写单元测试；验证只给 `orderSn` 或 `paySn` 查询时能路由到确定分片。
- [ ] 提交：`feat: add payment infrastructure and sharding model`。

### 任务 2：支付单、支付宝渠道与回调幂等

**文件：**
- 新建：`backend/services/pay-service/src/main/java/com/lzj/railway/pay/{controller,service,service/impl,dto,config,channel}/...`
- 新建：`backend/services/pay-service/src/test/java/com/lzj/railway/pay/service/PayServiceImplTest.java`
- 修改：`backend/services/gateway-service/...` 的 pay-service 路由

- [ ] 实现 `POST /api/pay/pay/create`，参数包含订单号、支付金额、渠道、交易类型；按订单号幂等创建或返回已有待支付单。
- [ ] 实现支付宝原生支付渠道：`AlipayClient` 用 `PAY_ALIPAY_*` 配置构造，生成支付表单或二维码内容；不在仓库保存真实密钥。
- [ ] 实现 `POST /api/pay/callback/alipay`，对 `notify_id`、签名、金额、交易状态校验；以 `pay_sn + trade_no + status` 条件更新保证回调重复时不重复发布事件。
- [ ] 实现按订单号、支付单号查询支付单接口；测试支付单重复创建、合法成功回调、重复回调、非法签名和金额不符。
- [ ] 提交：`feat: implement payment orders and alipay callback`。

### 任务 3：支付成功事件与订单、车票状态流转

**文件：**
- 新建：`backend/services/pay-service/src/main/java/com/lzj/railway/pay/mq/...`
- 修改：`backend/services/order-service/src/main/java/com/lzj/railway/order/{service,service/impl,controller,...}`
- 修改：`backend/services/ticket-service/src/main/java/com/lzj/railway/ticket/{service,dao,controller,...}`
- 新建：三个服务对应的消息事件与消费者测试

- [ ] 支付服务在支付单成功落库后发布 `PAYMENT_SUCCEEDED` 事件，事件含 `orderSn`、`paySn`、`tradeNo`、支付金额、完成时间。
- [ ] 订单服务消费事件，以 `PENDING_PAYMENT -> PAID` 条件更新订单和订单明细；重复消息与已关闭订单均不重复推进状态。
- [ ] 票务服务在订单支付确认后，以 `UNPAID -> PAID` 条件更新该订单关联的 `t_ticket`；订单号与票据关联字段不足时补充持久化关联，而非按用户名模糊更新。
- [ ] 用嵌入式/测试替身验证支付成功事件至少一次投递下的幂等消费。
- [ ] 提交：`feat: propagate successful payment to orders and tickets`。

### 任务 4：订单延迟关闭与库存释放

**文件：**
- 修改：`backend/services/order-service/src/main/java/com/lzj/railway/order/service/impl/OrderServiceImpl.java`
- 新建：`backend/services/order-service/src/main/java/com/lzj/railway/order/mq/...`
- 修改：`backend/services/ticket-service/src/main/java/com/lzj/railway/ticket/service/purchase/TicketPurchaseService.java`
- 新建：订单关闭事件、消费者与测试

- [ ] 创建订单后发送 RocketMQ 延迟消息，消息仅含 `orderSn` 与预期关闭时间。
- [ ] 延迟消费者重新读取订单，只有状态仍为 `PENDING_PAYMENT` 才关闭；已支付、已关闭订单直接幂等返回。
- [ ] 发布 `ORDER_CLOSED` 事件；票务服务消费后查询订单快照，释放实体座位并回补 Redis 令牌桶。
- [ ] 手动取消与超时关闭共用同一状态机，确保二者并发时只有一次库存回补。
- [ ] 提交：`feat: close expired orders and release ticket inventory`。

### 任务 5：退款单、支付宝退款与售后状态流转

**文件：**
- 新建：`backend/services/pay-service/src/main/java/com/lzj/railway/pay/refund/...`
- 修改：`backend/services/order-service/src/main/java/com/lzj/railway/order/...`
- 修改：`backend/services/ticket-service/src/main/java/com/lzj/railway/ticket/...`
- 新建：退款创建、重复退款、全额/部分退款测试

- [ ] 实现退款申请接口，校验订单已支付、退款金额不超过已支付金额，并创建幂等退款单。
- [ ] 调用支付宝退款 API，保存退款交易号与状态；成功后发布 `REFUND_SUCCEEDED` 事件。
- [ ] 订单服务处理全额退款为 `REFUNDED`、部分退款为 `PARTIAL_REFUNDED`，同步更新对应订单明细。
- [ ] 票务服务将退款对应车票更新为退款状态，按参考规则释放可售座位与余票。
- [ ] 提交：`feat: add payment refunds and ticket aftersales flow`。

### 任务 6：配置、接口文档与端到端验证

**文件：**
- 修改：Nacos `pay-service.yaml`、`order-service.yaml`、`ticket-service.yaml`、`gateway-service.yaml`
- 修改：Apifox/OpenAPI 导入文件
- 修改：`README.md` 或 `docs/architecture/...`

- [ ] 发布所有服务配置，支付敏感值使用 `${PAY_ALIPAY_*}`、`${ROCKETMQ_NAMESRV_ADDR}` 占位符。
- [ ] 配置网关 `/api/pay/**` 路由，回调路径不要求用户 JWT，但仅接受支付宝签名验证后的数据。
- [ ] 在本地依次启动 MySQL、Redis、Nacos、RocketMQ、user/order/ticket/pay/gateway 服务，执行“下单→创建支付宝支付单→沙箱支付回调→订单与车票已支付→退款→订单与车票退款”的端到端验证。
- [ ] 运行 `mvnw.cmd test`，确认全仓模块通过；导出 Apifox 接口定义。
- [ ] 提交：`docs: document payment domain integration`。
