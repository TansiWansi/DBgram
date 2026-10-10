package com.dbgram.controller;

import com.dbgram.security.AuthenticatedUser;
import com.dbgram.service.StoryService;
import com.dbgram.util.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/stories")
@RequiredArgsConstructor
public class StoryController {
    private final StoryService stories;

    @GetMapping
    public ApiResponse<?> tray(@AuthenticationPrincipal AuthenticatedUser user) {
        return ApiResponse.ok(stories.tray(user.id()));
    }

    @GetMapping("/user/{id}")
    public ApiResponse<?> byUser(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID id) {
        return ApiResponse.ok(stories.byUser(user.id(), id));
    }

    @PostMapping("/{id}/view")
    public ApiResponse<?> view(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID id) {
        stories.viewed(user.id(), id);
        return ApiResponse.ok("Story viewed", null);
    }

    @GetMapping("/{id}/viewers")
    public ApiResponse<?> viewers(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID id) {
        return ApiResponse.ok(stories.viewers(user.id(), id));
    }
}
