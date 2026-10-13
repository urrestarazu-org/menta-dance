package com.menta.billing.infrastructure.web.controller;

import com.menta.billing.application.dto.ExceptionPurchasePage;
import com.menta.billing.application.dto.PurchaseResolutionResult;
import com.menta.billing.application.dto.ResolveExceptionPurchaseCommand;
import com.menta.billing.application.port.in.ResolveExceptionPurchaseUseCase;
import com.menta.billing.application.port.out.PurchaseRepository;
import com.menta.billing.infrastructure.web.dto.ExceptionPurchasePageResponse;
import com.menta.billing.infrastructure.web.dto.ResolveExceptionPurchaseRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * HTTP adapter for the admin EXCEPTION-purchases inbox (#237, design C6/C7/C8): a read-only
 * inventory of physical purchases stuck in {@code FulfillmentStatus.EXCEPTION}, plus the single
 * admin action that closes one with an audited record (#539). Nothing else can move a purchase out
 * of {@code EXCEPTION}.
 *
 * <p>{@code SecurityConfig}'s existing generic {@code /api/v1/admin/**} rule already restricts
 * this path to {@code ROLE_ADMIN} — no new matcher is needed (design C8). {@link
 * #requireAdmin(Authentication)} is a second, independent defense-in-depth check, the exact shape
 * {@code PaymentAdminController} uses.</p>
 */
@RestController
@RequestMapping("/api/v1/admin/billing/purchases")
@PaymentEndpoint
@RequiredArgsConstructor
public class PurchaseAdminController {

    /** The spec's own cap; rejected, never clamped. */
    private static final int MAX_PAGE_SIZE = 50;
    private static final int DEFAULT_PAGE_SIZE = 20;

    private final PurchaseRepository purchaseRepository;
    private final ResolveExceptionPurchaseUseCase resolveExceptionPurchaseUseCase;

    /**
     * {@code status} is part of the URL contract (D10) but the query itself is fixed to {@code
     * EXCEPTION} via {@link PurchaseRepository#findInException(int, int)} — mirrors {@code
     * PaymentAdminController#list}'s own {@code status}/{@code substatus} contract. {@code size >
     * 50} is rejected with {@code 400} ({@link PaymentExceptionHandler#malformedRequest}), never
     * clamped.
     */
    @GetMapping
    public ResponseEntity<ExceptionPurchasePageResponse> list(
        @RequestParam String status, @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "" + DEFAULT_PAGE_SIZE) int size, Authentication authentication
    ) {
        requireAdmin(authentication);
        if (size > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("size must not exceed " + MAX_PAGE_SIZE);
        }
        ExceptionPurchasePage result = purchaseRepository.findInException(page, size);
        return ResponseEntity.ok(ExceptionPurchasePageResponse.from(result));
    }

    /**
     * Closes an {@code EXCEPTION} purchase as {@code REFUNDED} or {@code COMPENSATED} with a
     * mandatory note (#539). Repeating the identical request answers {@code 200} without any
     * side effect; a different decision on a resolved purchase is a {@code 409}.
     */
    @PostMapping("/{purchaseId}/resolution")
    public ResponseEntity<PurchaseResolutionResult> resolve(
        @PathVariable String purchaseId, @Valid @RequestBody ResolveExceptionPurchaseRequest request,
        Authentication authentication
    ) {
        requireAdmin(authentication);
        PurchaseResolutionResult result = resolveExceptionPurchaseUseCase.resolve(
            new ResolveExceptionPurchaseCommand(
                UUID.fromString(purchaseId), request.resolution(), request.note(),
                UUID.fromString(authentication.getName())
            )
        );
        return ResponseEntity.ok(result);
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
