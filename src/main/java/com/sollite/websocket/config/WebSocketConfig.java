package com.sollite.websocket.config;

import com.sollite.websocket.controller.AskingWebSocketHandler;
import com.sollite.websocket.controller.ForeignStockQuoteWebSocketHandler;
import com.sollite.websocket.controller.ForeignStockTransactionWebSocketHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketConfigurer {
    private final AskingWebSocketHandler askingWebSocketHandler;
    private final ForeignStockQuoteWebSocketHandler foreignStockQuoteWebSocketHandler;
    private final ForeignStockTransactionWebSocketHandler foreignStockTransactionWebSocketHandler;

    @Value("${app.frontend-url}")
    private String frontendUrl;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(askingWebSocketHandler, "/ws/asking/*")
                .setAllowedOriginPatterns(frontendUrl);
        registry.addHandler(foreignStockQuoteWebSocketHandler, "/ws/foreign/quote/*")
                .setAllowedOriginPatterns(frontendUrl);
        registry.addHandler(foreignStockTransactionWebSocketHandler, "/ws/foreign/transaction/*")
                .setAllowedOriginPatterns(frontendUrl);
    }
}
