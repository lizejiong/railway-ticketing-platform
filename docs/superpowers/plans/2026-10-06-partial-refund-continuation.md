# 部分退款续退实施计划

> **给 agentic workers：** 使用 `superpowers:executing-plans` 逐项执行。

**目标：** 已部分退款的订单仍可对剩余已支付乘车人退款；整单退款在部分退款后表示“退掉全部剩余可退明细”。

**架构：** 票务服务以订单域返回的订单/明细状态为准，先筛选仍处于已支付状态的明细，再把金额快照提交支付域。支付域已有的订单维度锁和累计退款金额校验继续负责并发与金额边界。

### 任务 1：放开部分退款状态并保持整单退款语义

**文件：**
- 修改：`backend/services/ticket-service/src/main/java/com/lzj/railway/ticket/service/purchase/TicketPurchaseService.java`
- 修改：`backend/services/ticket-service/src/test/java/com/lzj/railway/ticket/service/purchase/TicketPurchaseServiceTest.java`

- [ ] 已支付（10）和部分退款（11）订单均可发起退款。
- [ ] 整单退款只提交尚未退款的已支付明细；部分退款继续严格拒绝不存在、重复或已退款的明细。
- [ ] 为部分退款后的整单退款补充单元测试。
- [ ] 编译并提交 `fix: allow remaining tickets to be refunded`。
