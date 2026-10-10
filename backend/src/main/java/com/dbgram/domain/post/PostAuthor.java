package com.dbgram.domain.post;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

/**
 * Multi-author support with contribution percentage tracking.
 * The sum of contribution_pct across all authors of a post must equal 100.
 */
@Entity
@Table(name = "post_authors",
       uniqueConstraints = @UniqueConstraint(columnNames = {"post_id", "user_id"}))
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PostAuthor {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "post_id", nullable = false)
    private UUID postId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private AuthorRole role = AuthorRole.AUTHOR;

    @Column(name = "contribution_pct", nullable = false)
    @Builder.Default
    private Short contributionPct = 100;

    @Column(name = "added_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant addedAt = Instant.now();

    public enum AuthorRole {
        AUTHOR, CO_AUTHOR, CONTRIBUTOR, EDITOR
    }
}
