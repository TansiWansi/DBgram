package com.dbgram.service;

import com.dbgram.domain.user.Follow;
import com.dbgram.domain.user.FollowId;
import com.dbgram.domain.user.User;
import com.dbgram.repository.FollowRepository;
import com.dbgram.repository.UserRepository;
import com.dbgram.util.AppException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * User profile operations — profile CRUD, follow/unfollow, and follower queries.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final FollowRepository followRepository;

    /**
     * Get user by ID — throws 404 if not found.
     */
    @Transactional(readOnly = true)
    public User getUserById(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> AppException.notFound("User", userId));
    }

    /**
     * Get user by username.
     */
    @Transactional(readOnly = true)
    public User getUserByUsername(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> AppException.notFound("User", username));
    }

    /**
     * Update profile fields (display name, bio, avatar, etc.).
     */
    @Transactional
    public User updateProfile(UUID userId, String displayName, String bio,
                              String avatarUrl, String coverUrl) {
        User user = getUserById(userId);

        if (displayName != null) user.setDisplayName(displayName);
        if (bio != null) user.setBio(bio);
        if (avatarUrl != null) user.setAvatarUrl(avatarUrl);
        if (coverUrl != null) user.setCoverUrl(coverUrl);

        return userRepository.save(user);
    }

    /**
     * Follow a user — creates the Follow relationship and updates denormalized counters.
     */
    @Transactional
    public void follow(UUID followerId, UUID followingId) {
        if (followerId.equals(followingId)) {
            throw AppException.badRequest("You cannot follow yourself");
        }

        if (followRepository.existsByFollowerIdAndFollowingId(followerId, followingId)) {
            throw AppException.conflict("You are already following this user");
        }

        // Verify both users exist
        getUserById(followerId);
        getUserById(followingId);

        Follow follow = Follow.builder()
                .followerId(followerId)
                .followingId(followingId)
                .build();
        followRepository.save(follow);

        userRepository.updateFollowerCount(followingId, 1);
        userRepository.updateFollowingCount(followerId, 1);

        log.info("User {} followed user {}", followerId, followingId);
    }

    /**
     * Unfollow a user.
     */
    @Transactional
    public void unfollow(UUID followerId, UUID followingId) {
        FollowId id = new FollowId(followerId, followingId);
        if (!followRepository.existsById(id)) {
            throw AppException.badRequest("You are not following this user");
        }

        followRepository.deleteById(id);
        userRepository.updateFollowerCount(followingId, -1);
        userRepository.updateFollowingCount(followerId, -1);

        log.info("User {} unfollowed user {}", followerId, followingId);
    }

    /**
     * Get follower IDs for a user.
     */
    @Transactional(readOnly = true)
    public List<UUID> getFollowerIds(UUID userId) {
        return followRepository.findFollowerIds(userId);
    }

    /**
     * Get following IDs for a user.
     */
    @Transactional(readOnly = true)
    public List<UUID> getFollowingIds(UUID userId) {
        return followRepository.findFollowingIds(userId);
    }

    /**
     * Check if a user is following another.
     */
    @Transactional(readOnly = true)
    public boolean isFollowing(UUID followerId, UUID followingId) {
        return followRepository.existsByFollowerIdAndFollowingId(followerId, followingId);
    }
}
