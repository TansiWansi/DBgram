package com.dbgram.domain.user;

import io.hypersistence.utils.hibernate.type.json.JsonType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Type;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Core user entity — PostgreSQL ACID storage.
 * Profile metadata stored as JSONB for flexible schema evolution.
 */
@Entity
@Table(name = "users")
@EntityListeners(AuditingEntityListener.class)
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true, length = 30)
    private String username;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(unique = true, length = 20)
    private String phone;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    // ── Profile ──

    @Column(name = "display_name", nullable = false, length = 100)
    private String displayName;

    @Column(columnDefinition = "TEXT")
    private String bio;

    @Column(name = "avatar_url", length = 512)
    private String avatarUrl;

    @Column(name = "cover_url", length = 512)
    private String coverUrl;

    @Type(JsonType.class)
    @Column(name = "profile_meta", columnDefinition = "jsonb")
    private Map<String, Object> profileMeta;

    // ── Verification ──

    @Column(name = "is_verified", nullable = false)
    @Builder.Default
    private Boolean isVerified = false;

    @Column(name = "is_business", nullable = false)
    @Builder.Default
    private Boolean isBusiness = false;

    @Column(name = "is_private", nullable = false)
    @Builder.Default
    private Boolean isPrivate = false;

    @Enumerated(EnumType.STRING)
    @Column(name = "verification_type", length = 20)
    @Builder.Default
    private VerificationType verificationType = VerificationType.NONE;

    // ── 2FA ──

    @Column(name = "two_fa_enabled", nullable = false)
    @Builder.Default
    private Boolean twoFaEnabled = false;

    @Column(name = "two_fa_secret")
    private String twoFaSecret;

    // ── Denormalized Counters ──

    @Column(name = "follower_count", nullable = false)
    @Builder.Default
    private Long followerCount = 0L;

    @Column(name = "following_count", nullable = false)
    @Builder.Default
    private Long followingCount = 0L;

    @Column(name = "post_count", nullable = false)
    @Builder.Default
    private Long postCount = 0L;

    // ── Status ──

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private UserStatus status = UserStatus.ACTIVE;

    // ── Timestamps ──

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    // ── Enums ──

    public enum VerificationType {
        NONE, BLUE, GOLD, GOVERNMENT
    }

    public enum UserStatus {
        ACTIVE, SUSPENDED, DEACTIVATED, BANNED
    }

    /**
     * Returns true if this user qualifies as a "celebrity"
     * (used by the feed engine to decide push vs pull fan-out).
     */
    public boolean isCelebrity(long threshold) {
        return followerCount >= threshold;
    }
}
