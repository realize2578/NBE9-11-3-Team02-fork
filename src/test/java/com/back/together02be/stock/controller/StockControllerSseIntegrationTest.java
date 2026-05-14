package com.back.together02be.stock.controller;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.back.together02be.global.base.BaseIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.back.together02be.infra.kis.rest.KisPriceClient;
import com.back.together02be.infra.kis.websocket.KisWebSocketClient;
import com.back.together02be.stock.dto.RealtimeStockPrice;
import com.back.together02be.stock.service.RealTimeStockPriceStore;

@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("StockController - SSE 엔드포인트 통합 테스트")
class StockControllerSseIntegrationTest extends BaseIntegrationTest {

	// Mock
	@MockitoBean
	private KisWebSocketClient kisWebSocketClient;

	@MockitoBean
	private KisPriceClient kisPriceClient;

	// 테스트 픽스처
	private static final String SSE_URI      = "/api/stocks/{stockCode}/sse";
	private static final String VALID_CODE   = "005930";
	private static final String INVALID_CODE = "INVALID_99999";
	private static final String ALL_SSE_URI  = "/api/stocks/sse";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private RealTimeStockPriceStore rtStockPriceStore;

	@BeforeEach
	void setUp() {
		RealtimeStockPrice price = RealtimeStockPrice.builder()
			.stockCode(VALID_CODE)
			.price("70000")
			.changeSign("2")
			.change("500")
			.changeRate("0.72")
			.tradeTime("143000")
			.build();
		rtStockPriceStore.put(VALID_CODE, price);
	}

	@Test
	@DisplayName("유효한 종목코드로 SSE 연결 시 text/event-stream 으로 응답한다")
	void 정상_종목_SSE_스트림_수신() throws Exception {
		mockMvc.perform(get(SSE_URI, VALID_CODE)
				.accept(MediaType.TEXT_EVENT_STREAM))
			.andExpect(status().isOk())
			.andExpect(header().string(
				HttpHeaders.CONTENT_TYPE,
				containsString("text/event-stream")));
	}

	@Test
	@DisplayName("존재하지 않는 종목코드로 SSE 요청 시 404 를 반환한다")
	void 없는_종목코드_404() throws Exception {
		mockMvc.perform(get(SSE_URI, INVALID_CODE))
			.andExpect(status().isNotFound());
	}

	// 전체 종목 SSE 통합 테스트 (ST-01)
	@Test
	@DisplayName("전체 종목 SSE 연결 시 text/event-stream 으로 응답한다")
	void 전체_종목_SSE_스트림_수신() throws Exception {
		mockMvc.perform(get(ALL_SSE_URI)
						.accept(MediaType.TEXT_EVENT_STREAM))
				.andExpect(status().isOk())
				.andExpect(header().string(
						HttpHeaders.CONTENT_TYPE,
						containsString("text/event-stream")));
	}
}
