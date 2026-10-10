package com.dbgram.repository;

import com.dbgram.domain.post.Post;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface PostRepository extends JpaRepository<Post, UUID> {

    @Query("SELECT p FROM Post p WHERE p.communityId = :communityId AND p.status = 'PUBLISHED' ORDER BY p.publishedAt DESC")
    Page<Post> findByCommunityId(@Param("communityId") UUID communityId, Pageable pageable);

    @Query("SELECT p FROM Post p JOIN PostAuthor pa ON pa.postId = p.id WHERE pa.userId = :userId AND p.status = 'PUBLISHED' ORDER BY p.publishedAt DESC")
    Page<Post> findByAuthorId(@Param("userId") UUID userId, Pageable pageable);

    @Modifying
    @Query("UPDATE Post p SET p.likeCount = p.likeCount + :delta WHERE p.id = :postId")
    void updateLikeCount(@Param("postId") UUID postId, @Param("delta") long delta);

    @Modifying
    @Query("UPDATE Post p SET p.commentCount = p.commentCount + :delta WHERE p.id = :postId")
    void updateCommentCount(@Param("postId") UUID postId, @Param("delta") long delta);

    @Modifying
    @Query("UPDATE Post p SET p.viewCount = p.viewCount + :delta WHERE p.id = :postId")
    void updateViewCount(@Param("postId") UUID postId, @Param("delta") long delta);

    @Modifying
    @Query("UPDATE Post p SET p.saveCount = p.saveCount + :delta WHERE p.id = :postId")
    void updateSaveCount(@Param("postId") UUID postId, @Param("delta") long delta);

    /**
     * Fetch posts from a set of celebrity user IDs for pull-based feed merge.
     */
    @Query("""
        SELECT p FROM Post p
        JOIN PostAuthor pa ON pa.postId = p.id
        WHERE pa.userId IN :authorIds
          AND p.status = 'PUBLISHED'
          AND p.visibility = 'PUBLIC'
        ORDER BY p.publishedAt DESC
        """)
    List<Post> findRecentByAuthors(@Param("authorIds") List<UUID> authorIds, Pageable pageable);
}
