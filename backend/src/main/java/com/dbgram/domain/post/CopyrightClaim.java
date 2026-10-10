package com.dbgram.domain.post;

import io.hypersistence.utils.hibernate.type.json.JsonType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Type;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Copyright claim with full workflow:
 * PENDING → REVIEWING → UPHELD/REJECTED → COUNTER_CLAIMED → RESOLVED
 */
@Entity
@Table(name = "copyright_claims")
@EntityListeners(AuditingEntityListener.class)
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CopyrightClaim {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "claimant_id", nullable = false)
    private UUID claimantId;

    @Column(name = "claimant_email", nullable = false)
    private String claimantEmail;

    @Column(name = "target_post_id")
    private UUID targetPostId;

    @Column(name = "target_media_id")
    private UUID targetMediaId;

    @Column(name = "target_user_id")
    private UUID targetUserId;

    @Enumerated(EnumType.STRING)
    @Column(name = "claim_type", nullable = false, length = 30)
    private ClaimType claimType;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String description;

    @Type(JsonType.class)
    @Column(name = "evidence_urls", columnDefinition = "jsonb")
    private List<String> evidenceUrls;

    @Column(name = "original_work_url", length = 512)
    private String originalWorkUrl;

    @Column(name = "claimant_media_hash", length = 64)
    private String claimantMediaHash;

    @Column(name = "target_media_hash", length = 64)
    private String targetMediaHash;

    @Column(name = "hash_match")
    private Boolean hashMatch;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    @Builder.Default
    private ClaimStatus status = ClaimStatus.PENDING;

    @Column(name = "resolution_notes", columnDefinition = "TEXT")
    private String resolutionNotes;

    @Column(name = "resolved_by")
    private UUID resolvedBy;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public enum ClaimType {
        COPYRIGHT, TRADEMARK, IDENTITY, OTHER
    }

    public enum ClaimStatus {
        PENDING, REVIEWING, UPHELD, REJECTED,
        COUNTER_CLAIMED, COUNTER_REVIEWING, RESOLVED, WITHDRAWN
    }
}
