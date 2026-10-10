package com.dbgram.kafka;

import com.dbgram.repository.FollowRepository;
import com.dbgram.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Kafka consumer for the feed fan-out pipeline.
 * Implements the hybrid push/pull model:
 *   - Users with < threshold followers: push to each follower's Redis sorted set
 *   - Celebrity users (>= threshold): post stored separately, merged at read-time
 *
 * Uses Virtual Threads via Spring Boot 3.3+'s virtual thread support for massive concurrency.
 */
@Slf4j
@org.springframework.context.annotation.Profile("distributed")
@Component
@RequiredArgsConstructor
public class FanOutKafkaConsumer {

    private final FollowRepository followRepository;
    private final UserRepository userRepository;
    private final StringRedisTemplate redisTemplate;

    @Value("${feed.celebrity-threshold}")
    private long celebrityThreshold;

    @Value("${feed.redis-feed-ttl-hours}")
    private long redisFeedTtlHours;

    /**
     * Consumes post.created events and performs fan-out to follower feeds.
     */
    @KafkaListener(topics = "post.created", groupId = "fanout-consumer-group")
    public void onPostCreated(Map<String, Object> event) {
        String postId = (String) event.get("postId");
        String authorId = (String) event.get("authorId");
        String timestamp = (String) event.get("timestamp");

        log.info("Fan-out triggered for post {} by author {}", postId, authorId);

        UUID authorUuid = UUID.fromString(authorId);
        long followerCount = followRepository.countFollowers(authorUuid);

        if (followerCount >= celebrityThreshold) {
            // Celebrity: store in a dedicated sorted set for pull-based merge at read time
            String celebrityKey = "feed:celebrity:" + authorId;
            redisTemplate.opsForZSet().add(celebrityKey, postId, System.currentTimeMillis());
            redisTemplate.expire(celebrityKey, Duration.ofHours(redisFeedTtlHours));
            log.info("Celebrity fan-out: post {} added to celebrity cache (followers={})",
                    postId, followerCount);
        } else {
            // Normal user: push to each follower's personal feed sorted set
            List<UUID> followerIds = followRepository.findFollowerIds(authorUuid);
            double score = System.currentTimeMillis();

            for (UUID followerId : followerIds) {
                String feedKey = "feed:user:" + followerId;
                redisTemplate.opsForZSet().add(feedKey, postId, score);
                // Trim feed to latest 500 items to prevent unbounded growth
                redisTemplate.opsForZSet().removeRange(feedKey, 0, -501);
                redisTemplate.expire(feedKey, Duration.ofHours(redisFeedTtlHours));
            }
            log.info("Push fan-out complete: post {} → {} followers", postId, followerIds.size());
        }
    }

    /**
     * Remove deleted posts from follower feeds.
     */
    @KafkaListener(topics = "post.deleted", groupId = "fanout-consumer-group")
    public void onPostDeleted(Map<String, Object> event) {
        String postId = (String) event.get("postId");
        String userId = (String) event.get("userId");

        log.info("Post deleted event: removing {} from feeds", postId);

        UUID authorUuid = UUID.fromString(userId);
        List<UUID> followerIds = followRepository.findFollowerIds(authorUuid);
        for (UUID followerId : followerIds) {
            redisTemplate.opsForZSet().remove("feed:user:" + followerId, postId);
        }
        redisTemplate.opsForZSet().remove("feed:celebrity:" + userId, postId);
    }
}
