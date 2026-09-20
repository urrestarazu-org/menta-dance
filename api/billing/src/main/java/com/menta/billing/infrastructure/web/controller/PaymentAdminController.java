package com.menta.billing.infrastructure.web.controller;

import com.menta.billing.application.dto.ResolvePaymentProofCommand;
import com.menta.billing.application.port.in.ResolvePaymentProofUseCase;
import com.menta.billing.domain.model.ManualVerificationDecision;
import com.menta.billing.infrastructure.web.dto.RejectPaymentRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * HTTP adapter for admin resolution of a bank-transfer payment left in {@code
 * AwaitingManualVerification} (#31, US-BILLING-003, design D1/C1).
 *
 * <p>{@code SecurityConfig}'s existing generic {@code /api/v1/admin/**} rule already restricts
 * this path to {@code ROLE_ADMIN} — no new matcher is needed. {@link #isAdmin(Authentication)}
 * is a second, independent defense-in-depth check, the same shape {@code
 * SubscriptionAdminController} uses; unlike that controller's by-id cancellation, a non-admin
 * here gets a direct {@code 403} — this route carries no ownership ambiguity to protect with an
 * anti-oracle {@code 404}.</p>
 */
@RestController
@RequestMapping("/api/v1/admin/billing/payments")
@PaymentEndpoint
public class PaymentAdminController {

    private final ResolvePaymentProofUseCase resolvePaymentProofUseCase;

    public PaymentAdminController(ResolvePaymentProofUseCase resolvePaymentProofUseCase) {
        this.resolvePaymentProofUseCase = resolvePaymentProofUseCase;
    }

    @PostMapping("/{paymentId}/approve")
    public ResponseEntity<Void> approve(@PathVariable String paymentId, Authentication authentication) {
        requireAdmin(authentication);
        resolvePaymentProofUseCase.resolve(
            new ResolvePaymentProofCommand(paymentId, ManualVerificationDecision.APPROVED, null)
        );
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{paymentId}/reject")
    public ResponseEntity<Void> reject(
        @PathVariable String paymentId, @Valid @RequestBody RejectPaymentRequest request,
        Authentication authentication
    ) {
        requireAdmin(authentication);
        resolvePaymentProofUseCase.resolve(
            new ResolvePaymentProofCommand(paymentId, ManualVerificationDecision.REJECTED, request.reason())
        );
        return ResponseEntity.ok().build();
    }

    private static void requireAdmin(Authentication authentication) {
        if (!isAdmin(authentication)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
    }

    private static boolean isAdmin(Authentication authentication) {
        return authentication.getAuthorities().stream()
            .map(GrantedAuthority::getAuthority)
            .anyMatch("ROLE_ADMIN"::equals);
    }
}
