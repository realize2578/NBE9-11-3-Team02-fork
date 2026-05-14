package com.back.together02be.ranking.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.back.together02be.asset.entity.UserAccount;
import com.back.together02be.asset.entity.UserStock;
import com.back.together02be.asset.repository.UserStockRepository;
import com.back.together02be.stock.dto.RealtimeStockPrice;
import com.back.together02be.stock.entity.Stock;
import com.back.together02be.stock.entity.StockMarket;
import com.back.together02be.stock.service.RealTimeStockPriceStore;
import com.back.together02be.users.entity.Users;

@ExtendWith(MockitoExtension.class)
@DisplayName("RankingAssetCalculator - 유저 자산 계산 로직 테스트")
class RankingAssetCalculatorTest {

    @Mock
    private UserStockRepository userStockRepository;

    @Mock
    private RealTimeStockPriceStore realTimeStockPriceStore;

    @InjectMocks
    private RankingAssetCalculator rankingAssetCalculator;

    @Test
    @DisplayName("실시간 현재가가 정상 문자열이면 파싱된 현재가를 반환한다")
    void 정상_현재가_파싱() {
        RealtimeStockPrice price = RealtimeStockPrice.builder()
                .stockCode("005930").price("70000").build();

        long result = rankingAssetCalculator.extractCurrentPrice(price, 60_000L);
        assertThat(result).isEqualTo(70_000L);
    }

    @Test
    @DisplayName("실시간 현재가가 비정상(문자열 등)이면 평균 매입가를 반환한다")
    void 비정상_현재가_대체() {
        RealtimeStockPrice price = RealtimeStockPrice.builder()
                .stockCode("005930").price("이상한값").build();

        long result = rankingAssetCalculator.extractCurrentPrice(price, 60_000L);
        assertThat(result).isEqualTo(60_000L);
    }

    @Test
    @DisplayName("예수금과 보유 종목(실시간가 적용)의 총합을 정확히 계산한다")
    void 총자산_계산_성공() {
        // given
        Users user = new Users("user1", "password", "투자왕");
        ReflectionTestUtils.setField(user, "id", 1L);
        UserAccount account = new UserAccount(user, 0L, 1_000_000L); // 예수금 100만

        Stock stock = new Stock("005930", "삼성전자", StockMarket.KOSPI);
        UserStock userStock = new UserStock(user, stock, 10L, 60_000L);

        given(userStockRepository.findAllByUsersId(1L)).willReturn(List.of(userStock));
        given(realTimeStockPriceStore.get("005930")).willReturn(
                RealtimeStockPrice.builder().price("70000").build() // 실시간가 7만 (총 70만)
        );

        // when
        long totalAsset = rankingAssetCalculator.calculateTotalAsset(account);

        // then
        assertThat(totalAsset).isEqualTo(1_700_000L); // 100만 + 70만
    }
}