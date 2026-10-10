package com.dbgram.service;

import com.dbgram.domain.post.Post;
import com.dbgram.repository.FollowRepository;
import com.dbgram.repository.PostRepository;
import com.dbgram.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Feed service — reads the hybrid push/pull feed.
 * Merges pushed entries (from Redis) with celebrity posts (pull at read time),
 * applying temporal decay scoring: Score = e^(-λΔt) × AffinityWeight × EngagementRate
 */
@Slf4j
@org.springframework.context.annotation.Profile("distributed")
@Service
@RequiredArgsConstructor
public class FeedService {

    private final StringRedisTemplate redisTemplate;
    private final PostRepository postRepository;
    private final FollowRepository followRepository;
    private final UserRepository userRepository;

    @Value("${feed.decay-lambda}")
    private double decayLambda;

    @Value("${feed.celebrity-threshold}")
    private long celebrityThreshold;

    @Value("${feed.page-size}")
    private int pageSize;

    /**
     * Get the personalized feed for a user — merges push (Redis) + pull (celebrity) feeds.
     *
     * @param userId User to generate feed for
     * @param cursor Pagination cursor (timestamp-based, 0 = start)
     * @return Ordered list of posts scored by relevance + recency
     */
    @Transactional(readOnly = true)
    public List<Post> getFeed(UUID userId, long cursor) {
        // 1. Fetch pushed feed entries from Redis sorted set
        String feedKey = "feed:user:" + userId;
        Set<String> pushedPostIds = redisTemplate.opsForZSet()
                .reverseRangeByScore(feedKey, 0, cursor == 0 ? Double.MAX_VALUE : cursor,
                        0, pageSize);

        // 2. Fetch celebrity posts the user follows (pull-based)
        List<UUID> followingIds = followRepository.findFollowingIds(userId);
        List<UUID> celebrityIds = followingIds.stream()
                .filter(id -> {
                    var user = userRepository.findById(id).orElse(null);
                    return user != null && user.isCelebrity(celebrityThreshold);
                })
                .toList();

        List<Post> celebrityPosts = List.of();
        if (!celebrityIds.isEmpty()) {
            celebrityPosts = postRepository.findRecentByAuthors(celebrityIds,
                    PageRequest.of(0, pageSize));
        }

        // 3. Merge: collect all post IDs
        Set<UUID> allPostIds = new LinkedHashSet<>();
        if (pushedPostIds != null) {
            pushedPostIds.forEach(id -> allPostIds.add(UUID.fromString(id)));
        }
        celebrityPosts.forEach(p -> allPostIds.add(p.getId()));

        // 4. Load full post entities
        List<Post> allPosts = postRepository.findAllById(allPostIds);

        // 5. Score and sort by temporal decay
        long now = System.currentTimeMillis();
        allPosts.sort((a, b) -> {
            double scoreA = computeDecayScore(a, now);
            double scoreB = computeDecayScore(b, now);
            return Double.compare(scoreB, scoreA); // descending
        });

        // 6. Return paginated window
        return allPosts.stream()
                .limit(pageSize)
                .collect(Collectors.toList());
    }

    /**
     * Computes the decay score for a post:
     *   Score = e^(-λ * Δt) * EngagementBoost
     *
     * Where:
     *   - Δt = seconds since post creation
     *   - EngagementBoost = log2(likes + comments + saves + 1)
     */
    private double computeDecayScore(Post post, long nowMs) {
        long createdAtMs = post.getCreatedAt() != null
                ? post.getCreatedAt().toEpochMilli()
                : nowMs;
        double deltaSeconds = (nowMs - createdAtMs) / 1000.0;
        double decay = Math.exp(-decayLambda * deltaSeconds);

        long engagement = post.getLikeCount() + post.getCommentCount() + post.getSaveCount();
        double engagementBoost = Math.log(engagement + 1) / Math.log(2); // log2

        return decay * (1 + engagementBoost);
    }
}
