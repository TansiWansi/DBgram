package com.dbgram.domain.user;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

/**
 * Follow relationship between users.
 * Supports "Close Friends" for fine-grained ACL visibility.
 */
@Entity
@Table(name = "follows")
@IdClass(FollowId.class)
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Follow {

    @Id
    @Column(name = "follower_id", nullable = false)
    private UUID followerId;

    @Id
    @Column(name = "following_id", nullable = false)
    private UUID followingId;

    @Column(name = "is_close_friend", nullable = false)
    @Builder.Default
    private Boolean isCloseFriend = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();
}
