package com.menta.billing.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * A payment expected against a provider transaction (US-BILLING-002).
 *
 * <p>Created by a checkout flow not yet built (out of this issue's scope) in
 * {@link PaymentStatus.AwaitingProvider}, with {@code providerPaymentId}
 * already known — the webhook flow only verifies and confirms, it never
 * creates a payment from scratch.</p>
 */
public final class Payment {

    private final PaymentId id;
    private final String providerPaymentId;
    private final Money expectedAmount;
    private final String expectedExternalReference;
    private final String expectedMerchantAccountId;
    private final PaymentTarget target;
    private final PaymentStatus status;
    private final Instant createdAt;

    public Payment(
        PaymentId id, String providerPaymentId, Money expectedAmount, String expectedExternalReference,
        String expectedMerchantAccountId, PaymentTarget target, PaymentStatus status, Instant createdAt
    ) {
        this.id = Objects.requireNonNull(id, "id cannot be null");
        this.providerPaymentId = Objects.requireNonNull(providerPaymentId, "providerPaymentId cannot be null");
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
        return new Payment(
            id, providerPaymentId, expectedAmount, expectedExternalReference,
            expectedMerchantAccountId, target, newStatus, createdAt
        );
    }

    public PaymentId getId() {
        return id;
    }

    public String getProviderPaymentId() {
        return providerPaymentId;
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
