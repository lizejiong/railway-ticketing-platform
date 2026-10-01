package com.lzj.railway.ticket.controller;

import com.lzj.railway.framework.starter.web.handler.GlobalExceptionHandler;
import com.lzj.railway.ticket.dto.response.SeatClassResponse;
import com.lzj.railway.ticket.dto.response.TicketQueryResponse;
import com.lzj.railway.ticket.service.TicketQueryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 票务查询控制器测试。
 */
class TicketQueryControllerTest {

    private TicketQueryService ticketQueryService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        ticketQueryService = mock(TicketQueryService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new TicketQueryController(ticketQueryService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void shouldReturnQueryResult() throws Exception {
        when(ticketQueryService.query(any())).thenReturn(List.of(response()));

        mockMvc.perform(get("/api/ticket/query")
                        .param("departure", "北京南")
                        .param("arrival", "上海虹桥")
                        .param("departureDate", "2026-10-02")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].trainNumber").value("G1"));
    }

    @Test
    void shouldRejectMissingDepartureDate() throws Exception {
        mockMvc.perform(get("/api/ticket/query")
                        .param("departure", "北京南")
                        .param("arrival", "上海虹桥"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false));
    }

    private TicketQueryResponse response() {
        return new TicketQueryResponse(1L, "G1", "北京南", "上海虹桥", "07:00", "11:28", 268L,
                true, true, 0, "0,6", 0,
                List.of(new SeatClassResponse(0, 20, new BigDecimal("553.00"))));
    }
}
