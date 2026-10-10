package com.dbgram.controller;

import com.dbgram.security.AuthenticatedUser;
import com.dbgram.service.CopyrightClaimService;
import com.dbgram.util.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/api/copyright")
@RequiredArgsConstructor
public class CopyrightController {
    private final CopyrightClaimService claims;

    @GetMapping("/capabilities")
    public ApiResponse<?> capabilities(@AuthenticationPrincipal AuthenticatedUser user) {
        return ApiResponse.ok(Map.of("moderator", claims.isModerator(user.id())));
    }

    @GetMapping("/claims")
    public ApiResponse<?> list(@AuthenticationPrincipal AuthenticatedUser user) {
        return ApiResponse.ok(claims.list(user.id()));
    }

    @GetMapping("/claims/{id}")
    public ApiResponse<?> get(@PathVariable UUID id, @AuthenticationPrincipal AuthenticatedUser user) {
        return ApiResponse.ok(claims.get(id, user.id()));
    }

    @PostMapping("/claims")
    public ApiResponse<?> submit(@AuthenticationPrincipal AuthenticatedUser user, @RequestBody Submission request) {
        return ApiResponse.ok(claims.submit(user.id(), request.targetPostId(), request.targetMediaId(),
                request.claimType() == null ? "COPYRIGHT" : request.claimType(), request.description(),
                request.evidenceUrls(), request.originalWorkUrl(), request.claimantMediaHash()));
    }

    @PatchMapping("/claims/{id}")
    public ApiResponse<?> transition(@PathVariable UUID id, @AuthenticationPrincipal AuthenticatedUser user,
            @RequestBody Transition request) {
        return ApiResponse
                .ok(claims.transition(id, user.id(), request.status(), request.notes(), request.restoreContent()));
    }

    public record Submission(UUID targetPostId, UUID targetMediaId, String claimType, String description,
            List<String> evidenceUrls, String originalWorkUrl, String claimantMediaHash) {
    }

    public record Transition(String status, String notes, Boolean restoreContent) {
    }
}
