package com.dbgram.domain.feed;

import lombok.*;
import org.springframework.data.cassandra.core.cql.Ordering;
import org.springframework.data.cassandra.core.cql.PrimaryKeyType;
import org.springframework.data.cassandra.core.mapping.*;

import java.time.Instant;
import java.util.UUID;

/**
 * Pre-computed feed entry in Cassandra.
 * Partitioned by user_id, clustered by decay score (DESC) for fast retrieval.
 * 48-hour TTL ensures stale entries are automatically garbage-collected.
 */
@Table("feed_by_user")
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CassandraFeedEntry {

    @PrimaryKeyColumn(name = "user_id", ordinal = 0, type = PrimaryKeyType.PARTITIONED)
    private UUID userId;

    @PrimaryKeyColumn(name = "score", ordinal = 1, type = PrimaryKeyType.CLUSTERED,
                       ordering = Ordering.DESCENDING)
    private Double score;

    @PrimaryKeyColumn(name = "post_id", ordinal = 2, type = PrimaryKeyType.CLUSTERED,
                       ordering = Ordering.ASCENDING)
    private UUID postId;

    @Column("author_id")
    private UUID authorId;

    @Column("post_type")
    private String postType;

    // ── Denormalized Preview Data (avoids cross-DB joins) ──

    @Column("preview_text")
    private String previewText;

    @Column("preview_media")
    private String previewMedia;

    @Column("author_username")
    private String authorUsername;

    @Column("author_avatar")
    private String authorAvatar;

    @Column("like_count")
    private Long likeCount;

    @Column("comment_count")
    private Long commentCount;

    @Column("created_at")
    private Instant createdAt;

    @Column("inserted_at")
    private Instant insertedAt;
}
