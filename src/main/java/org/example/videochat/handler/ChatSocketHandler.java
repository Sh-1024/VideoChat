package org.example.videochat.handler;

import org.example.videochat.dto.SignalMessage;
import org.example.videochat.model.MessageType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.config.ScheduledTask;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import sun.misc.Signal;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ScheduledFuture;

@Component
public class ChatSocketHandler extends TextWebSocketHandler {
    private final ObjectMapper objectMapper;
    private final TaskScheduler taskScheduler;

    private static final Logger logger = LoggerFactory.getLogger(ChatSocketHandler.class);

    Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();
    Map<String, String> activeCalls = new ConcurrentHashMap<>();
    Map<String, ScheduledFuture<?>> futureDeletes = new ConcurrentHashMap<>();

    public ChatSocketHandler(ObjectMapper objectMapper, TaskScheduler taskScheduler) {
        this.objectMapper = objectMapper;
        this.taskScheduler = taskScheduler;
    }

    @Override
    public void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String payload = message.getPayload();
        SignalMessage signalMessage = objectMapper.readValue(payload, SignalMessage.class);

        switch (signalMessage.getMessageType()) {
            case REGISTER:
                handleRegister(session, signalMessage.getMessageSender());
                break;
            case OFFER:
                handleDirectMessage(signalMessage);
                break;
            case ANSWER:
                addActiveSession(signalMessage);
                handleDirectMessage(signalMessage);
                break;
            case HANGUP:
                removeActiveSession(signalMessage);
                handleDirectMessage(signalMessage);
                break;
            case REJECT:
                handleDirectMessage(signalMessage);
                break;
            case NETWORK_PATH:
                handleDirectMessage(signalMessage);
                break;
            case PING:
                handlePing(session);
                break;
            default:
                logger.error("Unknown message type: {}", signalMessage.getMessageType());

        }
    }


    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws IOException {
        logger.info("Connected, but not registered. ID: " + session.getId());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws IOException{
        String userId = (String) session.getAttributes().get("userId");
        logger.info("Disconnected: " + session.getId() + " with status: " + status);

        if (userId != null) {
            sessions.remove(userId);

            if (activeCalls.containsKey(userId)) {
                logger.info("User " + userId + " disconnected during call. Scheduling cleanup.");

                Instant executionTime = Instant.now().plusSeconds(10);
                ScheduledFuture<?> task = taskScheduler.schedule(() -> {
                    deleteUserInfo(userId);
                }, executionTime);

                futureDeletes.put(userId, task);
            }
        }
    }

    private void handleRegister(WebSocketSession session, String userId) throws IOException {
        session.getAttributes().put("userId", userId);

        sessions.put(userId, session);
        logger.info("Registered user: " + userId);

        ScheduledFuture<?> pendingTask = futureDeletes.remove(userId);
        if (pendingTask != null) {
            pendingTask.cancel(false);
            logger.info("Reconnect successful. Cleanup canceled for: " + userId);
        }

        SignalMessage idMessage = new SignalMessage();
        idMessage.setMessageType(MessageType.ID_ASSIGNED);
        idMessage.setMessageData(userId);
        session.sendMessage(new TextMessage(objectMapper.writeValueAsString(idMessage)));

        if (activeCalls.containsKey(userId)) {
            String partnerId = activeCalls.get(userId);
            logger.info("Restoring call for " + userId + " with " + partnerId);

            SignalMessage reconnectMessage = new SignalMessage();
            reconnectMessage.setMessageType(MessageType.RECONNECT);
            reconnectMessage.setMessageData(partnerId);
            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(reconnectMessage)));
        }
    }

    private void handlePing(WebSocketSession session) throws IOException{
        SignalMessage pong =  new SignalMessage();
        pong.setMessageType(MessageType.PONG);
        pong.setMessageData("server is up");
        session.sendMessage(new TextMessage(objectMapper.writeValueAsString(pong)));
    }

    private void removeActiveSession(SignalMessage message) {
        String sender = message.getMessageSender();
        String receiver = message.getMessageReceiver();
        activeCalls.remove(sender);
        if (receiver != null) activeCalls.remove(receiver);
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

    private void addActiveSession(SignalMessage message) {
        activeCalls.put(message.getMessageSender(), message.getMessageReceiver());
        activeCalls.put(message.getMessageReceiver(), message.getMessageSender());
    }

    private void deleteUserInfo(String userId) {
        try {
            futureDeletes.remove(userId);
            if (activeCalls.containsKey(userId)) {
                String receiverId = activeCalls.get(userId);

                activeCalls.remove(userId);
                activeCalls.remove(receiverId);

                logger.info("Timeout passed. Call terminated: " + userId + " and " + receiverId);

                WebSocketSession receiverSession = sessions.get(receiverId);
                if (receiverSession != null && receiverSession.isOpen()) {
                    SignalMessage hangUpMessage = new SignalMessage();
                    hangUpMessage.setMessageReceiver(receiverId);
                    hangUpMessage.setMessageType(MessageType.HANGUP);
                    hangUpMessage.setMessageData("Partner timeout");
                    receiverSession.sendMessage(new TextMessage(objectMapper.writeValueAsString(hangUpMessage)));
                }
            }
        } catch(IOException e) {
                logger.error("Error scheduling task to delete session for user: " + userId);
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
