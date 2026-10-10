package com.dbgram.domain.community;

import io.hypersistence.utils.hibernate.type.json.JsonType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Type;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Community entity — supports both permanent and temporal (auto-destruct) communities.
 * Temporal communities self-destruct when their event ends (enforced via Redis TTL + cron).
 */
@Entity
@Table(name = "communities")
@EntityListeners(AuditingEntityListener.class)
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Community {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, unique = true, length = 100)
    private String slug;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "avatar_url", length = 512)
    private String avatarUrl;

    @Column(name = "cover_url", length = 512)
    private String coverUrl;

    @Type(JsonType.class)
    @Column(columnDefinition = "jsonb")
    private List<Map<String, Object>> rules;

    // ── Type ──

    @Enumerated(EnumType.STRING)
    @Column(name = "community_type", nullable = false, length = 20)
    @Builder.Default
    private CommunityType communityType = CommunityType.PERMANENT;

    @Column(name = "expires_at")
    private Instant expiresAt;

    // ── Visibility ──

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private CommunityVisibility visibility = CommunityVisibility.PUBLIC;

    @Column(name = "is_paid", nullable = false)
    @Builder.Default
    private Boolean isPaid = false;

    // ── Denormalized Counters ──

    @Column(name = "member_count", nullable = false)
    @Builder.Default
    private Long memberCount = 0L;

    @Column(name = "post_count", nullable = false)
    @Builder.Default
    private Long postCount = 0L;

    // ── Metadata ──

    @Type(JsonType.class)
    @Column(columnDefinition = "jsonb")
    private List<String> tags;

    @Type(JsonType.class)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> settings;

    // ── Timestamps ──

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    // ── Enums ──

    public enum CommunityType {
        PERMANENT, TEMPORAL
    }

    public enum CommunityVisibility {
        PUBLIC, PRIVATE, HIDDEN
    }

    /**
     * Check if this temporal community has expired.
     */
    public boolean isExpired() {
        return communityType == CommunityType.TEMPORAL
                && expiresAt != null
                && Instant.now().isAfter(expiresAt);
    }
}
