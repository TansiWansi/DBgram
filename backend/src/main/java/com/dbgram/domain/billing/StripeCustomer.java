package com.dbgram.domain.billing;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

/**
 * Stripe customer mapping — links a DBgram user to their Stripe customer ID.
 */
@Entity
@Table(name = "stripe_customers")
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StripeCustomer {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false, unique = true)
    private UUID userId;

    @Column(name = "stripe_customer_id", nullable = false, unique = true)
    private String stripeCustomerId;

    @Column(name = "default_payment_method")
    private String defaultPaymentMethod;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();
}
