package com.menta.billing.infrastructure.web.controller;

import com.menta.billing.application.dto.CancelSubscriptionCommand;
import com.menta.billing.application.dto.CancellationResult;
import com.menta.billing.application.dto.CancellationTarget;
import com.menta.billing.application.port.in.CancelSubscriptionUseCase;
import com.menta.billing.infrastructure.web.dto.CancelSubscriptionRequest;
import com.menta.billing.infrastructure.web.dto.CancelSubscriptionResponse;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * HTTP adapter for administrative subscription cancellation (US-BILLING-011).
 *
 * <p>{@code SecurityConfig}'s existing generic {@code /api/v1/admin/**} rule already restricts
 * this path to {@code ROLE_ADMIN} — no new matcher is needed. {@link #isAdmin(Authentication)}
 * is a second, independent check at the application boundary (design.md A5), the same
 * defense-in-depth shape {@code PhysicalCoursePricingController} uses for ownership.</p>
 */
@RestController
@RequestMapping("/api/v1/admin/billing/subscriptions")
@SubscriptionEndpoint
public class SubscriptionAdminController {

    private final CancelSubscriptionUseCase cancelSubscriptionUseCase;

    public SubscriptionAdminController(CancelSubscriptionUseCase cancelSubscriptionUseCase) {
        this.cancelSubscriptionUseCase = cancelSubscriptionUseCase;
    }

    /** Escenarios 8/9 — {@code reason} is mandatory here and validated before any state change. */
    @DeleteMapping("/{subscriptionId}")
    public ResponseEntity<CancelSubscriptionResponse> cancel(
        @PathVariable String subscriptionId, @Valid @RequestBody CancelSubscriptionRequest request,
        Authentication authentication
    ) {
        CancellationResult result = cancelSubscriptionUseCase.cancel(new CancelSubscriptionCommand(
            new CancellationTarget.ById(UUID.fromString(subscriptionId)), actingUserId(authentication),
            isAdmin(authentication), request.reason()
        ));
        return ResponseEntity.ok(CancelSubscriptionResponse.from(result));
    }

    private static UUID actingUserId(Authentication authentication) {
        return UUID.fromString(authentication.getName());
    }

    private static boolean isAdmin(Authentication authentication) {
        return authentication.getAuthorities().stream()
            .map(GrantedAuthority::getAuthority)
            .anyMatch("ROLE_ADMIN"::equals);
    }
}
