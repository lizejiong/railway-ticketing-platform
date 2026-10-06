# 车票状态流转实施计划

> **给 agentic workers：** 使用 `superpowers:executing-plans` 逐项执行。

**目标：** 为车票记录建立订单关联，使其在支付、取消/超时关闭和退款时分别流转为已支付、已关闭和已退款，并以条件更新抵御重复或乱序消息。

**架构：** 下单事务在订单服务返回订单号后将该订单号回填至本地 `t_ticket`。支付/退款消费者按 `orderSn + trainId + 车厢 + 座位` 条件更新单张车票，只有状态实际推进才结算或释放座位、回补令牌；退款可由 `UNPAID` 或 `PAID` 进入 `REFUNDED`，从而安全处理跨 Topic 的消息乱序。

### 任务 1：建立订单号关联并补全状态流转

**文件：**
- 修改：`backend/services/ticket-service/src/main/java/com/lzj/railway/ticket/{dao/entity/TicketDO.java,common/constant/TicketStatus.java,service/purchase/TicketPurchaseTransactionService.java,service/purchase/TicketPurchaseService.java,mq/PaySuccessTicketConsumer.java,mq/RefundSuccessTicketConsumer.java}`
- 修改：`backend/services/ticket-service/src/test/java/com/lzj/railway/ticket/service/purchase/TicketPurchaseServiceTest.java`
- 新建：`deploy/mysql/migrations/002-add-ticket-order-sn.sql`
- 修改：`deploy/compose.yaml`

- [ ] 为 `t_ticket` 增加 `order_sn` 和索引，在下单本地事务中回填订单号。
- [ ] 支付、关闭与退款分别推进车票状态；重复事件不重复操作座位和令牌。
- [ ] 退款可处理先于支付消息到达的场景，避免后续支付消息重新卖座。
- [ ] 编译并提交 `fix: synchronize ticket lifecycle status`。
