package com.dbgram.repository;

import com.dbgram.domain.user.Follow;
import com.dbgram.domain.user.FollowId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface FollowRepository extends JpaRepository<Follow, FollowId> {

    boolean existsByFollowerIdAndFollowingId(UUID followerId, UUID followingId);

    @Query("SELECT f.followerId FROM Follow f WHERE f.followingId = :userId")
    List<UUID> findFollowerIds(@Param("userId") UUID userId);

    @Query("SELECT f.followingId FROM Follow f WHERE f.followerId = :userId")
    List<UUID> findFollowingIds(@Param("userId") UUID userId);

    @Query("SELECT f.followingId FROM Follow f WHERE f.followerId = :userId AND f.isCloseFriend = true")
    List<UUID> findCloseFriendIds(@Param("userId") UUID userId);

    @Query("SELECT COUNT(f) FROM Follow f WHERE f.followingId = :userId")
    long countFollowers(@Param("userId") UUID userId);
}
