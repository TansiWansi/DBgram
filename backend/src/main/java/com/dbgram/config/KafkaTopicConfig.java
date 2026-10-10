package com.dbgram.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * Kafka topic definitions for the DBgram event-driven architecture.
 * Topics are auto-created on startup in dev; use AdminClient in production.
 */
@org.springframework.context.annotation.Profile("distributed")
@Configuration
public class KafkaTopicConfig {

    // ── Post Events ──
    @Bean
    public NewTopic postCreatedTopic() {
        return TopicBuilder.name("post.created")
                .partitions(12)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic postUpdatedTopic() {
        return TopicBuilder.name("post.updated")
                .partitions(6)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic postDeletedTopic() {
        return TopicBuilder.name("post.deleted")
                .partitions(6)
                .replicas(1)
                .build();
    }

    // ── Feed Events ──
    @Bean
    public NewTopic feedFanOutTopic() {
        return TopicBuilder.name("feed.fanout")
                .partitions(12)
                .replicas(1)
                .build();
    }

    // ── Engagement Events ──
    @Bean
    public NewTopic engagementTopic() {
        return TopicBuilder.name("engagement.events")
                .partitions(12)
                .replicas(1)
                .build();
    }

    // ── Messaging Events ──
    @Bean
    public NewTopic messageSentTopic() {
        return TopicBuilder.name("message.sent")
                .partitions(12)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic messageDeletedTopic() {
        return TopicBuilder.name("message.deleted")
                .partitions(6)
                .replicas(1)
                .build();
    }

    // ── Media Events ──
    @Bean
    public NewTopic mediaUploadedTopic() {
        return TopicBuilder.name("media.uploaded")
                .partitions(6)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic mediaTranscodedTopic() {
        return TopicBuilder.name("media.transcoded")
                .partitions(6)
                .replicas(1)
                .build();
    }

    // ── Notification Events ──
    @Bean
    public NewTopic notificationTopic() {
        return TopicBuilder.name("notification.created")
                .partitions(12)
                .replicas(1)
                .build();
    }

    // ── Billing Events ──
    @Bean
    public NewTopic billingTopic() {
        return TopicBuilder.name("billing.events")
                .partitions(3)
                .replicas(1)
                .build();
    }

    // ── Copyright/Moderation Events ──
    @Bean
    public NewTopic moderationTopic() {
        return TopicBuilder.name("moderation.events")
                .partitions(3)
                .replicas(1)
                .build();
    }

    // ── Offline Sync Events ──
    @Bean
    public NewTopic offlineSyncTopic() {
        return TopicBuilder.name("offline.sync")
                .partitions(6)
                .replicas(1)
                .build();
    }
}
