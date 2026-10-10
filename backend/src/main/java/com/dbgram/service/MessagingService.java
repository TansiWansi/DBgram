package com.dbgram.service;

import com.dbgram.domain.messaging.CassandraMessage;
import com.dbgram.kafka.KafkaEventPublisher;
import com.dbgram.repository.MessageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.ByteBuffer;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * E2EE messaging service — stores and routes encrypted blobs.
 * The server NEVER decrypts message content — it only persists and delivers
 * the client-side encrypted payloads.
 *
 * Supports:
 * - Encrypted message storage in Cassandra
 * - Ephemeral messages with TTL-based auto-expiry
 * - 20-second undo window via Redis soft-delete
 * - Delivery and read receipts
 */
@Slf4j
@org.springframework.context.annotation.Profile("distributed")
@Service
@RequiredArgsConstructor
public class MessagingService {

    private final MessageRepository messageRepository;
    private final KafkaEventPublisher eventPublisher;
    private final StringRedisTemplate redisTemplate;

    private static final int UNDO_WINDOW_SECONDS = 20;

    /**
     * Store an encrypted message — the encrypted body comes pre-encrypted from the client.
     */
    public CassandraMessage sendMessage(UUID convoId, UUID senderId,
                                         byte[] encryptedBody, String messageType,
                                         UUID replyToId, boolean isEphemeral, Integer ttlSeconds) {
        UUID messageId = UUID.randomUUID();

        CassandraMessage message = CassandraMessage.builder()
                .convoId(convoId)
                .messageId(messageId)
                .senderId(senderId)
                .encryptedBody(ByteBuffer.wrap(encryptedBody))
                .messageType(messageType)
                .replyToId(replyToId)
                .isEphemeral(isEphemeral)
                .ttlSeconds(ttlSeconds)
                .isDeleted(false)
                .createdAt(Instant.now())
                .build();

        messageRepository.save(message);

        // Set up undo window in Redis
        String undoKey = "msg:undo:" + messageId;
        redisTemplate.opsForValue().set(undoKey, senderId.toString(),
                Duration.ofSeconds(UNDO_WINDOW_SECONDS));

        // Publish event for real-time delivery via WebSocket
        eventPublisher.publishMessageSent(convoId, senderId, messageId);

        log.debug("Message {} sent in conversation {} by {}", messageId, convoId, senderId);
        return message;
    }

    /**
     * Get messages in a conversation, ordered by creation time (newest first).
     */
    public List<CassandraMessage> getMessages(UUID convoId, int limit) {
        return messageRepository.findByConvoId(convoId)
                .stream()
                .filter(m -> !Boolean.TRUE.equals(m.getIsDeleted()))
                .limit(limit)
                .toList();
    }

    /**
     * Soft-delete a message within the undo window.
     * If the undo window has expired, the message is permanently marked as deleted.
     */
    public boolean deleteMessage(UUID convoId, UUID messageId, UUID userId) {
        String undoKey = "msg:undo:" + messageId;
        String storedSenderId = redisTemplate.opsForValue().get(undoKey);

        // Verify the user is the sender
        if (storedSenderId != null && storedSenderId.equals(userId.toString())) {
            // Within undo window — soft-delete from Cassandra
            messageRepository.findByConvoIdAndMessageId(convoId, messageId)
                    .ifPresent(msg -> {
                        msg.setIsDeleted(true);
                        msg.setDeletedAt(Instant.now());
                        messageRepository.save(msg);
                    });
            redisTemplate.delete(undoKey);
            log.info("Message {} deleted within undo window by {}", messageId, userId);
            return true;
        }

        // Outside undo window — still mark as deleted but it's permanent
        messageRepository.findByConvoIdAndMessageId(convoId, messageId)
                .ifPresent(msg -> {
                    if (msg.getSenderId().equals(userId)) {
                        msg.setIsDeleted(true);
                        msg.setDeletedAt(Instant.now());
                        messageRepository.save(msg);
                    }
                });
        return false;
    }

    /**
     * Mark a message as delivered.
     */
    public void markDelivered(UUID convoId, UUID messageId) {
        messageRepository.findByConvoIdAndMessageId(convoId, messageId)
                .ifPresent(msg -> {
                    msg.setDeliveredAt(Instant.now());
                    messageRepository.save(msg);
                });
    }

    /**
     * Mark a message as read.
     */
    public void markRead(UUID convoId, UUID messageId) {
        messageRepository.findByConvoIdAndMessageId(convoId, messageId)
                .ifPresent(msg -> {
                    msg.setReadAt(Instant.now());
                    messageRepository.save(msg);
                });
    }
}
