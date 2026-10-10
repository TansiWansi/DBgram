package com.dbgram.repository;

import com.dbgram.domain.post.PostAcl;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface PostAclRepository extends JpaRepository<PostAcl, UUID> {

    List<PostAcl> findByPostId(UUID postId);

    /**
     * High-speed ACL check: can user view this post?
     * Single query with covering indexes — targets <10ms execution.
     */
    @Query(value = """
        SELECT check_post_access(:postId, :userId)
        """, nativeQuery = true)
    boolean canUserViewPost(@Param("postId") UUID postId, @Param("userId") UUID userId);

    void deleteByPostId(UUID postId);
}
