package com.back.together02be.trade.processor;

import com.back.together02be.asset.entity.UserAccount;
import com.back.together02be.asset.entity.UserStock;
import com.back.together02be.asset.repository.UserAccountRepository;
import com.back.together02be.asset.repository.UserStockRepository;
import com.back.together02be.global.base.BaseIntegrationTest;
import com.back.together02be.ranking.repository.RankingSeasonRepository;
import com.back.together02be.stock.dto.RealtimeStockPrice;
import com.back.together02be.stock.entity.Stock;
import com.back.together02be.stock.repository.StockRepository;
import com.back.together02be.stock.service.RealTimeStockPriceStore;
import com.back.together02be.trade.dto.request.TradeSellReq;
import com.back.together02be.trade.repository.TradeRepository;
import com.back.together02be.trade.util.MarketTimeValidator;
import com.back.together02be.users.entity.Users;
import com.back.together02be.users.repository.UsersRepository;
import org.junit.jupiter.api.*;
import org.mockito.MockedStatic;
import org.mockito.ScopedMock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.Mockito.mockStatic;
import static org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED;

@SpringBootTest
@Transactional(propagation = NOT_SUPPORTED) // 각 스레드가 독립적인 트랜잭션을 가지도록
class TradeSellProcessorConcurrencyTest extends BaseIntegrationTest {

    @Autowired
    private TradeSellProcessor tradeSellProcessor;

    @Autowired
    private UserAccountRepository userAccountRepository;

    @Autowired
    private UserStockRepository userStockRepository;

    @Autowired
    private TradeRepository tradeRepository;

    @Autowired
    private UsersRepository userRepository;

    @Autowired
    private StockRepository stockRepository;

    @Autowired
    private RealTimeStockPriceStore stockPriceStore;

    @Autowired
    private RankingSeasonRepository rankingSeasonRepository;

    private Long userId;
    private Long stockId;
    private final Long INITIAL_QUANTITY = 100L;
    private final Long INITIAL_DEPOSIT = 1_000_000L;
    private final Long STOCK_PRICE = 10_000L;

    @BeforeEach
    void setUp() {

        // 1. BaseInitData로 이미 저장된 삼성전자 불러오기
        Stock stock = stockRepository.findByStockCode("005930")
                .orElseThrow(() -> new IllegalStateException("삼성전자 종목이 초기 데이터에 없습니다."));
        stockId = stock.getId();

        // 2. 테스트용 유저 생성 (매번 새로 만들어 격리)
        Users user = new Users("testUser_" + System.nanoTime(), "test@test.com", "password");
        userRepository.saveAndFlush(user);
        userId = user.getId();

        // 3. 테스트용 계좌 생성
        UserAccount account = new UserAccount(user, INITIAL_DEPOSIT, STOCK_PRICE * INITIAL_QUANTITY);
        userAccountRepository.saveAndFlush(account);

        // 4. 보유 주식 생성
        UserStock userStock = new UserStock(user, stock, INITIAL_QUANTITY, STOCK_PRICE);
        userStockRepository.saveAndFlush(userStock);

        // 5. 실시간 주가 세팅 (stale 방지 — 현재 시각 기준)
        String currentTime = LocalTime.now().format(DateTimeFormatter.ofPattern("HHmmss"));
        RealtimeStockPrice realtimePrice = RealtimeStockPrice.builder()
                .stockCode("005930")
                .price(String.valueOf(STOCK_PRICE))
                .changeSign("3")
                .change("0")
                .changeRate("0.00")
                .tradeTime(currentTime)
                .build();
        stockPriceStore.put("005930", realtimePrice);
    }

    @AfterEach
    void tearDown() {
        // stock은 BaseInitData 소유이므로 삭제 제외
        tradeRepository.deleteAll();
        userStockRepository.deleteAll();
        userAccountRepository.deleteAll();
        rankingSeasonRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    @DisplayName("동시에 여러 매도 요청 - 총 수량 초과 매도 불가 검증")
    void concurrentSell_shouldNotExceedTotalQuantity() throws InterruptedException {
        // given
        int threadCount = 10;
        long sellQuantityPerThread = 20L; // 각 스레드가 20주씩 매도 시도 (총 200주, 보유는 100주)

        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);
        List<Exception> exceptions = Collections.synchronizedList(new ArrayList<>());

        // when
        for (int i = 0; i < threadCount; i++) {
            final int threadId = i;
            executorService.submit(() -> {
                try {
                    startLatch.await(); // 모든 스레드가 준비될 때까지 대기

                    System.out.println("[Thread-" + threadId + "] 매도 시도 시작 (수량: " + sellQuantityPerThread + ")");

                    TradeSellReq request = new TradeSellReq(null, stockId, sellQuantityPerThread, STOCK_PRICE);
                    tradeSellProcessor.processSell(userId,request);
                    successCount.incrementAndGet();

                    UserStock current = userStockRepository
                            .findByUsersIdAndStockId(userId, stockId)
                            .orElse(null);

                    long remain = current != null ? current.getQuantity() : 0;

                    System.out.println("[Thread-" + threadId + "] ✅ 매도 성공 → 남은 수량: " + remain);

                } catch (IllegalStateException e) {
                    failCount.incrementAndGet();
                    exceptions.add(e);
                } catch (Exception e) {
                    failCount.incrementAndGet();
                    System.out.println("[Thread-" + threadId + "] ❌ 매도 실패 → 이유: " + e.getMessage());

                    exceptions.add(e);
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown(); // 모든 스레드 동시 시작
        doneLatch.await(30, TimeUnit.SECONDS);
        executorService.shutdown();

        // then
        UserStock finalStock = userStockRepository.findByUsersIdAndStockId(userId, stockId).orElse(null);
        long finalQuantity = finalStock != null ? finalStock.getQuantity() : 0L;

        System.out.println("=== 동시성 테스트 결과 ===");
        System.out.println("성공한 매도 수: " + successCount.get());
        System.out.println("실패한 매도 수: " + failCount.get());
        System.out.println("최종 보유 수량: " + finalQuantity);
        System.out.println("실패 이유들: ");
        exceptions.stream()
                .collect(Collectors.groupingBy(e -> e.getMessage(), Collectors.counting()))
                .forEach((msg, count) -> System.out.println("  - " + msg + " : " + count + "건"));

        // 최종 수량이 음수가 되면 안 됨
        assertThat(finalQuantity).isGreaterThanOrEqualTo(0L);

        // 성공한 매도 수량의 합이 초기 보유량을 초과하면 안 됨
        long totalSoldQuantity = successCount.get() * sellQuantityPerThread;
        assertThat(totalSoldQuantity).isLessThanOrEqualTo(INITIAL_QUANTITY);

        // 성공 + 실패 = 전체 스레드
        assertThat(successCount.get() + failCount.get()).isEqualTo(threadCount);
    }

    @Test
    @DisplayName("동시에 전량 매도 요청 - 중복 매도 불가 검증")
    void concurrentFullSell_onlyOneSucceeds() throws InterruptedException {
        // given
        int threadCount = 5;
        long sellQuantity = INITIAL_QUANTITY; // 전량 매도 요청

        System.out.println("\n================= TEST START =================");
        System.out.println("초기 보유 수량: " + INITIAL_QUANTITY + "주");
        System.out.println("각 스레드 요청 수량: " + sellQuantity + "주");
        System.out.println("총 요청 수량: " + (threadCount * sellQuantity) + "주");
        System.out.println("=============================================\n");

        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        // when
        for (int i = 0; i < threadCount; i++) {
            final int threadId = i;
            executorService.submit(() -> {
                try (MockedStatic<MarketTimeValidator> sw = mockStatic(MarketTimeValidator.class)){
                    sw.when(MarketTimeValidator::validateMarketOpen).thenAnswer(inv->null);

                    startLatch.await();

                    System.out.println("[Thread-" + threadId + "] ▶ 전량 매도 시도 (요청 수량: " + sellQuantity + ")");

                    TradeSellReq request = new TradeSellReq(null, stockId, sellQuantity, STOCK_PRICE);
                    tradeSellProcessor.processSell(userId, request);
                    successCount.incrementAndGet();

                    // 성공 후 현재 상태 조회
                    UserStock current = userStockRepository
                            .findByUsersIdAndStockId(userId, stockId)
                            .orElse(null);

                    long remain = current != null ? current.getQuantity() : 0;

                    System.out.println("[Thread-" + threadId + "] ✅ 성공 → 보유 수량: " + remain + "주 (전량 매도 완료)");

                } catch (Exception e) {
                    failCount.incrementAndGet();
                    System.err.println("======= 에러 발생 상세 원인 =======");
                    e.printStackTrace();
                    System.err.println("==================================");
                    UserStock current = userStockRepository
                            .findByUsersIdAndStockId(userId, stockId)
                            .orElse(null);

                    long remain = current != null ? current.getQuantity() : 0;

                    System.out.println("[Thread-" + threadId + "] ❌ 실패 → 보유 수량: " + remain + "주 (이미 매도됨)");

                } finally {
                    doneLatch.countDown();
                }
            });
        }

        System.out.println("=== 모든 스레드 동시 시작 ===");
        startLatch.countDown();
        doneLatch.await(30, TimeUnit.SECONDS);
        executorService.shutdown();

        // then
        System.out.println("\n=== 전량 매도 동시성 테스트 결과 ===");
        System.out.println("초기 보유 수량: " + INITIAL_QUANTITY + "주");
        System.out.println("총 요청 수량: " + (threadCount * sellQuantity) + "주");
        System.out.println("성공한 매도 수: " + successCount.get());
        System.out.println("실패한 매도 수: " + failCount.get());


        // 전량 매도 성공은 단 1번만 가능
        assertThat(successCount.get()).isEqualTo(1);
        assertThat(failCount.get()).isEqualTo(threadCount - 1);

        // UserStock 삭제 확인
        Optional<UserStock> deletedStock = userStockRepository.findByUsersIdAndStockId(userId, stockId);

        long finalQuantity = deletedStock.map(UserStock::getQuantity).orElse(0L);
        System.out.println("최종 보유 수량: " + finalQuantity + "주");

        if (successCount.get() == 1 && finalQuantity == 0) {
            System.out.println("✔️ 결과: 단 1명만 전량 매도 성공 → 중복 매도 완벽 차단");
        } else {
            System.out.println("❌ 결과: 동시성 문제 발생");
        }

        assertThat(deletedStock).isEmpty();
    }
}