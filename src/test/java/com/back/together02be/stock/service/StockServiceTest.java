package com.back.together02be.stock.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.BDDMockito.*;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import com.back.together02be.stock.dto.response.StockListRes;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedConstruction;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.back.together02be.stock.dto.RealtimeStockPrice;
import com.back.together02be.stock.dto.response.StockPriceRes;
import com.back.together02be.stock.entity.Stock;
import com.back.together02be.stock.entity.StockMarket;
import com.back.together02be.stock.repository.StockRepository;

import jakarta.persistence.EntityNotFoundException;

@ExtendWith(MockitoExtension.class)
@DisplayName("StockService 테스트")
class StockServiceTest {

	// Mock

	@Mock
	private StockRepository stockRepository;

	@Mock
	private RealTimeStockPriceStore rtStockPriceStore;

	// SUT

	@InjectMocks
	private StockService stockService;

	// 테스트 픽스처

	private static final String VALID_CODE      = "005930";
	private static final String INVALID_CODE    = "INVALID";
	private static final long   POLL_TIMEOUT_MS = 2_000L;   // 폴링 대기 최대치
	private static final long   SSE_INTERVAL_MS = 500L;     // 실제 상수와 맞춤

	// 헬퍼

	private void givenStockExists(String stockCode) {
		given(stockRepository.findByStockCode(stockCode))
			.willReturn(Optional.of(mock(Stock.class)));
	}

	@Test
	@DisplayName("유효한 종목코드로 조회하면 StockPriceRes를 반환한다")
	void getStockPrice_success() {
		// given
		String stockCode = "005930";
		Stock stock = new Stock(stockCode, "삼성전자", StockMarket.KOSPI);
		ReflectionTestUtils.setField(stock, "id", 1L); // BaseEntity의 id 주입

		when(stockRepository.findByStockCode(stockCode)).thenReturn(Optional.of(stock));

		// when
		StockPriceRes result = stockService.getStockPrice(stockCode);

		// then
		assertThat(result.stockId()).isEqualTo(1L);
		assertThat(result.stockCode()).isEqualTo(stockCode);
		assertThat(result.stockName()).isEqualTo("삼성전자");
	}

	@Test
	@DisplayName("존재하지 않는 종목코드로 조회하면 EntityNotFoundException이 발생한다")
	void getStockPrice_notFound() {
		// given
		String stockCode = "INVALID";
		when(stockRepository.findByStockCode(stockCode)).thenReturn(Optional.empty());

		// when & then
		assertThatThrownBy(() -> stockService.getStockPrice(stockCode))
			.isInstanceOf(EntityNotFoundException.class)
			.hasMessageContaining("존재하지 않는 종목코드입니다");
	}

	@Test
	@DisplayName("존재하지 않는 종목코드면 EntityNotFoundException 이 발생하고 SseEmitter 는 생성되지 않는다")
	void 없는_종목코드_예외() {
		given(stockRepository.findByStockCode(INVALID_CODE)).willReturn(Optional.empty());

		try (MockedConstruction<SseEmitter> mocked = mockConstruction(SseEmitter.class)) {
			assertThatThrownBy(() -> stockService.createSseEmitter(INVALID_CODE))
				.isInstanceOf(EntityNotFoundException.class)
				.hasMessageContaining(INVALID_CODE);

			assertThat(mocked.constructed()).isEmpty();
		}
	}

	@Test
	@DisplayName("유효한 종목코드면 SseEmitter 를 반환하고 onCompletion·onTimeout 콜백이 등록된다")
	void 유효한_종목_emitter_반환_및_콜백_등록() {
		givenStockExists(VALID_CODE);
		given(rtStockPriceStore.get(VALID_CODE)).willReturn(null); // send 호출 방지

		try (MockedConstruction<SseEmitter> mocked = mockConstruction(SseEmitter.class)) {
			SseEmitter result = stockService.createSseEmitter(VALID_CODE);

			assertThat(result).isNotNull();
			SseEmitter emitter = mocked.constructed().get(0);
			verify(emitter, atLeastOnce()).onCompletion(any(Runnable.class));
			verify(emitter, atLeastOnce()).onTimeout(any(Runnable.class));
		}
	}

	@Test
	@DisplayName("emitter 생성 후 스케줄러가 store.get() 을 최소 1회 이상 호출한다")
	void 주기_폴링_발생() throws InterruptedException {
		givenStockExists(VALID_CODE);

		CountDownLatch latch = new CountDownLatch(1);
		doAnswer(inv -> {
			latch.countDown();
			return null;
		}).when(rtStockPriceStore).get(VALID_CODE);

		try (MockedConstruction<SseEmitter> ignored = mockConstruction(SseEmitter.class)) {
			stockService.createSseEmitter(VALID_CODE);

			boolean polled = latch.await(POLL_TIMEOUT_MS, TimeUnit.MILLISECONDS);
			assertThat(polled)
				.as("스케줄러가 %dms 안에 store.get() 을 호출해야 한다", POLL_TIMEOUT_MS)
				.isTrue();
		}
	}

	@Test
	@DisplayName("store 에 해당 종목 가격이 없으면 emitter.send() 를 호출하지 않는다")
	void 가격_null이면_send_미호출() throws Exception {
		givenStockExists(VALID_CODE);

		// get() 이 null 을 반환하면서 latch 를 카운트다운 → 폴링 완료 시점 포착
		CountDownLatch latch = new CountDownLatch(1);
		doAnswer(inv -> {
			latch.countDown();
			return null;            // stockPrice == null → send() 분기 미진입
		}).when(rtStockPriceStore).get(VALID_CODE);

		try (MockedConstruction<SseEmitter> mocked = mockConstruction(SseEmitter.class)) {
			stockService.createSseEmitter(VALID_CODE);
			latch.await(POLL_TIMEOUT_MS, TimeUnit.MILLISECONDS);

			SseEmitter emitter = mocked.constructed().get(0);
			verify(emitter, never()).send(any(Object.class));
		}
	}

	@Test
	@DisplayName("store 에 가격이 있으면 emitter.send(stockPrice) 를 호출한다")
	void 가격_있으면_send_호출() throws Exception {
		givenStockExists(VALID_CODE);
		RealtimeStockPrice price = mock(RealtimeStockPrice.class);

		CountDownLatch getLatch = new CountDownLatch(1);
		doAnswer(inv -> { getLatch.countDown(); return price; })
			.when(rtStockPriceStore).get(VALID_CODE);

		try (MockedConstruction<SseEmitter> mocked = mockConstruction(SseEmitter.class)) {
			stockService.createSseEmitter(VALID_CODE);

			getLatch.await(POLL_TIMEOUT_MS, TimeUnit.MILLISECONDS);
			Thread.sleep(200); // get() 과 send() 는 같은 Runnable — 100% 동일 실행에서 연속 호출

			SseEmitter emitter = mocked.constructed().get(0);
			verify(emitter, atLeastOnce()).send(price);
		}
	}

	@Test
	@DisplayName("send 중 IOException 이 발생하면 emitter.complete() 를 호출한다")
	void IOException_발생시_complete_호출() throws Exception {
		givenStockExists(VALID_CODE);
		RealtimeStockPrice price = mock(RealtimeStockPrice.class);
		given(rtStockPriceStore.get(VALID_CODE)).willReturn(price);

		try (MockedConstruction<SseEmitter> mocked = mockConstruction(SseEmitter.class,
			(emitter, ctx) -> doThrow(new IOException("클라이언트 연결 끊김"))
				.when(emitter).send(price))) { // any() 오버로드 대신 price 인스턴스로 명시

			stockService.createSseEmitter(VALID_CODE);
			Thread.sleep(SSE_INTERVAL_MS * 3); // 첫 폴링(0ms) + IOException 처리 + complete() 여유

			SseEmitter emitter = mocked.constructed().get(0);
			verify(emitter, atLeastOnce()).complete();
		}
	}

	// 전체 종목 조회 (ST-01) 테스트

	@Test
	@DisplayName("전체 종목 조회 - 실시간 캐시값이 있으면 현재가와 등락률을 매핑하여 반환한다")
	void 전체_종목_조회_캐시있음() {
		// given
		Stock stock = new Stock("005930", "삼성전자", StockMarket.KOSPI);
		ReflectionTestUtils.setField(stock, "id", 1L);
		given(stockRepository.findAll()).willReturn(List.of(stock));

		RealtimeStockPrice price = RealtimeStockPrice.builder()
				.stockCode("005930")
				.price("70000")
				.changeRate("2.19")
				.build();
		given(rtStockPriceStore.get("005930")).willReturn(price);

		// when
		List<StockListRes> result = stockService.getStocks();

		// then
		assertThat(result).hasSize(1);
		assertThat(result.get(0).stockCode()).isEqualTo("005930");
		assertThat(result.get(0).currentPrice()).isEqualTo(70000L);
		assertThat(result.get(0).changeRate()).isEqualTo(2.19);
	}

	@Test
	@DisplayName("전체 종목 조회 - 캐시값이 없거나 파싱에 실패하면 null을 반환한다")
	void 전체_종목_조회_캐시없음_및_파싱실패() {
		// given
		Stock stock1 = new Stock("000660", "SK하이닉스", StockMarket.KOSPI);
		ReflectionTestUtils.setField(stock1, "id", 2L);
		Stock stock2 = new Stock("035420", "NAVER", StockMarket.KOSPI);
		ReflectionTestUtils.setField(stock2, "id", 3L);

		given(stockRepository.findAll()).willReturn(List.of(stock1, stock2));

		given(rtStockPriceStore.get("000660")).willReturn(null); // 캐시 없음

		RealtimeStockPrice badPrice = RealtimeStockPrice.builder()
				.stockCode("035420").price("abc").changeRate("rate").build();
		given(rtStockPriceStore.get("035420")).willReturn(badPrice); // 숫자 파싱 실패

		// when
		List<StockListRes> result = stockService.getStocks();

		// then
		assertThat(result).hasSize(2);
		assertThat(result.get(0).currentPrice()).isNull();
		assertThat(result.get(0).changeRate()).isNull();
		assertThat(result.get(1).currentPrice()).isNull();
		assertThat(result.get(1).changeRate()).isNull();
	}

	@Test
	@DisplayName("전체 종목 SSE 연결 시 SseEmitter 를 반환하고 콜백이 등록된다")
	void 전체_종목_SSE_생성_및_콜백_등록() {
		// given & when
		try (MockedConstruction<SseEmitter> mocked = mockConstruction(SseEmitter.class)) {
			SseEmitter result = stockService.createStockListSseEmitter();

			// then
			assertThat(result).isNotNull();
			SseEmitter emitter = mocked.constructed().get(0);
			verify(emitter, atLeastOnce()).onCompletion(any(Runnable.class));
			verify(emitter, atLeastOnce()).onTimeout(any(Runnable.class));
		}
	}
}