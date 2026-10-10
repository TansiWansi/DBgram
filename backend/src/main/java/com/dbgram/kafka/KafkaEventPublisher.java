package com.dbgram.kafka;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Centralized Kafka event publisher — all domain events flow through here.
 * Event payloads are JSON maps with a consistent envelope:
 *   { eventType, entityId, actorId, timestamp, ...payload }
 */
@Slf4j
@org.springframework.context.annotation.Profile("distributed")
@Component
@RequiredArgsConstructor
public class KafkaEventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    // ── Post Events ──

    public void publishPostCreated(UUID postId, UUID authorId) {
        publish("post.created", postId.toString(), Map.of(
                "eventType", "POST_CREATED",
                "postId", postId.toString(),
                "authorId", authorId.toString(),
                "timestamp", Instant.now().toString()
        ));
    }

    public void publishPostUpdated(UUID postId, UUID editorId) {
        publish("post.updated", postId.toString(), Map.of(
                "eventType", "POST_UPDATED",
                "postId", postId.toString(),
                "editorId", editorId.toString(),
                "timestamp", Instant.now().toString()
        ));
    }

    public void publishPostDeleted(UUID postId, UUID userId) {
        publish("post.deleted", postId.toString(), Map.of(
                "eventType", "POST_DELETED",
                "postId", postId.toString(),
                "userId", userId.toString(),
                "timestamp", Instant.now().toString()
        ));
    }

    // ── Engagement Events ──

    public void publishEngagement(String action, UUID postId, UUID userId) {
        publish("engagement.events", postId.toString(), Map.of(
                "eventType", "ENGAGEMENT",
                "action", action,
                "postId", postId.toString(),
                "userId", userId.toString(),
                "timestamp", Instant.now().toString()
        ));
    }

    // ── Feed Fan-Out Events ──

    public void publishFeedFanOut(UUID postId, UUID authorId, double score) {
        publish("feed.fanout", authorId.toString(), Map.of(
                "eventType", "FEED_FANOUT",
                "postId", postId.toString(),
                "authorId", authorId.toString(),
                "score", score,
                "timestamp", Instant.now().toString()
        ));
    }

    // ── Media Events ──

    public void publishMediaUploaded(UUID mediaId, UUID uploaderId) {
        publish("media.uploaded", mediaId.toString(), Map.of(
                "eventType", "MEDIA_UPLOADED",
                "mediaId", mediaId.toString(),
                "uploaderId", uploaderId.toString(),
                "timestamp", Instant.now().toString()
        ));
    }

    public void publishMediaTranscoded(UUID mediaId) {
        publish("media.transcoded", mediaId.toString(), Map.of(
                "eventType", "MEDIA_TRANSCODED",
                "mediaId", mediaId.toString(),
                "timestamp", Instant.now().toString()
        ));
    }

    // ── Notification Events ──

    public void publishNotification(UUID recipientId, String notificationType,
                                    Map<String, Object> payload) {
        var event = new java.util.HashMap<>(payload);
        event.put("eventType", "NOTIFICATION");
        event.put("recipientId", recipientId.toString());
        event.put("notificationType", notificationType);
        event.put("timestamp", Instant.now().toString());
        publish("notification.created", recipientId.toString(), event);
    }

    // ── Messaging Events ──

    public void publishMessageSent(UUID convoId, UUID senderId, UUID messageId) {
        publish("message.sent", convoId.toString(), Map.of(
                "eventType", "MESSAGE_SENT",
                "convoId", convoId.toString(),
                "senderId", senderId.toString(),
                "messageId", messageId.toString(),
                "timestamp", Instant.now().toString()
        ));
    }

    // ── Generic Publish ──

    private void publish(String topic, String key, Object payload) {
        kafkaTemplate.send(topic, key, payload)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish event to topic {}: {}", topic, ex.getMessage());
                    } else {
                        log.debug("Published event to {} [partition={}, offset={}]",
                                topic,
                                result.getRecordMetadata().partition(),
                                result.getRecordMetadata().offset());
                    }
                });
    }
}
