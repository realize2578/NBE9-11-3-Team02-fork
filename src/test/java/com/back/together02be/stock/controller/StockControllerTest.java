package com.back.together02be.stock.controller;

import static org.mockito.Mockito.*;
		import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
		import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.back.together02be.stock.dto.response.StockListRes;
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

import java.util.List;

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
	private static final String ALL_STOCKS_URI = "/api/stocks";

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

	// 전체 종목 조회 REST 테스트 (ST-01)
	@Test
	@DisplayName("전체 종목을 요청하면 200 OK와 함께 종목 리스트를 반환한다")
	void getStocks_success() throws Exception {
		// given
		List<StockListRes> response = List.of(
				new StockListRes(1L, "005930", "삼성전자", 70000L, 2.19),
				new StockListRes(2L, "000660", "SK하이닉스", 180000L, -1.12)
		);
		when(stockService.getStocks()).thenReturn(response);

		// when & then
		mockMvc.perform(get(ALL_STOCKS_URI))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].stockCode").value("005930"))
				.andExpect(jsonPath("$[0].stockName").value("삼성전자"))
				.andExpect(jsonPath("$[0].currentPrice").value(70000))
				.andExpect(jsonPath("$[0].changeRate").value(2.19))
				.andExpect(jsonPath("$[1].stockCode").value("000660"));
	}
}
