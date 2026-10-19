package com.menta.billing.infrastructure.web.controller;

import com.menta.billing.application.dto.AssignTrialCommand;
import com.menta.billing.application.dto.CancelSubscriptionCommand;
import com.menta.billing.application.dto.CancellationResult;
import com.menta.billing.application.dto.CancellationTarget;
import com.menta.billing.application.dto.TrialAssignmentResult;
import com.menta.billing.application.port.in.AssignTrialSubscriptionUseCase;
import com.menta.billing.application.port.in.CancelSubscriptionUseCase;
import com.menta.billing.infrastructure.web.dto.AssignTrialRequest;
import com.menta.billing.infrastructure.web.dto.AssignTrialResponse;
import com.menta.billing.infrastructure.web.dto.CancelSubscriptionRequest;
import com.menta.billing.infrastructure.web.dto.CancelSubscriptionResponse;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * HTTP adapter for administrative subscription cancellation (US-BILLING-011) and admin-assigned
 * trial subscriptions (US-BILLING-012).
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
    private final AssignTrialSubscriptionUseCase assignTrialSubscriptionUseCase;

    public SubscriptionAdminController(
        CancelSubscriptionUseCase cancelSubscriptionUseCase,
        AssignTrialSubscriptionUseCase assignTrialSubscriptionUseCase
    ) {
        this.cancelSubscriptionUseCase = cancelSubscriptionUseCase;
        this.assignTrialSubscriptionUseCase = assignTrialSubscriptionUseCase;
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

    /**
     * US-BILLING-012. {@code reason} and {@code days} are validated here, at the web layer
     * (design.md, Phase 4) — bean validation rejects a blank {@code reason} or a non-positive
     * {@code days} with {@code 400} before the use case runs.
     */
    @PostMapping("/trial")
    public ResponseEntity<AssignTrialResponse> assignTrial(
        @Valid @RequestBody AssignTrialRequest request, Authentication authentication
    ) {
        TrialAssignmentResult result = assignTrialSubscriptionUseCase.assign(new AssignTrialCommand(
            UUID.fromString(request.userId()), request.planId(), actingUserId(authentication),
            isAdmin(authentication), request.reason(), request.days()
        ));
        return ResponseEntity.status(HttpStatus.CREATED).body(AssignTrialResponse.from(result));
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
