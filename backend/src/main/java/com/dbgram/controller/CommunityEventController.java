package com.dbgram.controller;

import com.dbgram.security.AuthenticatedUser;
import com.dbgram.service.CommunityEventService;
import com.dbgram.util.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/api")
public class CommunityEventController {
    private final CommunityEventService events;

    public CommunityEventController(CommunityEventService events) {
        this.events = events;
    }

    public record Draft(
            @NotBlank @Size(max = 255) String title,
            @Size(max = 5000) String description,
            @NotNull Instant startsAt,
            @NotNull Instant endsAt,
            @Size(max = 512) String meetLink,
            @Min(1) @Max(100000) Integer maxAttendees) {
    }

    @GetMapping("/communities/{community}/events")
    public ApiResponse<?> list(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID community) {
        return ApiResponse.ok(events.list(user.id(), community));
    }

    @PostMapping("/communities/{community}/events")
    @ResponseStatus(org.springframework.http.HttpStatus.CREATED)
    public ApiResponse<?> create(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID community,
            @Valid @RequestBody Draft d) {
        return ApiResponse.ok(events.create(user.id(), community, new CommunityEventService.Draft(d.title(),
                d.description(), d.startsAt(), d.endsAt(), d.meetLink(), d.maxAttendees())));
    }

    @PostMapping("/events/{id}/rsvp")
    public ApiResponse<?> attend(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID id) {
        return ApiResponse.ok(events.rsvp(user.id(), id, true));
    }

    @DeleteMapping("/events/{id}/rsvp")
    public ApiResponse<?> leave(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID id) {
        return ApiResponse.ok(events.rsvp(user.id(), id, false));
    }

    @DeleteMapping("/events/{id}")
    public ApiResponse<?> cancel(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID id) {
        return ApiResponse.ok(events.cancel(user.id(), id));
    }
}
