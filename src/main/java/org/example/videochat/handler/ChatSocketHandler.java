package org.example.videochat.handler;

import org.example.videochat.dto.SignalMessage;
import org.example.videochat.model.MessageType;
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

        switch (signalMessage.getMessageType()) {
            case OFFER:
            case ANSWER:
            case NETWORK_PATH:
            case HANGUP:
            case REJECT:
                handleDirectMessage(signalMessage);
                break;
            case PING:
                handlePing(session);
                break;
            default:
                logger.error("Unknown message type: {}", signalMessage.getMessageType());

        }
    }

    private void handlePing(WebSocketSession session) throws IOException{
        SignalMessage pong =  new SignalMessage();
        pong.setMessageType(MessageType.PONG);
        pong.setMessageData("server is up");
        session.sendMessage(new TextMessage(objectMapper.writeValueAsString(pong)));
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        sessions.put(session.getId(), session); //issue with reconnection?
        logger.info("Connected: " + session.getId());

        SignalMessage idMessage = new SignalMessage();
        idMessage.setMessageType(MessageType.ID_ASSIGNED);
        idMessage.setMessageData(session.getId());
        session.sendMessage(new TextMessage(objectMapper.writeValueAsString(idMessage)));
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessions.remove(session.getId(), session);
        logger.info("Disconnected: " + session.getId() + " with status: " + status);
    }

    private void handleDirectMessage(SignalMessage directMessage) throws IOException {
        String receiverId = directMessage.getMessageReceiver();
        WebSocketSession receiverSession = sessions.get(receiverId);

        if (receiverSession != null && receiverSession.isOpen()) {
            logger.info("Receiver Session: " + receiverId);
            String message = objectMapper.writeValueAsString(directMessage);
            receiverSession.sendMessage(new TextMessage(message)); // thread issue, loopback issue?
        } else {
            sendError(directMessage.getMessageSender(), "User not found" + directMessage.getMessageReceiver());
        }
    }

    private void sendError(String targetId, String errorText) throws IOException {
        WebSocketSession session = sessions.get(targetId);
        if (session != null && session.isOpen()) {
            SignalMessage errorMessage = new SignalMessage();
            errorMessage.setMessageType(MessageType.ERROR);
            errorMessage.setMessageData(errorText);
            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(errorMessage)));
        }
    }
}
