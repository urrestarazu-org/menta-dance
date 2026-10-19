package com.menta.billing.infrastructure.web.controller;

import com.menta.billing.application.dto.CancelSubscriptionCommand;
import com.menta.billing.application.dto.CancellationResult;
import com.menta.billing.application.dto.CancellationTarget;
import com.menta.billing.application.dto.CreateSubscriptionCheckoutCommand;
import com.menta.billing.application.dto.CurrentSubscriptionResult;
import com.menta.billing.application.dto.SubscriptionCheckoutResult;
import com.menta.billing.application.port.in.CancelSubscriptionUseCase;
import com.menta.billing.application.port.in.CreateSubscriptionCheckoutUseCase;
import com.menta.billing.application.port.in.GetCurrentSubscriptionUseCase;
import com.menta.billing.application.port.in.GetSubscriptionHistoryUseCase;
import com.menta.billing.infrastructure.web.dto.CancelSubscriptionResponse;
import com.menta.billing.infrastructure.web.dto.CreateSubscriptionRequest;
import com.menta.billing.infrastructure.web.dto.CurrentSubscriptionResponse;
import com.menta.billing.infrastructure.web.dto.SubscriptionCheckoutResponse;
import com.menta.billing.infrastructure.web.dto.SubscriptionHistoryItemResponse;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * HTTP adapter for subscription checkout, self-service cancellation and the read-side status
 * views (US-BILLING-004, US-BILLING-010, US-BILLING-011).
 *
 * <p>{@code SecurityConfig} gates {@code POST}, {@code DELETE /me}, {@code GET /me} and {@code
 * GET /me/history} with dedicated {@code .authenticated()} matchers; no role is required for any
 * of them. The owning user is read from the token and never from the body or a request
 * parameter — the same {@code actingUserId} pattern {@code PhysicalCoursePricingController}
 * uses.</p>
 */
@RestController
@RequestMapping("/api/v1/billing/subscriptions")
@SubscriptionEndpoint
public class SubscriptionController {

    private final CreateSubscriptionCheckoutUseCase createSubscriptionCheckoutUseCase;
    private final CancelSubscriptionUseCase cancelSubscriptionUseCase;
    private final GetCurrentSubscriptionUseCase getCurrentSubscriptionUseCase;
    private final GetSubscriptionHistoryUseCase getSubscriptionHistoryUseCase;

    public SubscriptionController(
        CreateSubscriptionCheckoutUseCase createSubscriptionCheckoutUseCase,
        CancelSubscriptionUseCase cancelSubscriptionUseCase,
        GetCurrentSubscriptionUseCase getCurrentSubscriptionUseCase,
        GetSubscriptionHistoryUseCase getSubscriptionHistoryUseCase
    ) {
        this.createSubscriptionCheckoutUseCase = createSubscriptionCheckoutUseCase;
        this.cancelSubscriptionUseCase = cancelSubscriptionUseCase;
        this.getCurrentSubscriptionUseCase = getCurrentSubscriptionUseCase;
        this.getSubscriptionHistoryUseCase = getSubscriptionHistoryUseCase;
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

    /**
     * Current subscription (US-BILLING-004). Resolves PENDING, ACTIVE or EXPIRED only (D1); a
     * thrown {@code NoSubscriptionException} is never caught here — it reaches {@code
     * SubscriptionExceptionHandler} untranslated, the same pattern every other exception on this
     * controller follows. This route falls under the class's inherited {@code
     * @SubscriptionEndpoint} advice.
     */
    @GetMapping("/me")
    public ResponseEntity<CurrentSubscriptionResponse> currentOwn(Authentication authentication) {
        CurrentSubscriptionResult result = getCurrentSubscriptionUseCase.current(actingUserId(authentication));
        return ResponseEntity.ok(CurrentSubscriptionResponse.from(result));
    }

    /**
     * Full subscription history, newest first (US-BILLING-004). Never throws for a user with no
     * rows — an empty list is a valid {@code 200}. This route falls under the class's inherited
     * {@code @SubscriptionEndpoint} advice.
     */
    @GetMapping("/me/history")
    public ResponseEntity<List<SubscriptionHistoryItemResponse>> historyOwn(Authentication authentication) {
        List<SubscriptionHistoryItemResponse> history = getSubscriptionHistoryUseCase
            .history(actingUserId(authentication)).stream()
            .map(SubscriptionHistoryItemResponse::from)
            .toList();
        return ResponseEntity.ok(history);
    }

    private static UUID actingUserId(Authentication authentication) {
        return UUID.fromString(authentication.getName());
    }
}
