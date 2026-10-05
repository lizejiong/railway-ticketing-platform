package com.lzj.railway.order.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.lzj.railway.framework.convention.exception.ClientException;
import com.lzj.railway.framework.convention.exception.ServiceException;
import com.lzj.railway.order.common.OrderErrorCode;
import com.lzj.railway.order.common.OrderItemStatus;
import com.lzj.railway.order.common.OrderStatus;
import com.lzj.railway.order.dao.entity.OrderDO;
import com.lzj.railway.order.dao.entity.OrderItemDO;
import com.lzj.railway.order.dao.entity.OrderItemPassengerDO;
import com.lzj.railway.order.dao.mapper.OrderItemMapper;
import com.lzj.railway.order.dao.mapper.OrderItemPassengerMapper;
import com.lzj.railway.order.dao.mapper.OrderMapper;
import com.lzj.railway.order.dto.request.CancelTicketOrderRequest;
import com.lzj.railway.order.dto.request.TicketOrderCreateRequest;
import com.lzj.railway.order.dto.request.TicketOrderItemCreateRequest;
import com.lzj.railway.order.dto.response.TicketOrderItemResponse;
import com.lzj.railway.order.dto.response.TicketOrderResponse;
import com.lzj.railway.order.mq.DelayedOrderCloseEvent;
import com.lzj.railway.order.service.OrderNumberGenerator;
import com.lzj.railway.order.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

/** 订单创建与取消的本地事务实现。 */
@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {
    private final OrderMapper orderMapper;
    private final OrderItemMapper orderItemMapper;
    private final OrderItemPassengerMapper orderItemPassengerMapper;
    private final OrderNumberGenerator orderNumberGenerator;
    private final RedissonClient redissonClient;
    private final ApplicationEventPublisher eventPublisher;

    /** 一次提交写入主订单、乘车人订单明细与按证件分片的关系记录。 */
    @Override
    @Transactional(rollbackFor = Throwable.class)
    public String createTicketOrder(TicketOrderCreateRequest request) {
        if (!validCreateRequest(request)) {
            throw new ClientException(OrderErrorCode.ORDER_CREATE_FAILED);
        }
        LocalDateTime now = LocalDateTime.now();
        String orderSn = orderNumberGenerator.generate(request.userId());
        OrderDO order = new OrderDO();
        order.setOrderSn(orderSn);
        order.setUserId(request.userId());
        order.setUsername(request.username());
        order.setTrainId(request.trainId());
        order.setTrainNumber(request.trainNumber());
        order.setRidingDate(request.ridingDate());
        order.setDeparture(request.departure());
        order.setArrival(request.arrival());
        order.setDepartureTime(request.departureTime());
        order.setArrivalTime(request.arrivalTime());
        order.setSource(request.source());
        order.setStatus(OrderStatus.PENDING_PAYMENT.getCode());
        order.setOrderTime(request.orderTime());
        order.setCreateTime(now);
        order.setUpdateTime(now);
        order.setDelFlag(0);
        if (orderMapper.insert(order) != 1) {
            throw new ServiceException(OrderErrorCode.ORDER_CREATE_FAILED);
        }
        for (TicketOrderItemCreateRequest item : request.ticketOrderItems()) {
            insertOrderItem(orderSn, request, item, now);
            insertPassengerRelation(orderSn, item, now);
        }
        // 事务提交后再投递，避免消费者读取到尚未提交的订单快照。
        eventPublisher.publishEvent(new DelayedOrderCloseEvent(orderSn));
        return orderSn;
    }

    /** 使用订单号路由后再校验用户名，避免订单明细泄露给其他用户。 */
    @Override
    public TicketOrderResponse queryTicketOrder(String orderSn, String username) {
        OrderDO order = findOrder(orderSn);
        verifyOwner(order, username);
        return toOrderResponse(order);
    }

    /** 内部消息消费者已由服务网络隔离，此处只负责按分片键装配订单快照。 */
    @Override
    public TicketOrderResponse queryTicketOrderInternal(String orderSn) {
        return toOrderResponse(findOrder(orderSn));
    }

    private TicketOrderResponse toOrderResponse(OrderDO order) {
        List<TicketOrderItemResponse> items = orderItemMapper.selectList(new LambdaQueryWrapper<OrderItemDO>()
                        .eq(OrderItemDO::getOrderSn, order.getOrderSn()))
                .stream().map(this::toItemResponse).toList();
        return new TicketOrderResponse(order.getOrderSn(), order.getUserId(), order.getUsername(), order.getTrainId(),
                order.getTrainNumber(), order.getDeparture(), order.getArrival(), order.getDepartureTime(),
                order.getArrivalTime(), order.getStatus(), items);
    }

    /** 用订单粒度的 Redis 锁和状态条件更新，防止取消请求并发重复释放库存。 */
    @Override
    @Transactional(rollbackFor = Throwable.class)
    public void cancelTicketOrder(CancelTicketOrderRequest request) {
        OrderDO order = findOrder(request.orderSn());
        verifyOwner(order, request.username());
        RLock lock = redissonClient.getLock("railway:order:cancel:" + request.orderSn());
        lock.lock();
        try {
            int updatedOrder = orderMapper.update(null, new LambdaUpdateWrapper<OrderDO>()
                    .eq(OrderDO::getOrderSn, request.orderSn())
                    .eq(OrderDO::getStatus, OrderStatus.PENDING_PAYMENT.getCode())
                    .set(OrderDO::getStatus, OrderStatus.CLOSED.getCode())
                    .set(OrderDO::getUpdateTime, LocalDateTime.now()));
            if (updatedOrder != 1) {
                throw new ClientException(OrderErrorCode.ORDER_STATUS_NOT_CANCELLABLE);
            }
            orderItemMapper.update(null, new LambdaUpdateWrapper<OrderItemDO>()
                    .eq(OrderItemDO::getOrderSn, request.orderSn())
                    .eq(OrderItemDO::getStatus, OrderItemStatus.PENDING_PAYMENT.getCode())
                    .set(OrderItemDO::getStatus, OrderItemStatus.CLOSED.getCode())
                    .set(OrderItemDO::getUpdateTime, LocalDateTime.now()));
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    /**
     * 由延迟消息触发关闭。使用原状态作为更新条件，使它与支付成功事件天然互斥。
     */
    @Override
    @Transactional(rollbackFor = Throwable.class)
    public boolean closeExpiredTicketOrder(String orderSn) {
        RLock lock = redissonClient.getLock("railway:order:close:" + orderSn);
        lock.lock();
        try {
            int updatedOrder = orderMapper.update(null, new LambdaUpdateWrapper<OrderDO>()
                    .eq(OrderDO::getOrderSn, orderSn)
                    .eq(OrderDO::getStatus, OrderStatus.PENDING_PAYMENT.getCode())
                    .set(OrderDO::getStatus, OrderStatus.CLOSED.getCode())
                    .set(OrderDO::getUpdateTime, LocalDateTime.now()));
            if (updatedOrder != 1) {
                return false;
            }
            orderItemMapper.update(null, new LambdaUpdateWrapper<OrderItemDO>()
                    .eq(OrderItemDO::getOrderSn, orderSn)
                    .eq(OrderItemDO::getStatus, OrderItemStatus.PENDING_PAYMENT.getCode())
                    .set(OrderItemDO::getStatus, OrderItemStatus.CLOSED.getCode())
                    .set(OrderItemDO::getUpdateTime, LocalDateTime.now()));
            return true;
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    /** 消费退款成功消息，条件更新避免重复消息重复改变明细状态。 */
    @Override
    @Transactional(rollbackFor = Throwable.class)
    public void refundTicketOrder(String orderSn, List<Long> orderItemIds) {
        if (orderItemIds == null || orderItemIds.isEmpty()) return;
        orderItemMapper.update(null, new LambdaUpdateWrapper<OrderItemDO>()
                .eq(OrderItemDO::getOrderSn, orderSn).in(OrderItemDO::getId, orderItemIds)
                .eq(OrderItemDO::getStatus, OrderItemStatus.PAID.getCode())
                .set(OrderItemDO::getStatus, OrderItemStatus.REFUNDED.getCode())
                .set(OrderItemDO::getUpdateTime, LocalDateTime.now()));
    }

    /**
     * 支付回调允许重复投递，因此订单与明细都通过原状态条件更新保证幂等。
     */
    @Override
    @Transactional(rollbackFor = Throwable.class)
    public void confirmTicketOrderPayment(String orderSn, LocalDateTime payTime) {
        RLock lock = redissonClient.getLock("railway:order:payment:" + orderSn);
        lock.lock();
        try {
            int updatedOrder = orderMapper.update(null, new LambdaUpdateWrapper<OrderDO>()
                    .eq(OrderDO::getOrderSn, orderSn)
                    .eq(OrderDO::getStatus, OrderStatus.PENDING_PAYMENT.getCode())
                    .set(OrderDO::getStatus, OrderStatus.PAID.getCode())
                    .set(OrderDO::getPayTime, payTime)
                    .set(OrderDO::getUpdateTime, LocalDateTime.now()));
            if (updatedOrder == 1) {
                orderItemMapper.update(null, new LambdaUpdateWrapper<OrderItemDO>()
                        .eq(OrderItemDO::getOrderSn, orderSn)
                        .eq(OrderItemDO::getStatus, OrderItemStatus.PENDING_PAYMENT.getCode())
                        .set(OrderItemDO::getStatus, OrderItemStatus.PAID.getCode())
                        .set(OrderItemDO::getUpdateTime, LocalDateTime.now()));
            }
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    private void insertOrderItem(String orderSn, TicketOrderCreateRequest request,
                                 TicketOrderItemCreateRequest item, LocalDateTime now) {
        OrderItemDO orderItem = new OrderItemDO();
        orderItem.setOrderSn(orderSn);
        orderItem.setUserId(request.userId());
        orderItem.setUsername(request.username());
        orderItem.setTrainId(request.trainId());
        orderItem.setCarriageNumber(item.carriageNumber());
        orderItem.setSeatType(item.seatType());
        orderItem.setSeatNumber(item.seatNumber());
        orderItem.setRealName(item.realName());
        orderItem.setIdType(item.idType());
        orderItem.setIdCard(item.idCard());
        orderItem.setPhone(item.phone());
        orderItem.setAmount(item.amount());
        orderItem.setTicketType(item.ticketType());
        orderItem.setStatus(OrderItemStatus.PENDING_PAYMENT.getCode());
        orderItem.setCreateTime(now);
        orderItem.setUpdateTime(now);
        orderItem.setDelFlag(0);
        if (orderItemMapper.insert(orderItem) != 1) {
            throw new ServiceException(OrderErrorCode.ORDER_CREATE_FAILED);
        }
    }

    private void insertPassengerRelation(String orderSn, TicketOrderItemCreateRequest item, LocalDateTime now) {
        OrderItemPassengerDO relation = new OrderItemPassengerDO();
        relation.setOrderSn(orderSn);
        relation.setIdType(item.idType());
        relation.setIdCard(item.idCard());
        relation.setCreateTime(now);
        relation.setUpdateTime(now);
        relation.setDelFlag(0);
        if (orderItemPassengerMapper.insert(relation) != 1) {
            throw new ServiceException(OrderErrorCode.ORDER_CREATE_FAILED);
        }
    }

    private OrderDO findOrder(String orderSn) {
        OrderDO order = orderMapper.selectOne(new LambdaQueryWrapper<OrderDO>()
                .eq(OrderDO::getOrderSn, orderSn).last("LIMIT 1"));
        if (order == null) {
            throw new ClientException(OrderErrorCode.ORDER_NOT_FOUND);
        }
        return order;
    }

    private void verifyOwner(OrderDO order, String username) {
        if (!StringUtils.hasText(username) || !username.equals(order.getUsername())) {
            throw new ClientException(OrderErrorCode.ORDER_ACCESS_DENIED);
        }
    }

    private boolean validCreateRequest(TicketOrderCreateRequest request) {
        return request != null && request.userId() != null && StringUtils.hasText(request.username())
                && request.trainId() != null && request.ticketOrderItems() != null && !request.ticketOrderItems().isEmpty();
    }

    private TicketOrderItemResponse toItemResponse(OrderItemDO item) {
        return new TicketOrderItemResponse(item.getId(), item.getCarriageNumber(), item.getSeatType(), item.getSeatNumber(), null,
                item.getRealName(), item.getIdType(), item.getIdCard(), item.getPhone(), item.getAmount(),
                item.getTicketType(), item.getStatus());
    }
}
