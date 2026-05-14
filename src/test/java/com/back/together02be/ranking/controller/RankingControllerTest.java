package com.back.together02be.ranking.controller;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.back.together02be.global.base.BaseIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.back.together02be.ranking.dto.response.RankingRes;
import com.back.together02be.ranking.service.RankingSeasonService;
import com.back.together02be.ranking.service.RankingService;
import com.back.together02be.ranking.service.RankingSnapshotService;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("RankingController - 랭킹 API 통합 테스트")
class RankingControllerTest extends BaseIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RankingService rankingService;

    @MockitoBean
    private RankingSnapshotService rankingSnapshotService;

    @MockitoBean
    private RankingSeasonService rankingSeasonService;

    @Test
    @DisplayName("GET /api/rankings - 일간 랭킹을 200 OK와 함께 반환한다")
    void 일간_랭킹_조회_REST() throws Exception {
        // given
        List<RankingRes> response = List.of(
                new RankingRes(1L, "투자왕", 1, new BigDecimal("12.34"), 56_170_000L)
        );
        given(rankingService.getDailyRankings()).willReturn(response);

        // when & then
        mockMvc.perform(get("/api/rankings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].nickname").value("투자왕"))
                .andExpect(jsonPath("$[0].rank").value(1));

        verify(rankingService).getDailyRankings();
    }

    @Test
    @DisplayName("POST /api/rankings/snapshots/daily - 특정 날짜로 DAILY 스냅샷 생성을 트리거한다")
    void DAILY_랭킹_스냅샷_수동생성() throws Exception {
        // given
        LocalDate snapshotDate = LocalDate.of(2026, 5, 14);

        // when & then
        mockMvc.perform(post("/api/rankings/snapshots/daily")
                        .param("snapshotDate", snapshotDate.toString()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("DAILY 랭킹 생성 완료")));

        verify(rankingSnapshotService).createDailySnapshot(snapshotDate);
    }
}