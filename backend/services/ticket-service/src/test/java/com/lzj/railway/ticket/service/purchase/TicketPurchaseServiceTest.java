package com.lzj.railway.ticket.service.purchase;

import com.lzj.railway.framework.convention.result.Result;
import com.lzj.railway.framework.convention.exception.ClientException;
import com.lzj.railway.framework.convention.exception.ServiceException;
import com.lzj.railway.framework.starter.user.core.UserContext;
import com.lzj.railway.framework.starter.user.core.UserInfoDTO;
import com.lzj.railway.ticket.dto.request.PurchaseTicketPassengerRequest;
import com.lzj.railway.ticket.dto.request.PurchaseTicketRequest;
import com.lzj.railway.ticket.dto.response.PurchaseTicketResponse;
import com.lzj.railway.ticket.remote.TicketOrderRemoteService;
import com.lzj.railway.ticket.remote.UserRemoteService;
import com.lzj.railway.ticket.remote.PayRemoteService;
import com.lzj.railway.ticket.remote.dto.PassengerActualRemoteResponse;
import com.lzj.railway.ticket.remote.dto.TicketOrderItemRemoteResponse;
import com.lzj.railway.ticket.remote.dto.TicketOrderRemoteResponse;
import com.lzj.railway.ticket.dto.request.RefundTicketRequest;
import com.lzj.railway.ticket.dao.mapper.TicketMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 购票编排中令牌预扣和失败回补的测试。 */
class TicketPurchaseServiceTest {
    private PurchaseTicketValidationChain validationChain;
    private TrainRouteService trainRouteService;
    private TicketAvailabilityTokenBucket tokenBucket;
    private TicketPurchaseTransactionService transactionService;
    private UserRemoteService userRemoteService;
    private TicketOrderRemoteService ticketOrderRemoteService;
    private PayRemoteService payRemoteService;
    private TicketMapper ticketMapper;
    private RedissonClient redissonClient;
    private TicketPurchaseService service;
    private PurchaseTicketRequest request;
    private List<TrainRouteSegment> affectedSegments;

    @BeforeEach
    void setUp() {
        validationChain = mock(PurchaseTicketValidationChain.class);
        trainRouteService = mock(TrainRouteService.class);
        tokenBucket = mock(TicketAvailabilityTokenBucket.class);
        transactionService = mock(TicketPurchaseTransactionService.class);
        userRemoteService = mock(UserRemoteService.class);
        ticketOrderRemoteService = mock(TicketOrderRemoteService.class);
        payRemoteService = mock(PayRemoteService.class);
        ticketMapper = mock(TicketMapper.class);
        redissonClient = mock(RedissonClient.class);
        service = new TicketPurchaseService(validationChain, trainRouteService, tokenBucket, transactionService,
                userRemoteService, ticketOrderRemoteService, mock(SeatAllocationService.class), redissonClient, payRemoteService, ticketMapper);
        UserContext.setUser(UserInfoDTO.builder().userId("2105134991746658306").username("lisi").build());
        request = new PurchaseTicketRequest(3L, "VNP", "NKH", List.of(new PurchaseTicketPassengerRequest(1001L, 1)), List.of());
        PurchaseTicketContext context = new PurchaseTicketContext(request);
        context.setDepartureName("北京南");
        context.setArrivalName("杭州东");
        affectedSegments = List.of(new TrainRouteSegment("北京南", "杭州东"));
        when(validationChain.validate(request)).thenReturn(context);
        when(trainRouteService.listAffectedSaleSegments(3L, "北京南", "杭州东")).thenReturn(affectedSegments);
        when(tokenBucket.takeTokenFromBucket(3L, "北京南", "杭州东", affectedSegments, Map.of(1, 1L))).thenReturn(true);
    }

    @AfterEach
    void tearDown() {
        UserContext.removeUser();
    }

    /** 下游创建订单失败时必须补回已预扣的全部余票令牌。 */
    @Test
    void shouldRollbackTokensWhenDownstreamTransactionFails() {
        when(userRemoteService.listPassengers("lisi", List.of(1001L)))
                .thenReturn(Result.success(List.of(new PassengerActualRemoteResponse(1001L, "李四", 0,
                        "110101199001011234", 0, "13800138000", 0))));
        RLock lock = mock(RLock.class);
        when(redissonClient.getFairLock("railway:ticket:purchase:3:1")).thenReturn(lock);
        when(lock.isHeldByCurrentThread()).thenReturn(true);
        when(transactionService.execute(any(), eq(affectedSegments), any(), eq(2105134991746658306L), eq("lisi")))
                .thenThrow(new IllegalStateException("order unavailable"));

        assertThatThrownBy(() -> service.purchase(request)).isInstanceOf(ServiceException.class);

        verify(tokenBucket).rollbackInBucket(3L, affectedSegments, Map.of(1, 1L));
    }

    /** 成功时保留预扣令牌，并返回事务层创建的订单结果。 */
    @Test
    void shouldReturnOrderAfterTransactionSucceeds() {
        when(userRemoteService.listPassengers("lisi", List.of(1001L)))
                .thenReturn(Result.success(List.of(new PassengerActualRemoteResponse(1001L, "李四", 0,
                        "110101199001011234", 0, "13800138000", 0))));
        RLock lock = mock(RLock.class);
        when(redissonClient.getFairLock("railway:ticket:purchase:3:1")).thenReturn(lock);
        when(lock.isHeldByCurrentThread()).thenReturn(true);
        PurchaseTicketResponse expected = new PurchaseTicketResponse("202610031200000000121748306", List.of());
        when(transactionService.execute(any(), eq(affectedSegments), any(), eq(2105134991746658306L), eq("lisi")))
                .thenReturn(expected);

        assertThat(service.purchase(request)).isEqualTo(expected);
    }

    /** 令牌不足仍立即返回售罄，同时只触发后续请求可用的受控缓存核验。 */
    @Test
    void shouldScheduleTokenRefreshWhenTokenBucketIsInsufficient() {
        when(tokenBucket.takeTokenFromBucket(3L, "北京南", "杭州东", affectedSegments, Map.of(1, 1L)))
                .thenReturn(false);

        assertThatThrownBy(() -> service.purchase(request)).isInstanceOf(ClientException.class)
                .hasFieldOrPropertyWithValue("errorCode", "T000008");

        verify(tokenBucket).refreshOnTokenInsufficient(3L, "北京南", "杭州东", Map.of(1, 1L));
    }

    /** 部分退款后的整单退款只应提交尚未退款的已支付明细。 */
    @Test
    void shouldRefundRemainingPaidItemsForPartiallyRefundedOrder() {
        TicketOrderItemRemoteResponse refundedItem = new TicketOrderItemRemoteResponse(101L, "01", 1, "01A",
                1001L, "张三", 0, "110101199001011234", "13800138000", 10000, 0, 12);
        TicketOrderItemRemoteResponse paidItem = new TicketOrderItemRemoteResponse(102L, "01", 1, "01B",
                1002L, "李四", 0, "110101199001011235", "13800138001", 10000, 0, 10);
        TicketOrderRemoteResponse order = new TicketOrderRemoteResponse("202610060000000001", 2105134991746658306L,
                "lisi", 3L, "G1", "北京南", "杭州东", null, null, 11, List.of(refundedItem, paidItem));
        when(ticketOrderRemoteService.query("202610060000000001", "lisi")).thenReturn(Result.success(order));
        when(payRemoteService.refund(any())).thenReturn(Result.success());

        service.refund("202610060000000001", new RefundTicketRequest(1, List.of()));

        verify(payRemoteService).refund(org.mockito.ArgumentMatchers.argThat(request -> request.items().size() == 1
                && request.items().get(0).orderItemId().equals(102L)));
    }
}
