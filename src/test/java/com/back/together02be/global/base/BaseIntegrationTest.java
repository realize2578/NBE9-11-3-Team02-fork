package com.back.together02be.global.base;

import com.back.together02be.infra.kis.StockSubscriptionInitializer;
import com.back.together02be.infra.kis.websocket.KisWebSocketClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
public abstract class BaseIntegrationTest {
    @MockitoBean
    protected KisWebSocketClient kisWebSocketClient;

    @MockitoBean
    protected StockSubscriptionInitializer stockSubscriptionInitializer;
}