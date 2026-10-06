# 订单退款状态聚合实施计划

> **给 agentic workers：** 使用 `superpowers:executing-plans` 逐项执行。
**目标：** 让订单主状态正确反映部分退款和全部退款。

**架构：** 退款消息消费在订单服务本地事务中完成明细状态更新与状态聚合；所有查询按 `orderSn` 精确分片，重复消息仅产生幂等更新。

### 任务 1：补充状态并在退款消费者中聚合

**文件：**
- 修改：`backend/services/order-service/src/main/java/com/lzj/railway/order/common/OrderStatus.java`
- 修改：`backend/services/order-service/src/main/java/com/lzj/railway/order/service/impl/OrderServiceImpl.java`

- [ ] 增加 `PARTIAL_REFUND(11)`、`FULL_REFUND(12)`。
- [ ] 明细退款后读取全量明细并更新主订单状态。
- [ ] 编译订单服务并提交 `fix: synchronize order refund status`。
