package com.lzj.railway.pay.service.impl;

import com.lzj.railway.framework.convention.result.Result;
import com.lzj.railway.framework.starter.user.core.UserContext;
import com.lzj.railway.framework.starter.user.core.UserInfoDTO;
import com.lzj.railway.framework.starter.web.result.Results;
import com.lzj.railway.pay.channel.AliPayPageChannel;
import com.lzj.railway.pay.common.PaymentStatus;
import com.lzj.railway.pay.dao.entity.PayDO;
import com.lzj.railway.pay.dao.entity.RefundDO;
import com.lzj.railway.pay.dao.mapper.PayMapper;
import com.lzj.railway.pay.dao.mapper.RefundMapper;
import com.lzj.railway.pay.dto.request.CreatePaymentRequest;
import com.lzj.railway.pay.dto.response.PaymentResponse;
import com.lzj.railway.pay.mq.PaySuccessEvent;
import com.lzj.railway.pay.mq.RefundSuccessEvent;
import com.lzj.railway.pay.dto.request.RefundPaymentRequest;
import com.lzj.railway.pay.remote.OrderPaymentRemoteService;
import com.lzj.railway.pay.remote.dto.OrderPaymentItemRemoteResponse;
import com.lzj.railway.pay.remote.dto.OrderPaymentRemoteResponse;
import com.lzj.railway.pay.service.PayIdGenerator;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.context.ApplicationEventPublisher;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 支付金额、回调幂等和下游事件的核心行为测试。 */
class PaymentServiceImplTest {
    private final PayMapper payMapper = mock(PayMapper.class);
    private final RefundMapper refundMapper = mock(RefundMapper.class);
    private final OrderPaymentRemoteService orderRemoteService = mock(OrderPaymentRemoteService.class);
    private final PayIdGenerator payIdGenerator = mock(PayIdGenerator.class);
    private final AliPayPageChannel aliPayPageChannel = mock(AliPayPageChannel.class);
    private final RedissonClient redissonClient = mock(RedissonClient.class);
    private final ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
    private final RLock lock = mock(RLock.class);
    private final PaymentServiceImpl service = new PaymentServiceImpl(payMapper, refundMapper, orderRemoteService, payIdGenerator,
            aliPayPageChannel, redissonClient, eventPublisher);

    @BeforeAll
    static void initializeMybatisMetadata() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), "test"), PayDO.class);
    }

    @AfterEach
    void clearUserContext() {
        UserContext.removeUser();
    }

    @Test
    void shouldCreatePaymentWithAmountCalculatedFromOrderItems() {
        UserContext.setUser(UserInfoDTO.builder().userId("10001").username("traveler").build());
        Result<OrderPaymentRemoteResponse> remoteResult = Results.success(new OrderPaymentRemoteResponse(
                "20261005123456000001", "traveler", "G100", 0,
                List.of(new OrderPaymentItemRemoteResponse(1200), new OrderPaymentItemRemoteResponse(2300))));
        when(orderRemoteService.query("20261005123456000001", "traveler")).thenReturn(remoteResult);
        when(redissonClient.getLock(anyString())).thenReturn(lock);
        when(lock.isHeldByCurrentThread()).thenReturn(true);
        when(payMapper.selectOne(any())).thenReturn(null);
        when(payIdGenerator.generate("20261005123456000001")).thenReturn("20261005123456000001");
        when(payMapper.insert(any(PayDO.class))).thenReturn(1);
        when(aliPayPageChannel.createPaymentPage(anyString(), any(), anyString())).thenReturn("<form />");

        PaymentResponse response = service.create(new CreatePaymentRequest("20261005123456000001", "ALIPAY"));

        ArgumentCaptor<PayDO> paymentCaptor = ArgumentCaptor.forClass(PayDO.class);
        verify(payMapper).insert(paymentCaptor.capture());
        assertThat(paymentCaptor.getValue().getTotalAmount()).isEqualTo(3500);
        assertThat(paymentCaptor.getValue().getStatus()).isEqualTo(PaymentStatus.WAIT_BUYER_PAY.name());
        assertThat(response.paymentPage()).isEqualTo("<form />");
        verify(lock).unlock();
    }

    @Test
    void shouldPublishEventAfterSuccessfulPaymentCallback() {
        PayDO payment = new PayDO();
        payment.setPaySn("20261005123456000001");
        payment.setOrderSn("20261005123456000001");
        payment.setChannel("ALIPAY");
        payment.setTotalAmount(3500);
        payment.setStatus(PaymentStatus.WAIT_BUYER_PAY.name());
        when(payMapper.selectOne(any())).thenReturn(payment);
        when(payMapper.update(any(), any())).thenReturn(1);

        boolean completed = service.completeAliPay(payment.getPaySn(), "2026100500000001", 3500,
                LocalDateTime.of(2026, 10, 5, 12, 0), PaymentStatus.TRADE_SUCCESS.name());

        assertThat(completed).isTrue();
        verify(eventPublisher).publishEvent(any(PaySuccessEvent.class));
    }

    @Test
    void shouldPersistRefundSnapshotsAndPublishRefundEvent() {
        PayDO payment = new PayDO();
        payment.setPaySn("20261005123456000001");
        payment.setOrderSn("20261005123456000001");
        payment.setTradeNo("2026100500000001");
        payment.setPayAmount(3500);
        payment.setStatus(PaymentStatus.TRADE_SUCCESS.name());
        when(payMapper.selectOne(any())).thenReturn(payment);
        when(refundMapper.selectList(any())).thenReturn(List.of());
        when(redissonClient.getLock(anyString())).thenReturn(lock);
        when(lock.isHeldByCurrentThread()).thenReturn(true);

        service.refund(new RefundPaymentRequest(payment.getOrderSn(), 10001L, "traveler", 1L, "G100",
                "VNP", "NKH", List.of(new RefundPaymentRequest.RefundPaymentItem(11L, 1200, 1,
                "01", "01A", 1, "ID001", "张三"))));

        verify(aliPayPageChannel).refund(anyString(), anyString(), org.mockito.ArgumentMatchers.eq(1200), anyString());
        verify(refundMapper).insert(any());
        verify(eventPublisher).publishEvent(any(RefundSuccessEvent.class));
        verify(lock).unlock();
    }

    /** 已有成功退款快照时，重复的订单明细不得再次请求第三方退款。 */
    @Test
    void shouldRejectDuplicateRefundedOrderItem() {
        PayDO payment = new PayDO();
        payment.setPaySn("20261005123456000001");
        payment.setOrderSn("20261005123456000001");
        payment.setTradeNo("2026100500000001");
        payment.setPayAmount(3500);
        payment.setStatus(PaymentStatus.TRADE_SUCCESS.name());
        RefundDO previousRefund = new RefundDO();
        previousRefund.setOrderItemId(11L);
        previousRefund.setAmount(1200);
        when(payMapper.selectOne(any())).thenReturn(payment);
        when(refundMapper.selectList(any())).thenReturn(List.of(previousRefund));
        when(redissonClient.getLock(anyString())).thenReturn(lock);
        when(lock.isHeldByCurrentThread()).thenReturn(true);

        assertThatThrownBy(() -> service.refund(new RefundPaymentRequest(payment.getOrderSn(), 10001L, "traveler", 1L,
                "G100", "VNP", "NKH", List.of(new RefundPaymentRequest.RefundPaymentItem(11L, 1200, 1,
                "01", "01A", 1, "ID001", "张三")))))
                .hasFieldOrPropertyWithValue("errorCode", "P000009");

        verify(aliPayPageChannel, org.mockito.Mockito.never()).refund(anyString(), anyString(), any(), anyString());
        verify(lock).unlock();
    }
}
