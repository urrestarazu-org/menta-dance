package com.menta.billing.infrastructure.web.controller;

import com.menta.billing.application.dto.CancelSubscriptionCommand;
import com.menta.billing.application.dto.CancellationResult;
import com.menta.billing.application.dto.CancellationTarget;
import com.menta.billing.application.dto.CreateSubscriptionCheckoutCommand;
import com.menta.billing.application.dto.SubscriptionCheckoutResult;
import com.menta.billing.application.port.in.CancelSubscriptionUseCase;
import com.menta.billing.application.port.in.CreateSubscriptionCheckoutUseCase;
import com.menta.billing.infrastructure.web.dto.CancelSubscriptionResponse;
import com.menta.billing.infrastructure.web.dto.CreateSubscriptionRequest;
import com.menta.billing.infrastructure.web.dto.SubscriptionCheckoutResponse;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * HTTP adapter for subscription checkout and self-service cancellation (US-BILLING-010,
 * US-BILLING-011).
 *
 * <p>{@code SecurityConfig} gates {@code POST} and {@code DELETE /me} with {@code
 * .authenticated()}; no role is required for either. The owning user is read from the token and
 * never from the body — the same {@code actingUserId} pattern {@code
 * PhysicalCoursePricingController} uses.</p>
 */
@RestController
@RequestMapping("/api/v1/billing/subscriptions")
@SubscriptionEndpoint
public class SubscriptionController {

    private final CreateSubscriptionCheckoutUseCase createSubscriptionCheckoutUseCase;
    private final CancelSubscriptionUseCase cancelSubscriptionUseCase;

    public SubscriptionController(
        CreateSubscriptionCheckoutUseCase createSubscriptionCheckoutUseCase,
        CancelSubscriptionUseCase cancelSubscriptionUseCase
    ) {
        this.createSubscriptionCheckoutUseCase = createSubscriptionCheckoutUseCase;
        this.cancelSubscriptionUseCase = cancelSubscriptionUseCase;
    }

    @PostMapping
    public ResponseEntity<SubscriptionCheckoutResponse> create(
        @Valid @RequestBody CreateSubscriptionRequest request, Authentication authentication
    ) {
        SubscriptionCheckoutResult result = createSubscriptionCheckoutUseCase.create(
            new CreateSubscriptionCheckoutCommand(
                actingUserId(authentication), request.planId(), request.paymentMethod(), request.idempotencyKey()
            )
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(SubscriptionCheckoutResponse.from(result));
    }

    /**
     * Self-service cancellation (US-BILLING-011 escenario 1). No body — the caller cancels only
     * their own {@code ACTIVE} subscription, never on behalf of anyone else, so {@code reason}
     * is never collected here.
     */
    @DeleteMapping("/me")
    public ResponseEntity<CancelSubscriptionResponse> cancelOwn(Authentication authentication) {
        CancellationResult result = cancelSubscriptionUseCase.cancel(new CancelSubscriptionCommand(
            new CancellationTarget.Own(), actingUserId(authentication), false, null
        ));
        return ResponseEntity.ok(CancelSubscriptionResponse.from(result));
    }

    private static UUID actingUserId(Authentication authentication) {
        return UUID.fromString(authentication.getName());
    }
}
