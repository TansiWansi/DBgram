package com.dbgram.domain.post;

import io.hypersistence.utils.hibernate.type.json.JsonType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Type;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Immutable snapshot of a post's state for edit history.
 * Each edit creates a new version; the original is never mutated.
 */
@Entity
@Table(name = "post_versions",
       uniqueConstraints = @UniqueConstraint(columnNames = {"post_id", "version_number"}))
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PostVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "post_id", nullable = false)
    private UUID postId;

    @Column(name = "version_number", nullable = false)
    private Integer versionNumber;

    @Type(JsonType.class)
    @Column(nullable = false, columnDefinition = "jsonb")
    private List<Map<String, Object>> blocks;

    @Column(name = "text_content", columnDefinition = "TEXT")
    private String textContent;

    @Column(name = "edited_by", nullable = false)
    private UUID editedBy;

    @Column(name = "edit_summary")
    private String editSummary;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();
}
