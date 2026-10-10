package com.dbgram.domain.community;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

/**
 * Community membership with role hierarchy and Stripe subscription tracking.
 */
@Entity
@Table(name = "community_members",
       uniqueConstraints = @UniqueConstraint(columnNames = {"community_id", "user_id"}))
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CommunityMember {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "community_id", nullable = false)
    private UUID communityId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private MemberRole role = MemberRole.MEMBER;

    @Column(name = "tier_id")
    private UUID tierId;

    // ── Stripe Subscription ──

    @Column(name = "stripe_subscription_id")
    private String stripeSubscriptionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "subscription_status", length = 20)
    @Builder.Default
    private SubscriptionStatus subscriptionStatus = SubscriptionStatus.NONE;

    @Column(name = "subscription_expires_at")
    private Instant subscriptionExpiresAt;

    @Column(name = "joined_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant joinedAt = Instant.now();

    // ── Enums ──

    public enum MemberRole {
        OWNER, ADMIN, MODERATOR, MEMBER
    }

    public enum SubscriptionStatus {
        NONE, ACTIVE, PAST_DUE, CANCELED, TRIALING
    }

    public boolean hasActiveSubscription() {
        return subscriptionStatus == SubscriptionStatus.ACTIVE
                || subscriptionStatus == SubscriptionStatus.TRIALING;
    }
}
