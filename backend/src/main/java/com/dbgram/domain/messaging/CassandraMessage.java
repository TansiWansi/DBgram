package com.dbgram.domain.messaging;

import lombok.*;
import org.springframework.data.cassandra.core.cql.Ordering;
import org.springframework.data.cassandra.core.cql.PrimaryKeyType;
import org.springframework.data.cassandra.core.mapping.*;

import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.UUID;

/**
 * Cassandra entity for E2EE messages.
 * Partitioned by conversation for efficient conversation-level queries.
 * The server NEVER decrypts the payload — it only stores and routes encrypted blobs.
 */
@Table("messages_by_conversation")
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CassandraMessage {

    @PrimaryKeyColumn(name = "convo_id", ordinal = 0, type = PrimaryKeyType.PARTITIONED)
    private UUID convoId;

    @PrimaryKeyColumn(name = "message_id", ordinal = 1, type = PrimaryKeyType.CLUSTERED,
                       ordering = Ordering.DESCENDING)
    private UUID messageId;

    @Column("sender_id")
    private UUID senderId;

    /** Encrypted payload — client-side Signal Protocol encryption. Never plaintext. */
    @Column("encrypted_body")
    private ByteBuffer encryptedBody;

    @Column("message_type")
    private String messageType;

    @Column("reply_to_id")
    private UUID replyToId;

    @Column("is_ephemeral")
    private Boolean isEphemeral;

    @Column("ttl_seconds")
    private Integer ttlSeconds;

    @Column("delivered_at")
    private Instant deliveredAt;

    @Column("read_at")
    private Instant readAt;

    @Column("is_deleted")
    private Boolean isDeleted;

    @Column("deleted_at")
    private Instant deletedAt;

    @Column("created_at")
    private Instant createdAt;
}
