package com.dbgram.controller;

import com.dbgram.domain.community.Community;
import com.dbgram.security.AuthenticatedUser;
import com.dbgram.service.CommunityService;
import com.dbgram.util.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Community REST controller.
 */
@org.springframework.context.annotation.Profile("legacy-api")
@RestController
@RequestMapping("/api/communities")
@RequiredArgsConstructor
public class CommunityController {

    private final CommunityService communityService;

    @PostMapping
    public ResponseEntity<ApiResponse<Community>> createCommunity(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @Valid @RequestBody CreateCommunityRequest request) {
        Community community = communityService.createCommunity(
                principal.id(), request.name(), request.slug(), request.description(),
                request.type(), request.visibility(), request.expiresAt(), request.isPaid());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Community created", community));
    }

    @GetMapping("/{slug}")
    public ResponseEntity<ApiResponse<Community>> getCommunity(@PathVariable String slug) {
        return ResponseEntity.ok(ApiResponse.ok(communityService.getCommunityBySlug(slug)));
    }

    @PostMapping("/{communityId}/join")
    public ResponseEntity<ApiResponse<Void>> joinCommunity(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable UUID communityId) {
        communityService.joinCommunity(communityId, principal.id());
        return ResponseEntity.ok(ApiResponse.ok("Joined community", null));
    }

    @DeleteMapping("/{communityId}/leave")
    public ResponseEntity<ApiResponse<Void>> leaveCommunity(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable UUID communityId) {
        communityService.leaveCommunity(communityId, principal.id());
        return ResponseEntity.ok(ApiResponse.ok("Left community", null));
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<List<Community>>> getMyCommunities(
            @AuthenticationPrincipal AuthenticatedUser principal) {
        return ResponseEntity.ok(ApiResponse.ok(communityService.getUserCommunities(principal.id())));
    }

    // ── DTOs ──

    public record CreateCommunityRequest(
            @NotBlank String name,
            @NotBlank String slug,
            String description,
            Community.CommunityType type,
            Community.CommunityVisibility visibility,
            Instant expiresAt,
            Boolean isPaid) {
    }
}
