package com.lzj.railway.ticket.service.purchase;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.lzj.railway.framework.convention.exception.ClientException;
import com.lzj.railway.framework.convention.exception.ServiceException;
import com.lzj.railway.framework.convention.result.Result;
import com.lzj.railway.framework.starter.idempotent.annotation.Idempotent;
import com.lzj.railway.framework.starter.idempotent.enums.IdempotentType;
import com.lzj.railway.framework.starter.user.core.UserContext;
import com.lzj.railway.ticket.common.errorcode.TicketErrorCode;
import com.lzj.railway.ticket.common.constant.TicketStatus;
import com.lzj.railway.ticket.dao.entity.TicketDO;
import com.lzj.railway.ticket.dao.mapper.TicketMapper;
import com.lzj.railway.ticket.dto.request.PurchaseTicketRequest;
import com.lzj.railway.ticket.dto.request.RefundTicketRequest;
import com.lzj.railway.ticket.dto.response.PurchaseTicketResponse;
import com.lzj.railway.ticket.remote.TicketOrderRemoteService;
import com.lzj.railway.ticket.remote.PayRemoteService;
import com.lzj.railway.ticket.remote.UserRemoteService;
import com.lzj.railway.ticket.remote.dto.CancelTicketOrderRemoteRequest;
import com.lzj.railway.ticket.remote.dto.PassengerActualRemoteResponse;
import com.lzj.railway.ticket.remote.dto.TicketOrderItemRemoteResponse;
import com.lzj.railway.ticket.remote.dto.TicketOrderRemoteResponse;
import com.lzj.railway.ticket.remote.dto.RefundPaymentRemoteRequest;
import com.lzj.railway.ticket.remote.dto.RefundPaymentItemRemoteRequest;
import lombok.RequiredArgsConstructor;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.time.LocalDateTime;

/** 提交购票和取消订单的跨服务编排入口。 */
@Service
@RequiredArgsConstructor
public class TicketPurchaseService {
    /** 订单域已支付状态。 */
    private static final int ORDER_STATUS_PAID = 10;
    /** 订单域部分退款状态，仍允许对其余已支付明细发起退款。 */
    private static final int ORDER_STATUS_PARTIAL_REFUND = 11;
    /** 订单明细已支付状态。 */
    private static final int ORDER_ITEM_STATUS_PAID = 10;

    private final PurchaseTicketValidationChain validationChain;
    private final TrainRouteService trainRouteService;
    private final TicketAvailabilityTokenBucket tokenBucket;
    private final TicketPurchaseTransactionService transactionService;
    private final UserRemoteService userRemoteService;
    private final TicketOrderRemoteService ticketOrderRemoteService;
    private final SeatAllocationService seatAllocationService;
    private final RedissonClient redissonClient;
    private final PayRemoteService payRemoteService;
    private final TicketMapper ticketMapper;

    /** 进程内公平锁减少同进程线程争抢，再以 Redisson 公平锁覆盖多实例。 */
    private final Cache<String, ReentrantLock> localLocks = Caffeine.newBuilder()
            .expireAfterAccess(1, TimeUnit.DAYS).build();

    /**
     * 提交购票：校验、令牌预扣、分席别串行锁、实体锁座、写车票并创建订单。
     *
     * <p>幂等键绑定当前用户名，避免同一用户重复点击时重复消耗库存。</p>
     */
    @Idempotent(type = IdempotentType.SPEL,
            uniqueKeyPrefix = "railway:ticket:purchase:",
            key = "T(com.lzj.railway.framework.starter.user.core.UserContext).getUsername()",
            message = "正在执行下单流程，请稍后重试")
    public PurchaseTicketResponse purchase(PurchaseTicketRequest request) {
        String username = currentUsername();
        Long userId = currentUserId();
        PurchaseTicketContext context = validationChain.validate(request);
        List<TrainRouteSegment> affectedSegments = trainRouteService.listAffectedSaleSegments(request.trainId(),
                context.getDepartureName(), context.getArrivalName());
        Map<Integer, Long> seatTypeCounts = request.passengers().stream().collect(Collectors.groupingBy(
                passenger -> passenger.seatType(), Collectors.counting()));
        if (!tokenBucket.takeTokenFromBucket(request.trainId(), context.getDepartureName(), context.getArrivalName(),
                affectedSegments, seatTypeCounts)) {
            tokenBucket.refreshOnTokenInsufficient(request.trainId(), context.getDepartureName(),
                    context.getArrivalName(), seatTypeCounts);
            throw new ClientException(TicketErrorCode.TICKET_SOLD_OUT);
        }
        try {
            List<PassengerActualRemoteResponse> passengers = queryPassengers(username, request);
            return withSeatTypeLocks(request.trainId(), seatTypeCounts.keySet(), () -> transactionService.execute(
                    context, affectedSegments, passengers, userId, username));
        } catch (Throwable exception) {
            tokenBucket.rollbackInBucket(request.trainId(), affectedSegments, seatTypeCounts);
            throw exception;
        }
    }

    /** 取消订单后按订单快照释放受影响区间内的座位与余票令牌。 */
    public void cancel(String orderSn) {
        String username = currentUsername();
        Result<TicketOrderRemoteResponse> queryResult = ticketOrderRemoteService.query(orderSn, username);
        if (queryResult == null || !queryResult.isSuccess() || queryResult.getData() == null) {
            throw new ServiceException(TicketErrorCode.ORDER_SERVICE_FAILED);
        }
        TicketOrderRemoteResponse order = queryResult.getData();
        Result<Void> cancelResult = ticketOrderRemoteService.cancel(new CancelTicketOrderRemoteRequest(orderSn, username));
        if (cancelResult == null || !cancelResult.isSuccess()) {
            throw new ServiceException(TicketErrorCode.ORDER_SERVICE_FAILED);
        }
        releaseOrderResources(order);
    }

    /**
     * 消费延迟消息后关闭超时订单，并释放其占用的实体座位和余票令牌。
     *
     * <p>必须先由订单服务完成条件关闭；若订单已经支付，返回 {@code false} 且不会释放任何库存。</p>
     */
    public void closeExpired(String orderSn) {
        Result<TicketOrderRemoteResponse> queryResult = ticketOrderRemoteService.queryInternal(orderSn);
        if (queryResult == null || !queryResult.isSuccess() || queryResult.getData() == null) {
            throw new ServiceException(TicketErrorCode.ORDER_SERVICE_FAILED);
        }
        Result<Boolean> closeResult = ticketOrderRemoteService.closeExpired(orderSn);
        if (closeResult == null || !closeResult.isSuccess() || closeResult.getData() == null) {
            throw new ServiceException(TicketErrorCode.ORDER_SERVICE_FAILED);
        }
        if (Boolean.TRUE.equals(closeResult.getData())) {
            releaseOrderResources(queryResult.getData());
        }
    }

    /**
     * 按订单明细发起退款；票务域只负责鉴权、筛选可退明细并将不可篡改的金额快照交给支付域。
     */
    public void refund(String orderSn, RefundTicketRequest request) {
        String username = currentUsername();
        Result<TicketOrderRemoteResponse> queryResult = ticketOrderRemoteService.query(orderSn, username);
        if (queryResult == null || !queryResult.isSuccess() || queryResult.getData() == null) {
            throw new ServiceException(TicketErrorCode.ORDER_SERVICE_FAILED);
        }
        TicketOrderRemoteResponse order = queryResult.getData();
        if (!isRefundableOrderStatus(order.status())) {
            throw new ClientException(TicketErrorCode.ORDER_SERVICE_FAILED);
        }
        List<Long> requestedIds = request == null ? List.of() : request.orderItemIds();
        boolean fullRefund = request != null && Integer.valueOf(1).equals(request.type());
        if (order.passengerDetails() == null || order.passengerDetails().isEmpty()) {
            throw new ClientException(TicketErrorCode.ORDER_SERVICE_FAILED);
        }
        Set<Long> requestedIdSet = requestedIds == null ? Set.of() : new HashSet<>(requestedIds);
        List<TicketOrderItemRemoteResponse> items = order.passengerDetails().stream()
                .filter(item -> item.status() != null && item.status() == ORDER_ITEM_STATUS_PAID)
                .filter(item -> fullRefund || requestedIdSet.contains(item.id()))
                .toList();
        // 部分退款必须精确匹配请求明细，防止无效或已退款的明细被静默忽略。
        if (items.isEmpty() || (!fullRefund && items.size() != requestedIdSet.size())) {
            throw new ClientException(TicketErrorCode.ORDER_SERVICE_FAILED);
        }
        RefundPaymentRemoteRequest paymentRequest = new RefundPaymentRemoteRequest(orderSn, order.userId(), username,
                order.trainId(), order.trainNumber(), order.departure(), order.arrival(), items.stream()
                .map(item -> new RefundPaymentItemRemoteRequest(item.id(), item.amount(), item.seatType(),
                        item.carriageNumber(), item.seatNumber(), item.idType(), item.idCard(), item.realName())).toList());
        Result<Void> result = payRemoteService.refund(paymentRequest);
        if (result == null || !result.isSuccess()) {
            throw new ServiceException(TicketErrorCode.ORDER_SERVICE_FAILED);
        }
    }

    /** 判断订单是否仍存在可退款的已支付明细。 */
    private boolean isRefundableOrderStatus(Integer status) {
        return status != null && (status == ORDER_STATUS_PAID || status == ORDER_STATUS_PARTIAL_REFUND);
    }

    /** 根据订单创建时保存的座位快照释放全部受影响区间的资源。 */
    private void releaseOrderResources(TicketOrderRemoteResponse order) {
        ticketMapper.update(null, new LambdaUpdateWrapper<TicketDO>()
                .eq(TicketDO::getOrderSn, order.orderSn())
                .eq(TicketDO::getTicketStatus, TicketStatus.UNPAID)
                .set(TicketDO::getTicketStatus, TicketStatus.CLOSED)
                .set(TicketDO::getUpdateTime, LocalDateTime.now()));
        List<TrainRouteSegment> affectedSegments = trainRouteService.listAffectedSaleSegments(order.trainId(),
                order.departure(), order.arrival());
        List<AllocatedSeat> seats = order.passengerDetails().stream()
                .map(item -> new AllocatedSeat(item.passengerId(), item.seatType(), item.carriageNumber(),
                        item.seatNumber(), item.amount()))
                .toList();
        seatAllocationService.unlock(order.trainId(), seats, affectedSegments);
        Map<Integer, Long> counts = order.passengerDetails().stream().collect(Collectors.groupingBy(
                TicketOrderItemRemoteResponse::seatType, Collectors.counting()));
        tokenBucket.rollbackInBucket(order.trainId(), affectedSegments, counts);
    }

    private List<PassengerActualRemoteResponse> queryPassengers(String username, PurchaseTicketRequest request) {
        List<Long> ids = request.passengers().stream().map(passenger -> passenger.passengerId()).toList();
        Result<List<PassengerActualRemoteResponse>> result = userRemoteService.listPassengers(username, ids);
        if (result == null || !result.isSuccess() || result.getData() == null) {
            throw new ClientException(TicketErrorCode.PASSENGER_NOT_AVAILABLE);
        }
        Map<Long, PassengerActualRemoteResponse> passengerMap = result.getData().stream().collect(Collectors.toMap(
                PassengerActualRemoteResponse::id, Function.identity(), (left, right) -> left));
        if (passengerMap.size() != ids.size() || ids.stream().anyMatch(id -> !passengerMap.containsKey(id))) {
            throw new ClientException(TicketErrorCode.PASSENGER_NOT_AVAILABLE);
        }
        return ids.stream().map(passengerMap::get).toList();
    }

    private <T> T withSeatTypeLocks(Long trainId, Iterable<Integer> seatTypes, java.util.concurrent.Callable<T> callback) {
        List<Integer> orderedSeatTypes = new ArrayList<>();
        seatTypes.forEach(orderedSeatTypes::add);
        orderedSeatTypes.sort(Comparator.naturalOrder());
        List<ReentrantLock> localLockList = new ArrayList<>();
        List<RLock> distributedLockList = new ArrayList<>();
        try {
            for (Integer seatType : orderedSeatTypes) {
                String key = "railway:ticket:purchase:" + trainId + ':' + seatType;
                ReentrantLock localLock = localLocks.get(key, ignored -> new ReentrantLock(true));
                localLock.lock();
                localLockList.add(localLock);
                RLock distributedLock = redissonClient.getFairLock(key);
                distributedLock.lock();
                distributedLockList.add(distributedLock);
            }
            return callback.call();
        } catch (ClientException | ServiceException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new ServiceException("购票编排执行失败", exception, TicketErrorCode.SEAT_LOCK_FAILED);
        } finally {
            for (int index = distributedLockList.size() - 1; index >= 0; index--) {
                RLock lock = distributedLockList.get(index);
                if (lock.isHeldByCurrentThread()) {
                    lock.unlock();
                }
            }
            for (int index = localLockList.size() - 1; index >= 0; index--) {
                localLockList.get(index).unlock();
            }
        }
    }

    private String currentUsername() {
        String username = UserContext.getUsername();
        if (!StringUtils.hasText(username) || !StringUtils.hasText(UserContext.getUserId())) {
            throw new ClientException(TicketErrorCode.AUTHENTICATION_REQUIRED);
        }
        return username;
    }

    /** 从认证上下文读取订单分片使用的数值型用户主键。 */
    private Long currentUserId() {
        try {
            return Long.parseLong(UserContext.getUserId());
        } catch (NumberFormatException exception) {
            throw new ClientException(TicketErrorCode.AUTHENTICATION_REQUIRED);
        }
    }
}
