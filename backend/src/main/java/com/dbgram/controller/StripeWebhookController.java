package com.dbgram.controller;

import com.dbgram.service.BillingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Stripe Webhook controller for subscription management.
 */
@RestController
@RequestMapping("/api/webhooks")
@RequiredArgsConstructor
public class StripeWebhookController {
    private final BillingService billing;

    @PostMapping("/stripe")
    public ResponseEntity<Void> handleStripeEvent(
            @RequestHeader("Stripe-Signature") String signature,
            @RequestBody String payload) {
        billing.webhook(payload,signature);
        return ResponseEntity.ok().build();
    }
}
