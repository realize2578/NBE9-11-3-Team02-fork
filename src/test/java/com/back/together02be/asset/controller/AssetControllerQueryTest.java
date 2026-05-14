package com.back.together02be.asset.controller;

import com.back.together02be.asset.dto.response.UserStockRes;
import com.back.together02be.asset.service.AssetService;
import com.back.together02be.global.security.CustomAuthenticationFilter;
import com.back.together02be.global.security.SecurityUser;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AssetControllerQueryTest {

    @Autowired MockMvc mockMvc;

    @MockitoBean AssetService assetService;
    @MockitoBean CustomAuthenticationFilter jwtAuthFilter;

    @BeforeEach
    void configureFilter() throws Exception {
        // 가짜 필터체인 무조건 통과하도록 설정
        doAnswer(inv -> {
            ((FilterChain) inv.getArgument(2))
                    .doFilter((ServletRequest) inv.getArgument(0), (ServletResponse) inv.getArgument(1));
            return null;
        }).when(jwtAuthFilter).doFilter(any(), any(), any());
    }
    // 가짜 유저 생성
    private SecurityUser mockUser() {
        return new SecurityUser(1L, "testuser", "password", "테스터", List.of());
    }

    @Test
    @DisplayName("보유 종목 조회 정상 요청 — 200 OK 및 JSON 규격 검증")
    void getUserStocks_ReturnsOk() throws Exception {
        // Service 응답 Mocking
        UserStockRes mockRes = new UserStockRes("005930", "삼성전자", 10L, 50000L, 75000L); // DTO 생성자 스펙에 맞춰 수정 필요
        when(assetService.getUserStocks(anyLong())).thenReturn(List.of(mockRes));

        // 실제 Controller에 맵핑된 URL로 수정이 필요할 수 있습니다.
        mockMvc.perform(get("/api/asset/stocks")
                        .with(user(mockUser()))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                // 만약 공통 ApiRes 래퍼를 사용한다면 "$.data[0].stockCode" 형식으로 검증합니다.
                .andExpect(jsonPath("$.data[0].stockCode").value("005930"))
                .andExpect(jsonPath("$.data[0].quantity").value(10))
                .andExpect(jsonPath("$.data[0].currentPrice").value(75000));
    }
}