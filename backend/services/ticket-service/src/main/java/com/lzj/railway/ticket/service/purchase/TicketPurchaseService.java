package com.lzj.railway.ticket.service.purchase;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.lzj.railway.framework.convention.exception.ClientException;
import com.lzj.railway.framework.convention.exception.ServiceException;
import com.lzj.railway.framework.convention.result.Result;
import com.lzj.railway.framework.starter.idempotent.annotation.Idempotent;
import com.lzj.railway.framework.starter.idempotent.enums.IdempotentType;
import com.lzj.railway.framework.starter.user.core.UserContext;
import com.lzj.railway.ticket.common.errorcode.TicketErrorCode;
import com.lzj.railway.ticket.dto.request.PurchaseTicketRequest;
import com.lzj.railway.ticket.dto.response.PurchaseTicketResponse;
import com.lzj.railway.ticket.remote.TicketOrderRemoteService;
import com.lzj.railway.ticket.remote.UserRemoteService;
import com.lzj.railway.ticket.remote.dto.CancelTicketOrderRemoteRequest;
import com.lzj.railway.ticket.remote.dto.PassengerActualRemoteResponse;
import com.lzj.railway.ticket.remote.dto.TicketOrderItemRemoteResponse;
import com.lzj.railway.ticket.remote.dto.TicketOrderRemoteResponse;
import lombok.RequiredArgsConstructor;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;
import java.util.stream.Collectors;

/** 提交购票和取消订单的跨服务编排入口。 */
@Service
@RequiredArgsConstructor
public class TicketPurchaseService {
    private final PurchaseTicketValidationChain validationChain;
    private final TrainRouteService trainRouteService;
    private final TicketAvailabilityTokenBucket tokenBucket;
    private final TicketPurchaseTransactionService transactionService;
    private final UserRemoteService userRemoteService;
    private final TicketOrderRemoteService ticketOrderRemoteService;
    private final SeatAllocationService seatAllocationService;
    private final RedissonClient redissonClient;

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
