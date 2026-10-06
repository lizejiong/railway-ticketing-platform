# 本地模拟支付实施计划

> **给 agentic workers：** 使用 `superpowers:executing-plans` 逐项执行。

**目标：** 在不配置真实支付宝密钥或公网回调地址的前提下，允许本地开发完整验证“下单 → 创建支付单 → 确认支付成功 → 订单和车票状态推进”的主链路。

**架构：** 支付域新增 `MOCK` 渠道和仅在 `pay.mock.enabled=true` 时可用的确认接口。创建 MOCK 支付单不访问支付宝；确认接口必须从 JWT 获取当前用户，并再次通过订单服务校验订单归属，再复用既有 `completeAliPay` 和 RocketMQ 支付成功事件。默认开关为关闭。

**技术栈：** Spring Boot 配置绑定、Spring Cloud OpenFeign、RocketMQ、JUnit 5、Mockito。

---

### 任务 1：定义本地 Mock 配置和支付渠道

**文件：**
- 修改：`backend/services/pay-service/src/main/java/com/lzj/railway/pay/common/PaymentChannel.java`
- 新建：`backend/services/pay-service/src/main/java/com/lzj/railway/pay/config/MockPaymentProperties.java`
- 修改：`docs/operations/payment-nacos-config.md`

- [ ] **步骤 1：扩展枚举和默认关闭的配置绑定**

```java
public enum PaymentChannel {
    ALIPAY,
    MOCK
}

@Data
@Configuration
@ConfigurationProperties(prefix = "pay.mock")
public class MockPaymentProperties {
    private boolean enabled;
}
```

- [ ] **步骤 2：补充 Nacos 本地配置说明**

在 `pay-service.yaml` 的 `pay` 下加入：

```yaml
  mock:
    enabled: ${PAY_MOCK_ENABLED:false}
```

### 任务 2：复用支付成功状态机实现模拟确认

**文件：**
- 修改：`backend/services/pay-service/src/main/java/com/lzj/railway/pay/service/PaymentService.java`
- 修改：`backend/services/pay-service/src/main/java/com/lzj/railway/pay/service/impl/PaymentServiceImpl.java`
- 修改：`backend/services/pay-service/src/main/java/com/lzj/railway/pay/controller/PaymentController.java`

- [ ] **步骤 1：在服务接口声明确认方法**

```java
PaymentInfoResponse confirmMockPayment(String paySn);
```

- [ ] **步骤 2：创建 MOCK 支付单时跳过支付宝调用**

`create` 仅在渠道为 `ALIPAY` 时生成 `paymentPage`；`MOCK` 返回现有支付单状态和 `null` 支付页。

- [ ] **步骤 3：实现受控确认逻辑**

```java
if (!mockPaymentProperties.isEnabled()) {
    throw new ClientException(PayErrorCode.MOCK_PAYMENT_DISABLED);
}
// 查询支付单、校验当前用户拥有订单、校验渠道为 MOCK，再复用 completeAliPay。
completeAliPay(paySn, "MOCK-" + paySn, payment.getTotalAmount(), LocalDateTime.now(),
        PaymentStatus.TRADE_SUCCESS.name());
```

- [ ] **步骤 4：修正成功回调的重复消息语义**

只有支付单从 `WAIT_BUYER_PAY` 实际变为 `TRADE_SUCCESS` 时发布 `PaySuccessEvent`；已经成功的重复回调返回成功但不重复发布消息。

- [ ] **步骤 5：公开本地确认端点**

```java
@PostMapping("/mock/{paySn}/success")
public Result<PaymentInfoResponse> confirmMockPayment(@PathVariable String paySn) {
    return Results.success(paymentService.confirmMockPayment(paySn));
}
```

该路径沿用 `/api/pay/**` 网关 JWT 鉴权，不加入匿名回调白名单。

### 任务 3：验证并提交

**文件：**
- 修改：`backend/services/pay-service/src/test/java/com/lzj/railway/pay/service/impl/PaymentServiceImplTest.java`

- [ ] **步骤 1：增加 Mock 成功确认测试**

测试设置 `MockPaymentProperties.enabled=true`，模拟当前用户与其订单，断言确认操作更新支付单并发布一次 `PaySuccessEvent`，且不调用 `AliPayPageChannel`。

- [ ] **步骤 2：运行定向测试**

Run: `mvnw.cmd -pl services/pay-service -am '-Dtest=PaymentServiceImplTest' '-Dsurefire.failIfNoSpecifiedTests=false' test`

预期：`PaymentServiceImplTest` 全部通过。

- [ ] **步骤 3：提交**

```bash
git add -A
git commit -m "feat: add local mock payment channel"
```
