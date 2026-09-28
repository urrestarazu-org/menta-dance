package com.menta.billing.infrastructure.web.controller;

import com.menta.billing.application.dto.CorrectPaymentCommand;
import com.menta.billing.application.dto.ResolvePaymentProofCommand;
import com.menta.billing.application.port.in.CorrectPaymentUseCase;
import com.menta.billing.application.port.in.ResolvePaymentProofUseCase;
import com.menta.billing.domain.model.ManualVerificationDecision;
import com.menta.billing.infrastructure.web.dto.CorrectPaymentRequest;
import com.menta.billing.infrastructure.web.dto.RejectPaymentRequest;
import jakarta.validation.Valid;
import java.util.UUID;
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
    private final CorrectPaymentUseCase correctPaymentUseCase;

    public PaymentAdminController(
        ResolvePaymentProofUseCase resolvePaymentProofUseCase, CorrectPaymentUseCase correctPaymentUseCase
    ) {
        this.resolvePaymentProofUseCase = resolvePaymentProofUseCase;
        this.correctPaymentUseCase = correctPaymentUseCase;
    }

    @PostMapping("/{paymentId}/approve")
    public ResponseEntity<Void> approve(@PathVariable String paymentId, Authentication authentication) {
        requireAdmin(authentication);
        resolvePaymentProofUseCase.resolve(new ResolvePaymentProofCommand(
            paymentId, ManualVerificationDecision.APPROVED, null, adminId(authentication)
        ));
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{paymentId}/reject")
    public ResponseEntity<Void> reject(
        @PathVariable String paymentId, @Valid @RequestBody RejectPaymentRequest request,
        Authentication authentication
    ) {
        requireAdmin(authentication);
        resolvePaymentProofUseCase.resolve(new ResolvePaymentProofCommand(
            paymentId, ManualVerificationDecision.REJECTED, request.reason(), adminId(authentication)
        ));
        return ResponseEntity.ok().build();
    }

    /**
     * D3 prefix — no new {@code SecurityConfig} matcher, covered by the existing {@code
     * /api/v1/admin/**} rule (design C6). D9: a correction out of {@code ReconciliationRequired},
     * never a data edit.
     */
    @PostMapping("/{paymentId}/corrections")
    public ResponseEntity<Void> correct(
        @PathVariable String paymentId, @Valid @RequestBody CorrectPaymentRequest request,
        Authentication authentication
    ) {
        requireAdmin(authentication);
        correctPaymentUseCase.correct(new CorrectPaymentCommand(
            paymentId, request.decision(), request.reason(), request.evidence(), adminId(authentication)
        ));
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

    /** Same source {@code PhysicalCheckInController} uses: the JWT principal is the user UUID. */
    private static UUID adminId(Authentication authentication) {
        return UUID.fromString(authentication.getName());
    }
}
