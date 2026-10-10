package com.dbgram.repository;

import com.dbgram.domain.community.CommunityMember;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CommunityMemberRepository extends JpaRepository<CommunityMember, UUID> {

    Optional<CommunityMember> findByCommunityIdAndUserId(UUID communityId, UUID userId);

    boolean existsByCommunityIdAndUserId(UUID communityId, UUID userId);

    long countByCommunityId(UUID communityId);

    void deleteByCommunityIdAndUserId(UUID communityId, UUID userId);

    Optional<CommunityMember> findByStripeSubscriptionId(String stripeSubscriptionId);

    @Query("SELECT cm.communityId FROM CommunityMember cm WHERE cm.userId = :userId")
    List<UUID> findCommunityIdsByUserId(@Param("userId") UUID userId);
}
