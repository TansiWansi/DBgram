package com.dbgram.repository;

import com.dbgram.domain.community.Community;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CommunityRepository extends JpaRepository<Community, UUID> {

    Optional<Community> findBySlug(String slug);

    List<Community> findByOwnerId(UUID ownerId);

    @Modifying
    @Query("UPDATE Community c SET c.memberCount = c.memberCount + :delta WHERE c.id = :communityId")
    void updateMemberCount(@Param("communityId") UUID communityId, @Param("delta") long delta);

    @Query("SELECT c FROM Community c WHERE c.communityType = 'TEMPORAL' AND c.expiresAt <= :now")
    List<Community> findExpiredTemporalCommunities(@Param("now") Instant now);

    @Query("""
        SELECT c FROM Community c
        JOIN CommunityMember cm ON cm.communityId = c.id
        WHERE cm.userId = :userId
        ORDER BY c.createdAt DESC
        """)
    List<Community> findCommunitiesByMember(@Param("userId") UUID userId);
}
