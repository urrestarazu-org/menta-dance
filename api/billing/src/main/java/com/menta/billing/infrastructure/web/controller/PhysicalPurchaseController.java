package com.menta.billing.infrastructure.web.controller;

import com.menta.billing.application.dto.CreatePhysicalPurchaseCheckoutCommand;
import com.menta.billing.application.dto.PhysicalPurchaseCheckoutResult;
import com.menta.billing.application.port.in.CreatePhysicalPurchaseCheckoutUseCase;
import com.menta.billing.infrastructure.web.dto.CreatePhysicalPurchaseRequest;
import com.menta.billing.infrastructure.web.dto.PhysicalPurchaseCheckoutResponse;
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
 * HTTP adapter for the physical purchase checkout (#41, US-PHYSICAL-004).
 *
 * <p>{@code SecurityConfig} (in {@code api:auth}) gates {@code POST} with a
 * dedicated {@code .authenticated()} matcher; no role is required. The owning
 * user is read from the token and never from the body or a request
 * parameter — same {@code actingUserId} pattern {@code SubscriptionController}
 * uses.</p>
 */
@RestController
@RequestMapping("/api/v1/billing/physical/purchases")
@PhysicalPurchaseEndpoint
public class PhysicalPurchaseController {

    private final CreatePhysicalPurchaseCheckoutUseCase createPhysicalPurchaseCheckoutUseCase;

    public PhysicalPurchaseController(CreatePhysicalPurchaseCheckoutUseCase createPhysicalPurchaseCheckoutUseCase) {
        this.createPhysicalPurchaseCheckoutUseCase = createPhysicalPurchaseCheckoutUseCase;
    }

    @PostMapping
    public ResponseEntity<PhysicalPurchaseCheckoutResponse> create(
        @Valid @RequestBody CreatePhysicalPurchaseRequest request, Authentication authentication
    ) {
        PhysicalPurchaseCheckoutResult result = createPhysicalPurchaseCheckoutUseCase.create(
            new CreatePhysicalPurchaseCheckoutCommand(
                actingUserId(authentication), request.quoteId(), request.paymentMethod(), request.idempotencyKey()
            )
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(PhysicalPurchaseCheckoutResponse.from(result));
    }

    private static UUID actingUserId(Authentication authentication) {
        return UUID.fromString(authentication.getName());
    }
}
