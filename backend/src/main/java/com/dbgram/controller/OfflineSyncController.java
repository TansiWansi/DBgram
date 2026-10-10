package com.dbgram.controller;

import com.dbgram.security.AuthenticatedUser;
import com.dbgram.service.OfflineSyncService;
import com.dbgram.util.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/sync")
public class OfflineSyncController {
    private final OfflineSyncService sync;

    public OfflineSyncController(OfflineSyncService sync) {
        this.sync = sync;
    }

    public record ClientEvent(@NotNull UUID eventId, @NotNull UUID postId, @NotNull OfflineSyncService.EventType type) {
    }

    public record Batch(@NotNull @Size(min = 1, max = 100) List<@NotNull @Valid ClientEvent> events) {
    }

    @PostMapping
    public ApiResponse<List<Map<String, Object>>> sync(@AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody Batch batch) {
        return ApiResponse.ok(sync.sync(user.id(), batch.events().stream()
                .map(e -> new OfflineSyncService.Event(e.eventId(), e.postId(), e.type())).toList()));
    }
}
