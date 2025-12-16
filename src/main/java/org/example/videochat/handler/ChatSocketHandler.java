package org.example.videochat.handler;

import org.example.videochat.dto.SignalMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Component
public class ChatSocketHandler extends TextWebSocketHandler {
    private final ObjectMapper objectMapper;
    private static final Logger logger = LoggerFactory.getLogger(ChatSocketHandler.class);

    Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();

    public ChatSocketHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String payload = message.getPayload();
        SignalMessage signalMessage = objectMapper.readValue(payload, SignalMessage.class);

        //add message types?
        handleDirectMessage(signalMessage);
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        sessions.put(session.getId(), session); //issue with reconnection?
        logger.info("Connected: " + session.getId());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessions.remove(session.getId(), session);
        logger.info("Disconnected: " + session.getId() + " with status: " + status);
    }

    private void handleDirectMessage(SignalMessage directMessage) throws Exception {
        String receiverId = directMessage.getMessageReceiver();
        WebSocketSession receiverSession = sessions.get(receiverId);

        if (receiverSession.isOpen()) {
            logger.debug("Receiver Session: " + receiverId);
            String message = objectMapper.writeValueAsString(directMessage);
            receiverSession.sendMessage(new TextMessage(message)); // thread issue, loopback issue?
        }
    }


}
