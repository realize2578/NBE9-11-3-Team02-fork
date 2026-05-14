package com.back.together02be.config;

import com.back.together02be.infra.kis.StockSubscriptionInitializer;
import com.back.together02be.infra.kis.websocket.KisWebSocketClient;
import org.mockito.Mockito;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TestMockConfig {

    // 스프링이 진짜 kisWebSocketClient를 생성하기 직전에
    // 가짜 객체(Mock)를 대신 밀어 넣어 네트워크 호출(@PostConstruct)을 원천 차단합니다.
    @Bean(name = "kisWebSocketClient")
    public KisWebSocketClient kisWebSocketClient() {
        return Mockito.mock(KisWebSocketClient.class);
    }

    // 동일한 이유로 이벤트 리스너를 가진 Initializer도 가짜로 덮어씌웁니다.
    @Bean(name = "stockSubscriptionInitializer")
    public StockSubscriptionInitializer stockSubscriptionInitializer() {
        return Mockito.mock(StockSubscriptionInitializer.class);
    }
}