package com.dbgram.controller;

import com.dbgram.domain.post.Post;
import com.dbgram.security.AuthenticatedUser;
import com.dbgram.service.FeedService;
import com.dbgram.util.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Feed REST controller — personalized algorithmic feed.
 */
@org.springframework.context.annotation.Profile("legacy-api")
@RestController
@RequestMapping("/api/feed")
@RequiredArgsConstructor
public class FeedController {

    private final FeedService feedService;

    /**
     * Get the authenticated user's personalized feed.
     *
     * @param cursor Timestamp cursor for pagination (0 = start)
     */
    @GetMapping
    public ResponseEntity<ApiResponse<List<Post>>> getFeed(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @RequestParam(defaultValue = "0") long cursor) {
        List<Post> feed = feedService.getFeed(principal.id(), cursor);
        return ResponseEntity.ok(ApiResponse.ok(feed));
    }
}
