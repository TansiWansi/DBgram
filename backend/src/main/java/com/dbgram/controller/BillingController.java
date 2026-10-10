package com.dbgram.controller;

import com.dbgram.security.AuthenticatedUser;
import com.dbgram.service.BillingService;
import com.dbgram.util.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController
@RequestMapping("/api/billing")
@RequiredArgsConstructor
public class BillingController {
    private final BillingService billing;

    @GetMapping("/status")
    public ApiResponse<?> status(@AuthenticationPrincipal AuthenticatedUser user) {
        return ApiResponse.ok(billing.status(user.id()));
    }

    @PostMapping("/checkout")
    public ApiResponse<?> checkout(@AuthenticationPrincipal AuthenticatedUser user,
            @RequestBody(required = false) Checkout request) {
        return ApiResponse.ok(billing.checkout(user.id(), request == null ? null : request.communityId()));
    }

    @PostMapping("/portal")
    public ApiResponse<?> portal(@AuthenticationPrincipal AuthenticatedUser user) {
        return ApiResponse.ok(billing.portal(user.id()));
    }

    public record Checkout(UUID communityId) {
    }
}
