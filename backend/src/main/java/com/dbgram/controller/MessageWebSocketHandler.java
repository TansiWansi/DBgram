package com.dbgram.controller;

import com.dbgram.domain.messaging.CassandraMessage;
import com.dbgram.service.MessagingService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Base64;

/**
 * WebSocket handler for E2EE messaging.
 * Note: Decryption never happens here.
 */
@Slf4j
@org.springframework.context.annotation.Profile("distributed")
@Component
@RequiredArgsConstructor
public class MessageWebSocketHandler extends TextWebSocketHandler {

    private final MessagingService messagingService;
    private final ObjectMapper objectMapper;
    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        String userId = getUserIdFromSession(session);
        if (userId != null) {
            sessions.put(userId, session);
            log.info("WebSocket connection established for user: {}", userId);
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String userIdStr = getUserIdFromSession(session);
        if (userIdStr == null)
            return;
        UUID senderId = UUID.fromString(userIdStr);

        Map<String, Object> payload = objectMapper.readValue(message.getPayload(), Map.class);
        String action = (String) payload.get("action");
        UUID convoId = UUID.fromString((String) payload.get("convoId"));

        if ("SEND_MESSAGE".equals(action)) {
            byte[] encryptedBody = Base64.getDecoder().decode((String) payload.get("encryptedBody"));
            String messageType = (String) payload.get("messageType");
            String replyToIdStr = (String) payload.get("replyToId");
            UUID replyToId = replyToIdStr != null ? UUID.fromString(replyToIdStr) : null;
            boolean isEphemeral = (Boolean) payload.getOrDefault("isEphemeral", false);
            Integer ttlSeconds = (Integer) payload.get("ttlSeconds");

            CassandraMessage savedMessage = messagingService.sendMessage(
                    convoId, senderId, encryptedBody, messageType, replyToId, isEphemeral, ttlSeconds);

            // Ack to sender
            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(Map.of(
                    "action", "ACK",
                    "messageId", savedMessage.getMessageId().toString()))));
        } else if ("UNDO_MESSAGE".equals(action)) {
            UUID messageId = UUID.fromString((String) payload.get("messageId"));
            boolean undone = messagingService.deleteMessage(convoId, messageId, senderId);
            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(Map.of(
                    "action", "UNDO_ACK",
                    "messageId", messageId.toString(),
                    "success", undone))));
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        String userId = getUserIdFromSession(session);
        if (userId != null) {
            sessions.remove(userId);
            log.info("WebSocket connection closed for user: {}", userId);
        }
    }

    private String getUserIdFromSession(WebSocketSession session) {
        if (session.getPrincipal() != null) {
            return session.getPrincipal().getName(); // Assuming principal is set
        }
        // Fallback or explicit token parsing if we pass token in URL / headers
        // Since we didn't implement a complete handshake interceptor, this is a
        // placeholder.
        var query = session.getUri() != null ? session.getUri().getQuery() : null;
        if (query != null && query.contains("userId=")) {
            return query.split("userId=")[1].split("&")[0];
        }
        return null;
    }
}
