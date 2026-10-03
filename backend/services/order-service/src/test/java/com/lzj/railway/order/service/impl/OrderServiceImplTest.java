package com.lzj.railway.order.service.impl;

import com.lzj.railway.order.dao.mapper.OrderItemMapper;
import com.lzj.railway.order.dao.mapper.OrderItemPassengerMapper;
import com.lzj.railway.order.dao.mapper.OrderMapper;
import com.lzj.railway.order.dto.request.TicketOrderCreateRequest;
import com.lzj.railway.order.dto.request.TicketOrderItemCreateRequest;
import com.lzj.railway.order.service.OrderNumberGenerator;
import org.junit.jupiter.api.Test;
import org.redisson.api.RedissonClient;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 订单创建写入三类数据的事务编排测试。 */
class OrderServiceImplTest {

    /** 创建订单时应同时写入主订单、订单明细和乘车人关系。 */
    @Test
    void shouldCreateOrderWithItemsAndPassengerRelations() {
        OrderMapper orderMapper = mock(OrderMapper.class);
        OrderItemMapper itemMapper = mock(OrderItemMapper.class);
        OrderItemPassengerMapper relationMapper = mock(OrderItemPassengerMapper.class);
        OrderNumberGenerator orderNumberGenerator = mock(OrderNumberGenerator.class);
        OrderServiceImpl service = new OrderServiceImpl(orderMapper, itemMapper, relationMapper,
                orderNumberGenerator, mock(RedissonClient.class));
        when(orderNumberGenerator.generate(2105134991746658306L)).thenReturn("202610031200000000121748306");
        when(orderMapper.insert(any())).thenReturn(1);
        when(itemMapper.insert(any())).thenReturn(1);
        when(relationMapper.insert(any())).thenReturn(1);

        String orderSn = service.createTicketOrder(request());

        assertThat(orderSn).isEqualTo("202610031200000000121748306");
        verify(orderMapper).insert(any());
        verify(itemMapper).insert(any());
        verify(relationMapper).insert(any());
    }

    private TicketOrderCreateRequest request() {
        LocalDateTime now = LocalDateTime.of(2026, 10, 3, 12, 0);
        return new TicketOrderCreateRequest(2105134991746658306L, "lisi", 3L, "北京南", "杭州东", 0,
                now, now, "G1", now, now.plusHours(5), List.of(new TicketOrderItemCreateRequest("01", 1,
                "01A", 1001L, "李四", 0, "110101199001011234", "13800138000", 7500, 0)));
    }
}
