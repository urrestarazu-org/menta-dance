package com.menta.billing.domain.model;

import com.menta.billing.domain.exception.ProviderPaymentIdConflictException;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * A payment expected against a provider transaction (US-BILLING-002,
 * US-BILLING-010).
 *
 * <p>Created by the checkout flow in {@link PaymentStatus.AwaitingProvider}
 * with {@code providerPaymentId} <strong>absent</strong>. That absence is the
 * whole point: Mercado Pago's Checkout Pro returns a <em>preference</em> and a
 * redirect URL, and the real {@code payment.id} does not exist until the buyer
 * actually pays. The only identifier both sides share at checkout time is
 * {@code expectedExternalReference}, which we generate — so that, not the
 * provider's id, is the correlation key.</p>
 *
 * <p>{@code providerPaymentId} is bound later, by the webhook flow, via
 * {@link #bindProviderPaymentId(String)} — and only after the provider's own
 * authenticated response has been matched against every expected field. The
 * webhook itself contributes nothing but a {@code data.id}.</p>
 */
public final class Payment {

    private final PaymentId id;
    private final UUID userId;
    private final String providerPaymentId;
    private final Money expectedAmount;
    private final String expectedExternalReference;
    private final String expectedMerchantAccountId;
    private final PaymentTarget target;
    private final PaymentStatus status;
    private final Instant createdAt;

    public Payment(
        PaymentId id, UUID userId, String providerPaymentId, Money expectedAmount,
        String expectedExternalReference, String expectedMerchantAccountId, PaymentTarget target,
        PaymentStatus status, Instant createdAt
    ) {
        this.id = Objects.requireNonNull(id, "id cannot be null");
        this.userId = Objects.requireNonNull(userId, "userId cannot be null");
        if (providerPaymentId != null && providerPaymentId.isBlank()) {
            throw new IllegalArgumentException("providerPaymentId cannot be blank when present");
        }
        this.providerPaymentId = providerPaymentId;
        this.expectedAmount = Objects.requireNonNull(expectedAmount, "expectedAmount cannot be null");
        this.expectedExternalReference =
            Objects.requireNonNull(expectedExternalReference, "expectedExternalReference cannot be null");
        this.expectedMerchantAccountId =
            Objects.requireNonNull(expectedMerchantAccountId, "expectedMerchantAccountId cannot be null");
        this.target = Objects.requireNonNull(target, "target cannot be null");
        this.status = Objects.requireNonNull(status, "status cannot be null");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt cannot be null");
    }

    /**
     * The checkout's entry point (US-BILLING-010 escenario 1): local payment
     * first, provider charge afterwards — never the other way round, so a
     * confirmation can always be matched against something we already wrote.
     */
    public static Payment awaitingProvider(
        PaymentId id, UUID userId, Money expectedAmount, String expectedExternalReference,
        String expectedMerchantAccountId, PaymentTarget target, Instant createdAt
    ) {
        return new Payment(
            id, userId, null, expectedAmount, expectedExternalReference, expectedMerchantAccountId,
            target, new PaymentStatus.AwaitingProvider(), createdAt
        );
    }

    /**
     * US-BILLING-002: "para todo estado del proveedor, exigir coincidencia
     * exacta... referencia externa, cuenta/merchant, importe y moneda". Not
     * just for {@code approved} — every provider status is checked the same
     * way before it is ever trusted.
     */
    public boolean matchesExpected(ProviderOutcome outcome) {
        return expectedAmount.equals(outcome.amount())
            && expectedExternalReference.equals(outcome.externalReference())
            && expectedMerchantAccountId.equals(outcome.merchantAccountId());
    }

    /**
     * Associates the provider's own payment id with this payment, once and
     * only once.
     *
     * <ul>
     *   <li>Not bound yet → returns a bound copy.</li>
     *   <li>Already bound to the <em>same</em> id → returns {@code this}. A
     *       replayed or duplicated webhook is an expected condition, not an
     *       error (same monotonicity rule as {@link PaymentStatus}).</li>
     *   <li>Already bound to a <em>different</em> id → throws. Two provider
     *       transactions claiming one local payment is never something to
     *       resolve automatically: it goes to manual reconciliation, and the
     *       existing binding is never overwritten.</li>
     * </ul>
     *
     * @throws ProviderPaymentIdConflictException if a different id is already bound
     */
    public Payment bindProviderPaymentId(String newProviderPaymentId) {
        if (newProviderPaymentId == null || newProviderPaymentId.isBlank()) {
            throw new IllegalArgumentException("providerPaymentId cannot be null or blank");
        }
        if (providerPaymentId == null) {
            return copyWith(newProviderPaymentId, status);
        }
        if (providerPaymentId.equals(newProviderPaymentId)) {
            return this;
        }
        throw new ProviderPaymentIdConflictException(providerPaymentId, newProviderPaymentId);
    }

    public boolean isBound() {
        return providerPaymentId != null;
    }

    /**
     * Applies a verified provider outcome. A mismatch never reaches {@link
     * PaymentStatus.Pending#resolve} — it always routes to {@code
     * ReconciliationRequired} without touching any other field, regardless
     * of which provider status was reported. See {@link PaymentStatus} for
     * why a terminal payment is untouched by this call (monotonicity).
     */
    public Payment applyProviderOutcome(ProviderOutcome outcome, Instant now) {
        if (!matchesExpected(outcome)) {
            return withStatus(new PaymentStatus.ReconciliationRequired(
                "provider outcome does not match expected payment fields"
            ));
        }
        return switch (status) {
            case PaymentStatus.Pending pending -> withStatus(pending.resolve(outcome, now));
            case PaymentStatus.Completed completed -> this;
            case PaymentStatus.Rejected rejected -> this;
            case PaymentStatus.Cancelled cancelled -> this;
            case PaymentStatus.Expired expired -> this;
        };
    }

    /** Used when the inbox exhausts retries querying the provider — never a mismatch. */
    public Payment markReconciliationRequired(String reason) {
        return switch (status) {
            case PaymentStatus.Pending pending -> withStatus(new PaymentStatus.ReconciliationRequired(reason));
            case PaymentStatus.Completed completed -> this;
            case PaymentStatus.Rejected rejected -> this;
            case PaymentStatus.Cancelled cancelled -> this;
            case PaymentStatus.Expired expired -> this;
        };
    }

    public boolean isTerminal() {
        return !(status instanceof PaymentStatus.Pending);
    }

    public Optional<Instant> confirmedAt() {
        return status instanceof PaymentStatus.Completed completed
            ? Optional.of(completed.confirmedAt())
            : Optional.empty();
    }

    private Payment withStatus(PaymentStatus newStatus) {
        return copyWith(providerPaymentId, newStatus);
    }

    private Payment copyWith(String newProviderPaymentId, PaymentStatus newStatus) {
        return new Payment(
            id, userId, newProviderPaymentId, expectedAmount, expectedExternalReference,
            expectedMerchantAccountId, target, newStatus, createdAt
        );
    }

    public PaymentId getId() {
        return id;
    }

    /** Whose payment this is (US-BILLING-010) — always the authenticated user, never a client-supplied value. */
    public UUID getUserId() {
        return userId;
    }

    /** Empty until the webhook flow binds it — see this class's Javadoc for why it cannot be known at checkout. */
    public Optional<String> getProviderPaymentId() {
        return Optional.ofNullable(providerPaymentId);
    }

    public Money getExpectedAmount() {
        return expectedAmount;
    }

    public String getExpectedExternalReference() {
        return expectedExternalReference;
    }

    public String getExpectedMerchantAccountId() {
        return expectedMerchantAccountId;
    }

    public PaymentTarget getTarget() {
        return target;
    }

    public PaymentStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
