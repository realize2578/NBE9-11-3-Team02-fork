package com.back.together02be.stock.controller;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.back.together02be.stock.dto.response.StockPriceRes;
import com.back.together02be.stock.service.StockService;

import jakarta.persistence.EntityNotFoundException;

@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("StockController 통합 테스트")
class StockControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean                        // @MockBean 아니고 @MockitoBean
	private StockService stockService;

	private static final String STOCK_URI  = "/api/stocks/{stockCode}";
	private static final String VALID_CODE = "005930";

	@Test
	@DisplayName("유효한 종목코드로 요청하면 200 OK와 ApiRes 구조로 응답한다")
	void getStockPrice_success() throws Exception {
		// given
		StockPriceRes response = new StockPriceRes(1L, VALID_CODE, "삼성전자");
		when(stockService.getStockPrice(VALID_CODE)).thenReturn(response);

		// when & then
		mockMvc.perform(get(STOCK_URI, VALID_CODE))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.message").value("주식 정보 조회 완료"))
			.andExpect(jsonPath("$.data.stockId").value(1))
			.andExpect(jsonPath("$.data.stockCode").value(VALID_CODE))
			.andExpect(jsonPath("$.data.stockName").value("삼성전자"));
	}

	@Test
	@DisplayName("존재하지 않는 종목코드로 요청하면 404를 반환한다")
	void getStockPrice_notFound() throws Exception {
		// given
		when(stockService.getStockPrice("INVALID"))
			.thenThrow(new EntityNotFoundException("존재하지 않는 종목코드입니다: INVALID"));

		// when & then
		mockMvc.perform(get(STOCK_URI, "INVALID"))
			.andExpect(status().isNotFound());
	}
}
