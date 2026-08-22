package com.menta.billing.infrastructure.web.controller;

import com.menta.billing.application.dto.CreateSubscriptionCheckoutCommand;
import com.menta.billing.application.dto.SubscriptionCheckoutResult;
import com.menta.billing.application.port.in.CreateSubscriptionCheckoutUseCase;
import com.menta.billing.infrastructure.web.dto.CreateSubscriptionRequest;
import com.menta.billing.infrastructure.web.dto.SubscriptionCheckoutResponse;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * HTTP adapter for subscription checkout (US-BILLING-010).
 *
 * <p>{@code SecurityConfig} gates this path with {@code .authenticated()};
 * no role is required. The owning user is read from the token and never from
 * the body — the same {@code actingUserId} pattern {@code
 * PhysicalCoursePricingController} uses.</p>
 */
@RestController
@RequestMapping("/api/v1/billing/subscriptions")
@SubscriptionEndpoint
public class SubscriptionController {

    private final CreateSubscriptionCheckoutUseCase createSubscriptionCheckoutUseCase;

    public SubscriptionController(CreateSubscriptionCheckoutUseCase createSubscriptionCheckoutUseCase) {
        this.createSubscriptionCheckoutUseCase = createSubscriptionCheckoutUseCase;
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

    private static UUID actingUserId(Authentication authentication) {
        return UUID.fromString(authentication.getName());
    }
}
