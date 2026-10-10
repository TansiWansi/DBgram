package com.dbgram.controller;

import com.dbgram.security.AuthenticatedUser;
import com.dbgram.service.AnalyticsService;
import com.dbgram.util.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController
@RequestMapping("/api/analytics")
@RequiredArgsConstructor
public class AnalyticsController {
    private final AnalyticsService analytics;

    @GetMapping("/me")
    public ApiResponse<?> me(@AuthenticationPrincipal AuthenticatedUser user) {
        return ApiResponse.ok(analytics.getInsights(user.id()));
    }

    @PostMapping("/posts/{id}/view")
    public ApiResponse<?> view(@PathVariable UUID id, @AuthenticationPrincipal AuthenticatedUser user) {
        return ApiResponse.ok(analytics.recordView(id, user.id()));
    }
}
