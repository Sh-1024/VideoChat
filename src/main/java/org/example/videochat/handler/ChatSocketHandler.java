package org.example.videochat.handler;

import org.example.videochat.dto.SignalMessage;
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
        System.out.println("New Session: " + session.getId()); //change to normal log later
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessions.remove(session.getId(), session);
        System.out.println("Closed Session: " + session.getId() + " with reason: " + status.getReason()); //change to normal log later
    }

    private void handleDirectMessage(SignalMessage directMessage) throws Exception {
        String receiverId = directMessage.getMessageReceiver();
        WebSocketSession receiverSession = sessions.get(receiverId);

        if (receiverSession.isOpen()) {
            System.out.println("ReceiverSession: " + receiverSession.getId()); // change to logs later
            String message = objectMapper.writeValueAsString(directMessage);
            receiverSession.sendMessage(new TextMessage(message)); // thread issue, loopback issue?
        }
    }


}
