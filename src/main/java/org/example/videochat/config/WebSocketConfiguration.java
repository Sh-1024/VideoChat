package org.example.videochat.config;

import org.example.videochat.handler.ChatSocketHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Component
@EnableWebSocket
public class WebSocketConfiguration implements WebSocketConfigurer {

    ChatSocketHandler chatSocketHandler;

    public WebSocketConfiguration(ChatSocketHandler chatSocketHandler) {
        this.chatSocketHandler = chatSocketHandler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(chatSocketHandler, "/socket").setAllowedOrigins("*");
    }
}
