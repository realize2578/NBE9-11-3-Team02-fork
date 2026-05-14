package com.back.together02be.ranking.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;
import org.springframework.test.util.ReflectionTestUtils;

import com.back.together02be.ranking.dto.response.RankingRes;
import com.back.together02be.ranking.entity.Ranking;
import com.back.together02be.ranking.entity.RankingSnapshotType;
import com.back.together02be.ranking.repository.RankingRepository;
import com.back.together02be.users.entity.Users;

@ExtendWith(MockitoExtension.class)
@DisplayName("RankingService - 랭킹 조회 로직 테스트")
class RankingServiceTest {

    @Mock
    private RankingRepository rankingRepository;

    @InjectMocks
    private RankingService rankingService;

    @Test
    @DisplayName("일간 랭킹 조회 - 오늘 DAILY 랭킹을 순위순으로 반환한다")
    void 일간_랭킹_조회_성공() {
        // given
        LocalDate today = LocalDate.now();

        Users user1 = new Users("user1", "password", "투자왕");
        ReflectionTestUtils.setField(user1, "id", 1L);

        Users user2 = new Users("user2", "password", "수익왕");
        ReflectionTestUtils.setField(user2, "id", 2L);

        Ranking ranking1 = new Ranking(
                user1, 1, new BigDecimal("12.34"), 56_170_000L, RankingSnapshotType.DAILY, today
        );
        Ranking ranking2 = new Ranking(
                user2, 2, new BigDecimal("8.50"), 54_250_000L, RankingSnapshotType.DAILY, today
        );

        // when() -> given() 으로 변경
        given(rankingRepository.findRankings(
                eq(RankingSnapshotType.DAILY),
                eq(today),
                any(Sort.class)
        )).willReturn(List.of(ranking1, ranking2));

        // when
        List<RankingRes> result = rankingService.getDailyRankings();

        // then
        assertThat(result).hasSize(2);

        assertThat(result.get(0).userId()).isEqualTo(1L);
        assertThat(result.get(0).nickname()).isEqualTo("투자왕");
        assertThat(result.get(0).rank()).isEqualTo(1);
        assertThat(result.get(0).profitRate()).isEqualByComparingTo("12.34");
        assertThat(result.get(0).totalAsset()).isEqualTo(56_170_000L);

        assertThat(result.get(1).userId()).isEqualTo(2L);
        assertThat(result.get(1).nickname()).isEqualTo("수익왕");
        assertThat(result.get(1).rank()).isEqualTo(2);
    }

    @Test
    @DisplayName("월간 랭킹 조회 - 특정 날짜 MONTHLY 랭킹을 순위순으로 반환한다")
    void 월간_랭킹_조회_성공() {
        // given
        LocalDate snapshotDate = LocalDate.of(2026, 5, 31);

        Users user = new Users("user1", "password", "월간왕");
        ReflectionTestUtils.setField(user, "id", 1L);

        Ranking ranking = new Ranking(
                user, 1, new BigDecimal("15.50"), 57_750_000L, RankingSnapshotType.MONTHLY, snapshotDate
        );

        given(rankingRepository.findRankings(
                eq(RankingSnapshotType.MONTHLY),
                eq(snapshotDate),
                any(Sort.class)
        )).willReturn(List.of(ranking));

        // when
        List<RankingRes> result = rankingService.getMonthlyRankings(snapshotDate);

        // then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).userId()).isEqualTo(1L);
        assertThat(result.get(0).nickname()).isEqualTo("월간왕");
        assertThat(result.get(0).rank()).isEqualTo(1);
        assertThat(result.get(0).profitRate()).isEqualByComparingTo("15.50");
        assertThat(result.get(0).totalAsset()).isEqualTo(57_750_000L);
    }

    @Test
    @DisplayName("일간 랭킹 조회 시 Repository에 rankingPosition 오름차순 정렬 조건을 전달한다")
    void 일간_랭킹_조회_정렬조건_검증() {
        // given
        given(rankingRepository.findRankings(
                eq(RankingSnapshotType.DAILY),
                any(LocalDate.class),
                any(Sort.class)
        )).willReturn(List.of());

        // when
        rankingService.getDailyRankings();

        // then
        ArgumentCaptor<Sort> sortCaptor = ArgumentCaptor.forClass(Sort.class);

        verify(rankingRepository).findRankings(
                eq(RankingSnapshotType.DAILY),
                any(LocalDate.class),
                sortCaptor.capture()
        );

        Sort sort = sortCaptor.getValue();

        assertThat(sort.getOrderFor("rankingPosition")).isNotNull();
        assertThat(sort.getOrderFor("rankingPosition").getDirection()).isEqualTo(Sort.Direction.ASC);
    }
}