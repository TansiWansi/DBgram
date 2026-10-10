package com.dbgram.service;

import com.dbgram.domain.community.Community;
import com.dbgram.domain.community.CommunityMember;
import com.dbgram.repository.CommunityMemberRepository;
import com.dbgram.repository.CommunityRepository;
import com.dbgram.util.AppException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Community service — CRUD for permanent and temporal communities.
 * Temporal communities use Redis EXPIREAT for auto-destruction.
 */
@Slf4j
@org.springframework.context.annotation.Profile("distributed")
@Service
@RequiredArgsConstructor
public class CommunityService {

    private final CommunityRepository communityRepository;
    private final CommunityMemberRepository memberRepository;
    private final StringRedisTemplate redisTemplate;

    /**
     * Create a new community.
     */
    @Transactional
    public Community createCommunity(UUID ownerId, String name, String slug,
                                     String description, Community.CommunityType type,
                                     Community.CommunityVisibility visibility,
                                     Instant expiresAt, Boolean isPaid) {
        Community community = Community.builder()
                .ownerId(ownerId)
                .name(name)
                .slug(slug)
                .description(description)
                .communityType(type)
                .visibility(visibility)
                .expiresAt(expiresAt)
                .isPaid(isPaid != null ? isPaid : false)
                .build();

        community = communityRepository.save(community);

        // Add owner as first member with OWNER role
        CommunityMember ownerMember = CommunityMember.builder()
                .communityId(community.getId())
                .userId(ownerId)
                .role(CommunityMember.MemberRole.OWNER)
                .build();
        memberRepository.save(ownerMember);

        // If temporal, set Redis TTL for auto-cleanup
        if (type == Community.CommunityType.TEMPORAL && expiresAt != null) {
            Duration ttl = Duration.between(Instant.now(), expiresAt);
            if (!ttl.isNegative()) {
                redisTemplate.opsForValue().set(
                        "community:temporal:" + community.getId(),
                        community.getId().toString(),
                        ttl
                );
            }
        }

        log.info("Community created: {} ({}) by user {}", name, type, ownerId);
        return community;
    }

    /**
     * Get community by ID.
     */
    @Transactional(readOnly = true)
    public Community getCommunityById(UUID communityId) {
        return communityRepository.findById(communityId)
                .orElseThrow(() -> AppException.notFound("Community", communityId));
    }

    /**
     * Get community by slug.
     */
    @Transactional(readOnly = true)
    public Community getCommunityBySlug(String slug) {
        return communityRepository.findBySlug(slug)
                .orElseThrow(() -> AppException.notFound("Community", slug));
    }

    /**
     * Join a community.
     */
    @Transactional
    public void joinCommunity(UUID communityId, UUID userId) {
        Community community = getCommunityById(communityId);

        if (community.isExpired()) {
            throw AppException.badRequest("This community has expired");
        }

        if (memberRepository.existsByCommunityIdAndUserId(communityId, userId)) {
            throw AppException.conflict("Already a member of this community");
        }

        CommunityMember member = CommunityMember.builder()
                .communityId(communityId)
                .userId(userId)
                .role(CommunityMember.MemberRole.MEMBER)
                .build();
        memberRepository.save(member);

        communityRepository.updateMemberCount(communityId, 1);
        log.info("User {} joined community {}", userId, communityId);
    }

    /**
     * Leave a community.
     */
    @Transactional
    public void leaveCommunity(UUID communityId, UUID userId) {
        Community community = getCommunityById(communityId);

        if (community.getOwnerId().equals(userId)) {
            throw AppException.badRequest("Owner cannot leave the community");
        }

        memberRepository.deleteByCommunityIdAndUserId(communityId, userId);
        communityRepository.updateMemberCount(communityId, -1);
        log.info("User {} left community {}", userId, communityId);
    }

    /**
     * Get all communities a user belongs to.
     */
    @Transactional(readOnly = true)
    public List<Community> getUserCommunities(UUID userId) {
        List<UUID> communityIds = memberRepository.findCommunityIdsByUserId(userId);
        return communityRepository.findAllById(communityIds);
    }
}
