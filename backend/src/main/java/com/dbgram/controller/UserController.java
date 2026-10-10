package com.dbgram.controller;

import com.dbgram.domain.user.User;
import com.dbgram.security.AuthenticatedUser;
import com.dbgram.service.UserService;
import com.dbgram.util.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * User profile and social graph REST controller.
 */
@org.springframework.context.annotation.Profile("legacy-api")
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserProfileResponse>> getMyProfile(
            @AuthenticationPrincipal AuthenticatedUser principal) {
        User user = userService.getUserById(principal.id());
        return ResponseEntity.ok(ApiResponse.ok(toProfileResponse(user)));
    }

    @GetMapping("/{username}")
    public ResponseEntity<ApiResponse<UserProfileResponse>> getUserProfile(
            @PathVariable String username) {
        User user = userService.getUserByUsername(username);
        return ResponseEntity.ok(ApiResponse.ok(toProfileResponse(user)));
    }

    @PutMapping("/me")
    public ResponseEntity<ApiResponse<UserProfileResponse>> updateProfile(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @RequestBody UpdateProfileRequest request) {
        User user = userService.updateProfile(
                principal.id(), request.displayName(), request.bio(),
                request.avatarUrl(), request.coverUrl());
        return ResponseEntity.ok(ApiResponse.ok("Profile updated", toProfileResponse(user)));
    }

    @PostMapping("/{userId}/follow")
    public ResponseEntity<ApiResponse<Void>> follow(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable UUID userId) {
        userService.follow(principal.id(), userId);
        return ResponseEntity.ok(ApiResponse.ok("Followed successfully", null));
    }

    @DeleteMapping("/{userId}/follow")
    public ResponseEntity<ApiResponse<Void>> unfollow(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable UUID userId) {
        userService.unfollow(principal.id(), userId);
        return ResponseEntity.ok(ApiResponse.ok("Unfollowed successfully", null));
    }

    @GetMapping("/{userId}/followers")
    public ResponseEntity<ApiResponse<List<UUID>>> getFollowers(@PathVariable UUID userId) {
        return ResponseEntity.ok(ApiResponse.ok(userService.getFollowerIds(userId)));
    }

    @GetMapping("/{userId}/following")
    public ResponseEntity<ApiResponse<List<UUID>>> getFollowing(@PathVariable UUID userId) {
        return ResponseEntity.ok(ApiResponse.ok(userService.getFollowingIds(userId)));
    }

    @GetMapping("/{userId}/is-following")
    public ResponseEntity<ApiResponse<Map<String, Boolean>>> isFollowing(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable UUID userId) {
        boolean following = userService.isFollowing(principal.id(), userId);
        return ResponseEntity.ok(ApiResponse.ok(Map.of("isFollowing", following)));
    }

    // ── DTOs ──

    public record UpdateProfileRequest(
            String displayName,
            String bio,
            String avatarUrl,
            String coverUrl
    ) {}

    public record UserProfileResponse(
            UUID id,
            String username,
            String displayName,
            String bio,
            String avatarUrl,
            String coverUrl,
            Boolean isVerified,
            Boolean isBusiness,
            Long followerCount,
            Long followingCount,
            Long postCount
    ) {}

    private UserProfileResponse toProfileResponse(User user) {
        return new UserProfileResponse(
                user.getId(), user.getUsername(), user.getDisplayName(),
                user.getBio(), user.getAvatarUrl(), user.getCoverUrl(),
                user.getIsVerified(), user.getIsBusiness(),
                user.getFollowerCount(), user.getFollowingCount(), user.getPostCount()
        );
    }
}
