package com.lzj.railway.pay.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.lzj.railway.framework.convention.exception.ClientException;
import com.lzj.railway.framework.convention.exception.ServiceException;
import com.lzj.railway.framework.convention.result.Result;
import com.lzj.railway.framework.starter.user.core.UserContext;
import com.lzj.railway.pay.channel.AliPayPageChannel;
import com.lzj.railway.pay.common.PayErrorCode;
import com.lzj.railway.pay.common.PaymentChannel;
import com.lzj.railway.pay.common.PaymentStatus;
import com.lzj.railway.pay.dao.entity.PayDO;
import com.lzj.railway.pay.dao.entity.RefundDO;
import com.lzj.railway.pay.dao.mapper.PayMapper;
import com.lzj.railway.pay.dao.mapper.RefundMapper;
import com.lzj.railway.pay.dto.request.CreatePaymentRequest;
import com.lzj.railway.pay.dto.request.RefundPaymentRequest;
import com.lzj.railway.pay.dto.response.PaymentInfoResponse;
import com.lzj.railway.pay.dto.response.PaymentResponse;
import com.lzj.railway.pay.mq.PaySuccessEvent;
import com.lzj.railway.pay.mq.RefundSuccessEvent;
import com.lzj.railway.pay.remote.OrderPaymentRemoteService;
import com.lzj.railway.pay.remote.dto.OrderPaymentItemRemoteResponse;
import com.lzj.railway.pay.remote.dto.OrderPaymentRemoteResponse;
import com.lzj.railway.pay.service.PayIdGenerator;
import com.lzj.railway.pay.service.PaymentService;
import lombok.RequiredArgsConstructor;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

/** 支付单本地事务与支付宝收银台请求的编排实现。 */
@Service
@RequiredArgsConstructor
public class PaymentServiceImpl implements PaymentService {
    private final PayMapper payMapper;
    private final RefundMapper refundMapper;
    private final OrderPaymentRemoteService orderRemoteService;
    private final PayIdGenerator payIdGenerator;
    private final AliPayPageChannel aliPayPageChannel;
    private final RedissonClient redissonClient;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * 读取订单快照计算金额，在订单粒度加锁后复用未完成支付单，避免并发请求生成多个支付宝交易。
     */
    @Override
    @Transactional(rollbackFor = Throwable.class)
    public PaymentResponse create(CreatePaymentRequest request) {
        String username = requireUsername();
        OrderPaymentRemoteResponse order = loadPayableOrder(request.orderSn(), username);
        int totalAmount = calculateTotalAmount(order.passengerDetails());
        PaymentChannel channel = resolveChannel(request.channel());
        RLock lock = redissonClient.getLock("railway:pay:create:" + request.orderSn());
        lock.lock();
        try {
            PayDO payment = findByOrderSn(request.orderSn());
            if (payment == null) {
                payment = createPayment(order, totalAmount, channel);
            }
            if (PaymentStatus.TRADE_SUCCESS.name().equals(payment.getStatus())) {
                return toResponse(payment, null);
            }
            String paymentPage = aliPayPageChannel.createPaymentPage(payment.getPaySn(), payment.getTotalAmount(),
                    payment.getSubject());
            return toResponse(payment, paymentPage);
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    /** 查询时再次校验订单归属，支付流水号不会被直接暴露为跨用户查询入口。 */
    @Override
    public PaymentInfoResponse queryByOrderSn(String orderSn) {
        loadPayableOrExistingOrder(orderSn, requireUsername());
        PayDO payment = findByOrderSn(orderSn);
        if (payment == null) {
            throw new ClientException(PayErrorCode.PAYMENT_NOT_FOUND);
        }
        return new PaymentInfoResponse(payment.getPaySn(), payment.getOrderSn(), payment.getTotalAmount(),
                payment.getPayAmount(), payment.getChannel(), payment.getStatus(), payment.getGmtPayment());
    }

    /**
     * 使用状态条件更新消除支付宝重复通知；只有首次从待支付变为成功时才会继续发布订单成功事件。
     */
    @Override
    @Transactional(rollbackFor = Throwable.class)
    public boolean completeAliPay(String paySn, String tradeNo, Integer paidAmount, LocalDateTime paymentTime,
                                  String tradeStatus) {
        PayDO payment = payMapper.selectOne(new LambdaQueryWrapper<PayDO>()
                .eq(PayDO::getPaySn, paySn).last("LIMIT 1"));
        if (payment == null || !PaymentStatus.TRADE_SUCCESS.name().equals(tradeStatus)
                || !payment.getTotalAmount().equals(paidAmount)) {
            return false;
        }
        int updated = payMapper.update(null, new LambdaUpdateWrapper<PayDO>()
                .eq(PayDO::getPaySn, paySn)
                .eq(PayDO::getStatus, PaymentStatus.WAIT_BUYER_PAY.name())
                .set(PayDO::getStatus, PaymentStatus.TRADE_SUCCESS.name())
                .set(PayDO::getTradeNo, tradeNo)
                .set(PayDO::getPayAmount, paidAmount)
                .set(PayDO::getGmtPayment, paymentTime)
                .set(PayDO::getUpdateTime, LocalDateTime.now()));
        if (updated == 1 || PaymentStatus.TRADE_SUCCESS.name().equals(payment.getStatus())) {
            eventPublisher.publishEvent(new PaySuccessEvent(payment.getOrderSn(), payment.getPaySn(),
                    payment.getChannel(), paymentTime));
            return true;
        }
        return false;
    }

    /**
     * 以订单维度串行化退款，先校验可退款余额，再调用渠道并保存逐乘车人的退款审计记录。
     */
    @Override
    @Transactional(rollbackFor = Throwable.class)
    public void refund(RefundPaymentRequest request) {
        if (request == null || request.items() == null || request.items().isEmpty()) {
            throw new ClientException(PayErrorCode.PAYMENT_AMOUNT_INVALID);
        }
        if (request.items().stream().anyMatch(item -> item == null || item.orderItemId() == null
                || item.amount() == null || item.amount() <= 0)) {
            throw new ClientException(PayErrorCode.PAYMENT_AMOUNT_INVALID);
        }
        Set<Long> requestedOrderItemIds = request.items().stream()
                .map(RefundPaymentRequest.RefundPaymentItem::orderItemId).collect(java.util.stream.Collectors.toSet());
        if (requestedOrderItemIds.size() != request.items().size()) {
            throw new ClientException(PayErrorCode.REFUND_ITEM_ALREADY_PROCESSED);
        }
        RLock lock = redissonClient.getLock("railway:pay:refund:" + request.orderSn());
        lock.lock();
        try {
            PayDO payment = findByOrderSn(request.orderSn());
            if (payment == null || !PaymentStatus.TRADE_SUCCESS.name().equals(payment.getStatus())) {
                throw new ClientException(PayErrorCode.ORDER_NOT_PAYABLE);
            }
            List<RefundDO> successfulRefunds = refundMapper.selectList(new LambdaQueryWrapper<RefundDO>()
                    .eq(RefundDO::getOrderSn, request.orderSn()).eq(RefundDO::getStatus, 1));
            Set<Long> refundedOrderItemIds = successfulRefunds.stream().map(RefundDO::getOrderItemId)
                    .filter(java.util.Objects::nonNull).collect(java.util.stream.Collectors.toSet());
            if (requestedOrderItemIds.stream().anyMatch(refundedOrderItemIds::contains)) {
                throw new ClientException(PayErrorCode.REFUND_ITEM_ALREADY_PROCESSED);
            }
            int amount = request.items().stream().map(RefundPaymentRequest.RefundPaymentItem::amount)
                    .reduce(0, Math::addExact);
            int refunded = successfulRefunds.stream()
                    .map(RefundDO::getAmount).reduce(0, Math::addExact);
            if (amount <= 0 || refunded + amount > payment.getPayAmount()) {
                throw new ClientException(PayErrorCode.PAYMENT_AMOUNT_INVALID);
            }
            String refundRequestNo = payment.getPaySn() + '-' + System.currentTimeMillis();
            aliPayPageChannel.refund(payment.getPaySn(), payment.getTradeNo(), amount, refundRequestNo);
            LocalDateTime now = LocalDateTime.now();
            for (RefundPaymentRequest.RefundPaymentItem item : request.items()) {
                RefundDO refund = new RefundDO();
                refund.setPaySn(payment.getPaySn()); refund.setOrderSn(request.orderSn()); refund.setOrderItemId(item.orderItemId());
                refund.setTradeNo(payment.getTradeNo());
                refund.setAmount(item.amount()); refund.setUserId(request.userId()); refund.setUsername(request.username());
                refund.setTrainId(request.trainId()); refund.setTrainNumber(request.trainNumber()); refund.setDeparture(request.departure());
                refund.setArrival(request.arrival()); refund.setSeatType(item.seatType()); refund.setIdType(item.idType());
                refund.setIdCard(item.idCard()); refund.setRealName(item.realName()); refund.setStatus(1);
                refund.setRefundTime(now); refund.setCreateTime(now); refund.setUpdateTime(now); refund.setDelFlag(0);
                refundMapper.insert(refund);
            }
            eventPublisher.publishEvent(new RefundSuccessEvent(request.orderSn(), request.trainId(),
                    request.departure(), request.arrival(), request.items()));
        } finally {
            if (lock.isHeldByCurrentThread()) lock.unlock();
        }
    }

    private PayDO createPayment(OrderPaymentRemoteResponse order, int totalAmount, PaymentChannel channel) {
        LocalDateTime now = LocalDateTime.now();
        PayDO payment = new PayDO();
        payment.setPaySn(payIdGenerator.generate(order.orderSn()));
        payment.setOrderSn(order.orderSn());
        payment.setOutOrderSn(payment.getPaySn());
        payment.setOrderRequestId(payment.getPaySn());
        payment.setChannel(channel.name());
        payment.setTradeType("PAGE");
        payment.setSubject("铁路购票-" + order.trainNumber());
        payment.setTotalAmount(totalAmount);
        payment.setStatus(PaymentStatus.WAIT_BUYER_PAY.name());
        payment.setCreateTime(now);
        payment.setUpdateTime(now);
        payment.setDelFlag(0);
        if (payMapper.insert(payment) != 1) {
            throw new ServiceException("支付单创建失败", PayErrorCode.PAYMENT_CHANNEL_FAILED);
        }
        return payment;
    }

    private OrderPaymentRemoteResponse loadPayableOrder(String orderSn, String username) {
        OrderPaymentRemoteResponse order = loadPayableOrExistingOrder(orderSn, username);
        if (order.status() == null || order.status() != 0) {
            throw new ClientException(PayErrorCode.ORDER_NOT_PAYABLE);
        }
        return order;
    }

    private OrderPaymentRemoteResponse loadPayableOrExistingOrder(String orderSn, String username) {
        try {
            Result<OrderPaymentRemoteResponse> result = orderRemoteService.query(orderSn, username);
            if (result == null || !result.isSuccess() || result.getData() == null
                    || !username.equals(result.getData().username())) {
                throw new ClientException(PayErrorCode.ORDER_NOT_FOUND);
            }
            return result.getData();
        } catch (ClientException exception) {
            throw exception;
        } catch (Throwable exception) {
            throw new ServiceException("订单服务调用失败", exception, PayErrorCode.ORDER_NOT_FOUND);
        }
    }

    private int calculateTotalAmount(List<OrderPaymentItemRemoteResponse> details) {
        if (details == null || details.isEmpty()) {
            throw new ClientException(PayErrorCode.PAYMENT_AMOUNT_INVALID);
        }
        if (details.stream().map(OrderPaymentItemRemoteResponse::amount)
                .anyMatch(amount -> amount == null || amount <= 0)) {
            throw new ClientException(PayErrorCode.PAYMENT_AMOUNT_INVALID);
        }
        try {
            return details.stream().map(OrderPaymentItemRemoteResponse::amount)
                    .reduce(0, Math::addExact);
        } catch (ArithmeticException exception) {
            throw new ClientException(PayErrorCode.PAYMENT_AMOUNT_INVALID);
        }
    }

    private PayDO findByOrderSn(String orderSn) {
        return payMapper.selectOne(new LambdaQueryWrapper<PayDO>()
                .eq(PayDO::getOrderSn, orderSn).last("LIMIT 1"));
    }

    private PaymentChannel resolveChannel(String channel) {
        if (!StringUtils.hasText(channel) || PaymentChannel.ALIPAY.name().equalsIgnoreCase(channel)) {
            return PaymentChannel.ALIPAY;
        }
        throw new ClientException(PayErrorCode.PAYMENT_CHANNEL_UNSUPPORTED);
    }

    private String requireUsername() {
        String username = UserContext.getUsername();
        if (!StringUtils.hasText(username)) {
            throw new ClientException(PayErrorCode.AUTHENTICATION_REQUIRED);
        }
        return username;
    }

    private PaymentResponse toResponse(PayDO payment, String paymentPage) {
        return new PaymentResponse(payment.getPaySn(), payment.getOrderSn(), payment.getStatus(), paymentPage);
    }
}
