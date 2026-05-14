package com.back.together02be.asset.controller;

import com.back.together02be.asset.entity.UserAccount;
import com.back.together02be.asset.entity.UserStock;
import com.back.together02be.asset.repository.UserAccountRepository;
import com.back.together02be.asset.repository.UserStockRepository;
import com.back.together02be.global.security.SecurityUser;
import com.back.together02be.infra.kis.rest.KisPriceClient;
import com.back.together02be.infra.kis.rest.dto.KisPriceRes;
import com.back.together02be.infra.kis.rest.service.KisTokenService;
import com.back.together02be.stock.entity.Stock;
import com.back.together02be.stock.repository.StockRepository;
import com.back.together02be.users.entity.Users;
import com.back.together02be.users.repository.UsersRepository;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.BDDMockito.given;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
public class AssetControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private KisTokenService kisTokenService;
    @MockitoBean
    private KisPriceClient kisPriceClient;

    @Autowired
    private UserAccountRepository userAccountRepository;
    @Autowired
    private UsersRepository usersRepository;
    @Autowired
    private UserStockRepository userStockRepository;
    @Autowired
    private StockRepository stockRepository;

    private Users testUser;

    @BeforeEach
    void setUp() {
        // ✅ KisPriceClient Mock 동작 정의
        KisPriceRes.Output mockOutput = new KisPriceRes.Output("70000", "1000", "2", "1.45");
        KisPriceRes mockPriceRes = new KisPriceRes("0", "MCA00000", "정상처리", mockOutput);

        // 실제 메서드명: getCurrentPrice(String token, String stockCode)
        given(kisPriceClient.getCurrentPrice(anyString(), anyString())).willReturn(mockPriceRes);

        // kisTokenService Mock
        given(kisTokenService.getAccessToken()).willReturn("mock-token");
        // 1. 기존 데이터 정리 (이미 DB에 데이터가 많다면 전체 삭제는 위험할 수 있으니 주의)
        userStockRepository.deleteAll();
        userAccountRepository.deleteAll();
        // stockRepository는 BaseInitData가 관리하므로 삭제하지 않습니다.

        // 2. 이미 존재하는 Stock을 조회
        Stock samsung = stockRepository.findByStockCode("005930")
                .orElseThrow(() -> new RuntimeException("삼성전자 데이터가 존재하지 않습니다."));

        Stock hynix = stockRepository.findByStockCode("000660")
                .orElseThrow(() -> new RuntimeException("SK하이닉스 데이터가 존재하지 않습니다."));

        // 3. 테스트에 필요한 유저 데이터만 생성 (필요하다면)
        testUser = usersRepository.save(new Users("user1", "1234", "my_nick"));

        UserAccount testUserAccount = new UserAccount(testUser, 10000L, 1000000L);
        userAccountRepository.save(testUserAccount);

        // 4. 연관관계 설정 후 저장
        UserStock us1 = new UserStock(testUser, samsung, 10L, 5000L);
        UserStock us2 = new UserStock(testUser, hynix, 2L, 10000L);
        userStockRepository.saveAll(List.of(us1, us2));
    }

    @Test
    @DisplayName("총매수금 및 보유 주식 목록 조회 테스트")
    //@WithMockUser(username = "1") // SecurityUser가 ID를 사용한다면 1을 설정
    void test1() throws Exception {
        SecurityUser securityUser = new SecurityUser(
                testUser.getId(),
                testUser.getUsername(),
                testUser.getPassword(),
                testUser.getNickname(),
                List.of(new SimpleGrantedAuthority("ROLE_USER"))
        );
        // 1. URL 수정: "/api/asset/accounts/1" -> "/api/asset/accounts"
        mockMvc.perform(get("/api/asset/accounts").with(user(securityUser)))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalAmount").value(10000L))
                .andExpect(jsonPath("$.data.stocks.length()").value(2));
    }
}
