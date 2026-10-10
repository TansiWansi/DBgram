package com.dbgram.repository;

import com.dbgram.domain.post.PostVersion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PostVersionRepository extends JpaRepository<PostVersion, UUID> {

    List<PostVersion> findByPostIdOrderByVersionNumberDesc(UUID postId);

    Optional<PostVersion> findByPostIdAndVersionNumber(UUID postId, Integer versionNumber);

    int countByPostId(UUID postId);
}
