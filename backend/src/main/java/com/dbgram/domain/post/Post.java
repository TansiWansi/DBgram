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
import java.util.Map;
import java.util.UUID;

/**
 * Post entity — Notion-style structured posts using JSONB blocks.
 *
 * <p>The {@code blocks} field is a JSON array of block objects:
 * <pre>[
 *   {"type": "text", "data": {"content": "Hello world", "format": "markdown"}},
 *   {"type": "carousel", "data": {"media_urls": ["s3://...", "s3://..."]}},
 *   {"type": "poll", "data": {"question": "...", "options": [...]}},
 *   {"type": "code", "data": {"language": "java", "content": "..."}}
 * ]</pre>
 */
@Entity
@Table(name = "posts")
@EntityListeners(AuditingEntityListener.class)
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Post {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "community_id")
    private UUID communityId;

    // ── Structured Content (JSONB) ──

    @Type(JsonType.class)
    @Column(nullable = false, columnDefinition = "jsonb")
    private List<Map<String, Object>> blocks;

    @Column(name = "text_content", columnDefinition = "TEXT")
    private String textContent;

    // ── Post Type ──

    @Enumerated(EnumType.STRING)
    @Column(name = "post_type", nullable = false, length = 20)
    @Builder.Default
    private PostType postType = PostType.STANDARD;

    // ── Visibility ──

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    @Builder.Default
    private PostVisibility visibility = PostVisibility.PUBLIC;

    // ── Media Provenance ──

    @Column(name = "media_hash", length = 64)
    private String mediaHash;

    @Column(name = "merkle_root", length = 64)
    private String merkleRoot;

    @Column(name = "blockchain_tx", length = 128)
    private String blockchainTx;

    @Column(name = "provenance_anchored", nullable = false)
    @Builder.Default
    private Boolean provenanceAnchored = false;

    // ── Engagement Counters (denormalized) ──

    @Column(name = "like_count", nullable = false)
    @Builder.Default
    private Long likeCount = 0L;

    @Column(name = "comment_count", nullable = false)
    @Builder.Default
    private Long commentCount = 0L;

    @Column(name = "share_count", nullable = false)
    @Builder.Default
    private Long shareCount = 0L;

    @Column(name = "save_count", nullable = false)
    @Builder.Default
    private Long saveCount = 0L;

    @Column(name = "view_count", nullable = false)
    @Builder.Default
    private Long viewCount = 0L;

    // ── Settings ──

    @Column(name = "comments_enabled", nullable = false)
    @Builder.Default
    private Boolean commentsEnabled = true;

    @Column(name = "is_pinned", nullable = false)
    @Builder.Default
    private Boolean isPinned = false;

    @Column(name = "hashtags", columnDefinition = "TEXT[]")
    private String[] hashtags;

    @Column(name = "current_version", nullable = false)
    @Builder.Default
    private Integer currentVersion = 1;

    // ── Status ──

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private PostStatus status = PostStatus.PUBLISHED;

    // ── Timestamps ──

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "published_at")
    private Instant publishedAt;

    // ── Relationships ──

    @OneToMany(fetch = FetchType.LAZY)
    @JoinColumn(name = "post_id", insertable = false, updatable = false)
    private List<PostAuthor> authors;

    // ── Enums ──

    public enum PostType {
        STANDARD, REEL, STORY, ARTICLE
    }

    public enum PostVisibility {
        PUBLIC, FOLLOWERS_ONLY, CLOSE_FRIENDS, COMMUNITY_ONLY, CUSTOM, PRIVATE
    }

    public enum PostStatus {
        DRAFT, PUBLISHED, ARCHIVED, REMOVED, UNDER_REVIEW
    }

    /**
     * Extracts plaintext from all text blocks for full-text search indexing.
     */
    public String extractTextContent() {
        if (blocks == null || blocks.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (Map<String, Object> block : blocks) {
            if ("text".equals(block.get("type"))) {
                @SuppressWarnings("unchecked")
                Map<String, Object> data = (Map<String, Object>) block.get("data");
                if (data != null && data.get("content") != null) {
                    sb.append(data.get("content")).append(" ");
                }
            }
        }
        return sb.toString().trim();
    }
}
