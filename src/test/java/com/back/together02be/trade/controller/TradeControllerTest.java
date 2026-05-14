package com.back.together02be.trade.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.back.together02be.global.exception.DuplicateRequestException;
import com.back.together02be.global.security.CustomAuthenticationFilter;
import com.back.together02be.global.security.SecurityUser;
import com.back.together02be.trade.dto.BuyRes;
import com.back.together02be.trade.service.TradeService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class TradeControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    TradeService tradeService;

    @MockitoBean
    CustomAuthenticationFilter jwtAuthFilter;

    @BeforeEach
    void configureFilter() throws Exception {
        // mock 필터가 chain을 통과하도록 설정 (기본 mock은 doFilter를 막아서 DispatcherServlet에 못 도달)
        doAnswer(inv -> {
            ((FilterChain) inv.getArgument(2))
                    .doFilter((ServletRequest) inv.getArgument(0), (ServletResponse) inv.getArgument(1));
            return null;
        }).when(jwtAuthFilter).doFilter(any(), any(), any());
    }

    private SecurityUser mockUser() {
        return new SecurityUser(1L, "testuser", "password", "테스터", List.of());
    }

    @Test
    @DisplayName("정상 매수 요청 — 200 OK")
    void 정상_매수_요청() throws Exception {
        BuyRes response = new BuyRes(1L, "삼성전자", 10L, 70_000L, 700_000L, 49_300_000L);
        when(tradeService.buy(anyLong(), anyString(), any())).thenReturn(response);

        mockMvc.perform(post("/api/trades/buy")
                        .with(user(mockUser()))
                        .header("X-Idempotency-Key", "test-uuid-1234")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "stockId": 1,
                                  "quantity": 10,
                                  "expectedPrice": 70000
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("매수가 완료되었습니다."))
                .andExpect(jsonPath("$.data.tradeId").value(1))
                .andExpect(jsonPath("$.data.stockName").value("삼성전자"))
                .andExpect(jsonPath("$.data.quantity").value(10))
                .andExpect(jsonPath("$.data.price").value(70_000))
                .andExpect(jsonPath("$.data.amount").value(700_000))
                .andExpect(jsonPath("$.data.remainingDeposit").value(49_300_000));
    }

    @Test
    @DisplayName("stockId null — 400 + 검증 메시지")
    void stockId_null_검증_실패() throws Exception {
        mockMvc.perform(post("/api/trades/buy")
                        .with(user(mockUser()))
                        .header("X-Idempotency-Key", "test-uuid-1234")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "stockId": null,
                                  "quantity": 10,
                                  "expectedPrice": 70000
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("종목 ID는 필수입니다."));
    }

    @Test
    @DisplayName("quantity null — 400 + 검증 메시지")
    void quantity_null_검증_실패() throws Exception {
        mockMvc.perform(post("/api/trades/buy")
                        .with(user(mockUser()))
                        .header("X-Idempotency-Key", "test-uuid-1234")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "stockId": 1,
                                  "quantity": null,
                                  "expectedPrice": 70000
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("매수 수량은 필수입니다."));
    }

    @Test
    @DisplayName("quantity 0 — 400 + 검증 메시지")
    void quantity_0_검증_실패() throws Exception {
        mockMvc.perform(post("/api/trades/buy")
                        .with(user(mockUser()))
                        .header("X-Idempotency-Key", "test-uuid-1234")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "stockId": 1,
                                  "quantity": 0,
                                  "expectedPrice": 70000
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("매수 수량은 1 이상이어야 합니다."));
    }

    @Test
    @DisplayName("expectedPrice null — 400 + 검증 메시지")
    void expectedPrice_null_검증_실패() throws Exception {
        mockMvc.perform(post("/api/trades/buy")
                        .with(user(mockUser()))
                        .header("X-Idempotency-Key", "test-uuid-1234")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "stockId": 1,
                                  "quantity": 10,
                                  "expectedPrice": null
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("예상 체결가는 필수입니다."));
    }

    @Test
    @DisplayName("중복 요청 — 409 + 멱등성 메시지")
    void 중복_요청_차단() throws Exception {
        when(tradeService.buy(anyLong(), anyString(), any()))
                .thenThrow(new DuplicateRequestException("이미 처리된 요청입니다."));

        mockMvc.perform(post("/api/trades/buy")
                        .with(user(mockUser()))
                        .header("X-Idempotency-Key", "duplicate-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "stockId": 1,
                                  "quantity": 10,
                                  "expectedPrice": 70000
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("이미 처리된 요청입니다."));
    }
}
