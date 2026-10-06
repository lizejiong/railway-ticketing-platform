# 退款明细幂等实施计划

> **给 agentic workers：** 使用 `superpowers:executing-plans` 逐项执行。

**目标：** 同一订单明细在订单域异步消费退款事件前被重复请求时，支付域必须拒绝第二次退款，避免重复调用第三方渠道。

**架构：** `t_refund` 持久化 `order_item_id` 并建立唯一索引；支付域以订单维度 Redisson 锁串行化后，一次读取已成功退款记录，先检查请求内重复和历史重复，再计算累计退款额度并调用渠道。

### 任务 1：持久化订单明细标识并拒绝重复退款

**文件：**
- 修改：`backend/services/pay-service/src/main/java/com/lzj/railway/pay/{dao/entity/RefundDO.java,service/impl/PaymentServiceImpl.java,common/PayErrorCode.java}`
- 修改：`backend/services/pay-service/src/test/java/com/lzj/railway/pay/service/impl/PaymentServiceImplTest.java`
- 新建：`deploy/mysql/migrations/001-add-refund-order-item-id.sql`
- 修改：`deploy/compose.yaml`

- [ ] 在退款快照中保存订单明细 ID，并对其建立唯一索引。
- [ ] 在第三方退款调用前拒绝空明细、请求内重复明细及历史已退款明细。
- [ ] 测试重复退款不会触发支付渠道。
- [ ] 编译并提交 `fix: prevent duplicate ticket refunds`。
