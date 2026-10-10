package com.dbgram.repository;

import com.dbgram.domain.user.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByUsername(String username);

    Optional<User> findByEmail(String email);

    Optional<User> findByPhone(String phone);

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);

    @Modifying
    @Query("UPDATE User u SET u.followerCount = u.followerCount + :delta WHERE u.id = :userId")
    void updateFollowerCount(@Param("userId") UUID userId, @Param("delta") long delta);

    @Modifying
    @Query("UPDATE User u SET u.followingCount = u.followingCount + :delta WHERE u.id = :userId")
    void updateFollowingCount(@Param("userId") UUID userId, @Param("delta") long delta);

    @Modifying
    @Query("UPDATE User u SET u.postCount = u.postCount + :delta WHERE u.id = :userId")
    void updatePostCount(@Param("userId") UUID userId, @Param("delta") long delta);
}
