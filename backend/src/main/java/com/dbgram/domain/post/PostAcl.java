package com.dbgram.domain.post;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

/**
 * Fine-grained Access Control entry for posts with CUSTOM visibility.
 * Each entry represents one rule (allow/deny user, list, community, or tier).
 */
@Entity
@Table(name = "post_acl")
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PostAcl {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "post_id", nullable = false)
    private UUID postId;

    @Enumerated(EnumType.STRING)
    @Column(name = "rule_type", nullable = false, length = 30)
    private AclRuleType ruleType;

    @Column(name = "target_user_id")
    private UUID targetUserId;

    @Column(name = "target_list_id")
    private UUID targetListId;

    @Column(name = "target_community_id")
    private UUID targetCommunityId;

    @Column(name = "target_tier_id")
    private UUID targetTierId;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();

    public enum AclRuleType {
        ALLOW_USER,
        DENY_USER,
        ALLOW_LIST,
        ALLOW_COMMUNITY,
        ALLOW_TIER
    }
}
