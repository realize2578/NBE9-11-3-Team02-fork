package com.back.together02be.trade;

import com.back.together02be.asset.entity.UserAccount;
import com.back.together02be.asset.entity.UserStock;
import com.back.together02be.asset.repository.UserAccountRepository;
import com.back.together02be.asset.repository.UserStockRepository;
import com.back.together02be.global.util.JwtUtil;
import com.back.together02be.stock.dto.RealtimeStockPrice;
import com.back.together02be.stock.service.RealTimeStockPriceStore;
import com.back.together02be.trade.controller.TradeController;
import com.back.together02be.trade.util.MarketTimeValidator;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.*;
import org.mockito.MockedStatic;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.Mockito.mockStatic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Transactional
public class TradeControllerSellTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private UserStockRepository userStockRepository;

    @Autowired
    private UserAccountRepository userAccountRepository;

    @Autowired
    private RealTimeStockPriceStore realtimeStockPriceService;

    @Value("${jwt.secret}")
    private String jwtSecret;

    private String accessToken;

    //마켓 시간 모킹(장 운영 시간이 아니여도 테스트 가능)
    private static MockedStatic<MarketTimeValidator> mockedValidator;

    @BeforeAll
    static void beforeAll() {
        mockedValidator = mockStatic(MarketTimeValidator.class);
    }

    @AfterAll
    static void afterAll() {
        if (mockedValidator != null) {
            mockedValidator.close();
        }
    }

    @BeforeEach
    void setUp() {
        //호출시 아무 일도 하지 않도록 설정
        mockedValidator.when(MarketTimeValidator::validateMarketOpen)
                .thenAnswer(invocation -> null);

        accessToken = JwtUtil.generateAccessToken(
                jwtSecret,
                60 * 60,  // 1시간
                Map.of(
                        "id", 1L,
                        "username", "testuser",   // DB에 실제 존재하는 값
                        "nickname", "테스터"       // DB에 실제 존재하는 값
                )
        );


        // 2. 데이터 초기화 (수량 10, 잔액 100만, 총매입 70만)
        UserStock userStock = userStockRepository.findByUsersIdAndStockId(1L, 1L)
                .orElseThrow(() -> new RuntimeException("테스트용 UserStock 데이터가 없습니다."));

        UserAccount userAccount = userAccountRepository.findByUsersId(1L)
                .orElseThrow(() -> new RuntimeException("테스트용 UserAccount 데이터가 없습니다."));

        // [UserStock] 메서드 사용: 수량을 강제로 10으로 변경
        userStock.updateQuantity(10L);

        // [UserAccount] 메서드 사용: 현재 잔액이 얼마든 1,000,000으로 맞추기
        // (목표값 - 현재값)을 더해버리면 현재값이 무엇이든 목표값이 됩니다.
        userAccount.addDeposit(1000000L - userAccount.getDeposit());

        // 총 매입 금액도 700,000으로 맞추기
        // 별도의 set 메서드가 없으므로 기존 값을 빼고 목표값을 더하는 식으로 처리
        userAccount.subtractTotalPurchase(userAccount.getTotalPurchase()); // 0으로 만듦
        userAccount.increaseTotalPurchase(700000L); // 70만으로 설정

        // DB 반영
        userStockRepository.saveAndFlush(userStock);
        userAccountRepository.saveAndFlush(userAccount);


        // 테스트용 실시간 가격 데이터 주입 (삼성전자 등)
        RealtimeStockPrice samsungPrice = RealtimeStockPrice.builder()
                .stockCode("005930")
                .price("75000")
                .changeSign("1")
                .change("1")
                .changeRate("3")
                .tradeTime(LocalTime.now().format(DateTimeFormatter.ofPattern("HHmmss")))
                .build();
        realtimeStockPriceService.put("005930", samsungPrice);
    }

    @Test
    @DisplayName("매도 성공 - 부분 매도")
    void t1() throws Exception {

        ResultActions result = mvc
                .perform(
                        post("/api/trades/sell")
                                .contentType(MediaType.APPLICATION_JSON)
                                .header("Authorization", "Bearer " + accessToken)
                                .header("X-Idempotency-Key", UUID.randomUUID().toString())
                                .content("""
                                        {
                                            "userId": 1,
                                            "stockId": 1,
                                            "quantity": 5,
                                            "expectedPrice": 75000
                                        }
                                        """)
                )
                .andDo(print());

        result
                .andExpect(handler().handlerType(TradeController.class))
                .andExpect(handler().methodName("sell"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("매도가 완료되었습니다."))
                .andExpect(jsonPath("$.data").exists());

        // DB 상태 검증
        UserStock userStock = userStockRepository
                .findByUsersIdAndStockId(1L, 1L)
                .orElseThrow();
        UserAccount userAccount = userAccountRepository
                .findByUsersId(1L)
                .orElseThrow();

        assertThat(userStock.getQuantity()).isEqualTo(5L);              // 10 - 5
        assertThat(userAccount.getDeposit()).isEqualTo(1375000L);       // 100만 + 75000*5
        assertThat(userAccount.getTotalPurchase()).isEqualTo(350000L);  // 70만 - 70000*5
    }

    @Test
    @DisplayName("매도 성공 - 전량 매도 시 UserStock 삭제")
    void t2() throws Exception {
        ResultActions result = mvc
                .perform(
                        post("/api/trades/sell")
                                .contentType(MediaType.APPLICATION_JSON)
                                .header("Authorization", "Bearer " + accessToken)
                                .header("X-Idempotency-Key", UUID.randomUUID().toString())
                                .content("""
                                        {
                                            "userId": 1,
                                            "stockId": 1,
                                            "quantity": 10,
                                            "expectedPrice": 75000
                                        }
                                        """)
                )
                .andDo(print());

        result
                .andExpect(handler().handlerType(TradeController.class))
                .andExpect(handler().methodName("sell"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("매도가 완료되었습니다."))
                .andExpect(jsonPath("$.data").exists());


        // DB 상태 검증 - UserStock 삭제 확인
        Optional<UserStock> deleted = userStockRepository
                .findByUsersIdAndStockId(1L, 1L);
        UserAccount userAccount = userAccountRepository
                .findByUsersId(1L)
                .orElseThrow();

        assertThat(deleted).isEmpty();                                  // 전량매도 → 삭제
        assertThat(userAccount.getDeposit()).isEqualTo(1750000L);       // 100만 + 75000*10
        assertThat(userAccount.getTotalPurchase()).isEqualTo(0L);       // 70만 - 70000*10
    }

    @Test
    @DisplayName("매도 성공 - 손실 매도 (현재가 < 평단가)")
    void t3() throws Exception {
        RealtimeStockPrice lossPrice = RealtimeStockPrice.builder()
                .stockCode("005930")
                .price("60000")
                .changeSign("1")
                .change("1")
                .changeRate("3")
                .tradeTime(LocalTime.now().format(DateTimeFormatter.ofPattern("HHmmss")))
                .build();
        realtimeStockPriceService.put("005930", lossPrice);

        ResultActions result = mvc
                .perform(
                        post("/api/trades/sell")
                                .contentType(MediaType.APPLICATION_JSON)
                                .header("Authorization", "Bearer " + accessToken)
                                .header("X-Idempotency-Key", UUID.randomUUID().toString())
                                .content("""
                                        {
                                            "userId": 1,
                                            "stockId": 1,
                                            "quantity": 1,
                                            "expectedPrice": 60000
                                        }
                                        """)
                )
                .andDo(print());

        result
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("매도가 완료되었습니다."));

        // profit = (60000 - 70000) * 1 = -10000 → Trade에 저장됐는지 확인
        UserStock userStock = userStockRepository
                .findByUsersIdAndStockId(1L, 1L)
                .orElseThrow();

        assertThat(userStock.getQuantity()).isEqualTo(9L);              // 10 - 1
        assertThat(userAccount().getDeposit()).isEqualTo(1060000L);     // 100만 + 60000*1
    }

    // ────────────────────────────────────────────
    // 실패 케이스
    // ────────────────────────────────────────────

    @Test
    @DisplayName("매도 실패 - 보유하지 않은 종목")
    void t4() throws Exception {
        ResultActions result = mvc
                .perform(
                        post("/api/trades/sell")
                                .contentType(MediaType.APPLICATION_JSON)
                                .header("Authorization", "Bearer " + accessToken)
                                .header("X-Idempotency-Key", UUID.randomUUID().toString())
                                .content("""
                                        {
                                            "userId": 1,
                                            "stockId": 999,
                                            "quantity": 1,
                                            "price": 75000
                                        }
                                        """)
                )
                .andDo(print());

        result
                .andExpect(handler().handlerType(TradeController.class))
                .andExpect(handler().methodName("sell"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("주식 정보가 없습니다."));
    }

    @Test
    @DisplayName("매도 실패 - 보유 수량 초과")
    void t5() throws Exception {
        ResultActions result = mvc
                .perform(
                        post("/api/trades/sell")
                                .contentType(MediaType.APPLICATION_JSON)
                                .header("Authorization", "Bearer " + accessToken)
                                .header("X-Idempotency-Key", UUID.randomUUID().toString())
                                .content("""
                                        {
                                            "userId": 1,
                                            "stockId": 1,
                                            "quantity": 11,
                                            "expectedPrice": 75000
                                        }
                                        """)
                )
                .andDo(print());

        result
                .andExpect(handler().handlerType(TradeController.class))
                .andExpect(handler().methodName("sell"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("보유 수량이 부족합니다."));

        // DB 수량 변화 없음 검증
        UserStock userStock = userStockRepository
                .findByUsersIdAndStockId(1L, 1L)
                .orElseThrow();

        assertThat(userStock.getQuantity()).isEqualTo(10L); // 그대로
    }

    @Test
    @DisplayName("매도 실패 - quantity가 0 이하")
    void t6() throws Exception {
        ResultActions result = mvc
                .perform(
                        post("/api/trades/sell")
                                .contentType(MediaType.APPLICATION_JSON)
                                .header("Authorization", "Bearer " + accessToken)
                                .header("X-Idempotency-Key", UUID.randomUUID().toString())
                                .content("""
                                        {
                                            "userId": 1,
                                            "stockId": 1,
                                            "quantity": 0,
                                            "price": 75000
                                        }
                                        """)
                )
                .andDo(print());

        result
                .andExpect(handler().handlerType(TradeController.class))
                .andExpect(handler().methodName("sell"))
                .andExpect(status().isBadRequest());
    }

    private UserAccount userAccount() {
        return userAccountRepository.findByUsersId(1L).orElseThrow();
    }
}
