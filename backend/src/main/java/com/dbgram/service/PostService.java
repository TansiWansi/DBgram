package com.dbgram.service;

import com.dbgram.domain.post.Post;
import com.dbgram.domain.post.PostAuthor;
import com.dbgram.domain.post.PostVersion;
import com.dbgram.kafka.KafkaEventPublisher;
import com.dbgram.repository.PostRepository;
import com.dbgram.repository.PostVersionRepository;
import com.dbgram.repository.UserRepository;
import com.dbgram.util.AppException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Post service — structured post CRUD, versioning, and engagement tracking.
 * Publishes Kafka events on create/update/delete for feed fan-out.
 */
@Slf4j
@org.springframework.context.annotation.Profile("distributed")
@Service
@RequiredArgsConstructor
public class PostService {

    private final PostRepository postRepository;
    private final PostVersionRepository postVersionRepository;
    private final UserRepository userRepository;
    private final KafkaEventPublisher eventPublisher;

    /**
     * Create a new structured post with JSONB blocks.
     */
    @Transactional
    public Post createPost(UUID authorId, List<Map<String, Object>> blocks,
                           Post.PostType postType, Post.PostVisibility visibility,
                           String[] hashtags, UUID communityId) {

        Post post = Post.builder()
                .blocks(blocks)
                .textContent(extractTextFromBlocks(blocks))
                .postType(postType)
                .visibility(visibility)
                .hashtags(hashtags)
                .communityId(communityId)
                .status(Post.PostStatus.PUBLISHED)
                .publishedAt(Instant.now())
                .build();

        PostAuthor author = PostAuthor.builder()
                .postId(post.getId())
                .userId(authorId)
                .role(PostAuthor.AuthorRole.AUTHOR)
                .contributionPct((short) 100)
                .build();
        post.setAuthors(List.of(author));

        post = postRepository.save(post);

        // Update author's post count
        userRepository.updatePostCount(authorId, 1);

        // Publish Kafka event for feed fan-out
        eventPublisher.publishPostCreated(post.getId(), authorId);

        log.info("Post created: {} by user {}", post.getId(), authorId);
        return post;
    }

    /**
     * Update a post's blocks — creates a new version snapshot before applying changes.
     */
    @Transactional
    public Post updatePost(UUID postId, UUID editorId, List<Map<String, Object>> newBlocks,
                           String editSummary) {
        Post post = getPostById(postId);

        // Verify editor has permission (must be an author)
        verifyAuthorPermission(post, editorId);

        // Snapshot the current state as a version
        PostVersion version = PostVersion.builder()
                .postId(postId)
                .versionNumber(post.getCurrentVersion())
                .blocks(post.getBlocks())
                .textContent(post.getTextContent())
                .editedBy(editorId)
                .editSummary(editSummary)
                .build();
        postVersionRepository.save(version);

        // Apply updates
        post.setBlocks(newBlocks);
        post.setTextContent(extractTextFromBlocks(newBlocks));
        post.setCurrentVersion(post.getCurrentVersion() + 1);

        post = postRepository.save(post);

        eventPublisher.publishPostUpdated(postId, editorId);

        log.info("Post updated: {} (v{}) by user {}", postId, post.getCurrentVersion(), editorId);
        return post;
    }

    /**
     * Soft-delete a post by marking it as REMOVED.
     */
    @Transactional
    public void deletePost(UUID postId, UUID userId) {
        Post post = getPostById(postId);
        verifyAuthorPermission(post, userId);

        post.setStatus(Post.PostStatus.REMOVED);
        postRepository.save(post);

        eventPublisher.publishPostDeleted(postId, userId);
        log.info("Post deleted: {} by user {}", postId, userId);
    }

    /**
     * Get a single post by ID.
     */
    @Transactional(readOnly = true)
    public Post getPostById(UUID postId) {
        return postRepository.findById(postId)
                .orElseThrow(() -> AppException.notFound("Post", postId));
    }

    /**
     * Get posts by author, paginated.
     */
    @Transactional(readOnly = true)
    public Page<Post> getPostsByAuthor(UUID authorId, Pageable pageable) {
        return postRepository.findByAuthorId(authorId, pageable);
    }

    /**
     * Get edit history for a post.
     */
    @Transactional(readOnly = true)
    public List<PostVersion> getEditHistory(UUID postId) {
        return postVersionRepository.findByPostIdOrderByVersionNumberDesc(postId);
    }

    /**
     * Revert a post to a previous version.
     */
    @Transactional
    public Post revertToVersion(UUID postId, UUID editorId, int targetVersion) {
        Post post = getPostById(postId);
        verifyAuthorPermission(post, editorId);

        PostVersion version = postVersionRepository.findByPostIdAndVersionNumber(postId, targetVersion)
                .orElseThrow(() -> AppException.notFound("PostVersion", targetVersion));

        return updatePost(postId, editorId, version.getBlocks(),
                "Reverted to version " + targetVersion);
    }

    // ── Internal Helpers ──

    private void verifyAuthorPermission(Post post, UUID userId) {
        if (post.getAuthors() == null) return;
        boolean isAuthor = post.getAuthors().stream()
                .anyMatch(a -> a.getUserId().equals(userId));
        if (!isAuthor) {
            throw AppException.forbidden("You are not an author of this post");
        }
    }

    private String extractTextFromBlocks(List<Map<String, Object>> blocks) {
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
