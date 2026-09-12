package com.menta.billing.domain.model;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Physical-class capacity fulfillment for a {@link Payment} (US-BILLING-002,
 * #41 US-PHYSICAL-004). Created once the payment reaches {@link
 * PaymentStatus.Completed}; never before — the payment itself is the
 * financial settlement and does not wait on fulfillment succeeding.
 *
 * <p>Covers an ordered, non-empty set of physical sessions — one for an
 * {@code INDIVIDUAL} quote, N for a {@code MONTHLY} one (design A1). There is
 * no per-session status: all-or-nothing assignment (design A3) makes {@link
 * #status} complete information about every session in the list.</p>
 */
public final class Purchase {

    private final UUID id;
    private final PaymentId paymentId;
    private final List<String> physicalSessionIds;
    private final FulfillmentStatus status;

    public Purchase(UUID id, PaymentId paymentId, List<String> physicalSessionIds, FulfillmentStatus status) {
        this.id = Objects.requireNonNull(id, "id cannot be null");
        this.paymentId = Objects.requireNonNull(paymentId, "paymentId cannot be null");
        Objects.requireNonNull(physicalSessionIds, "physicalSessionIds cannot be null");
        if (physicalSessionIds.isEmpty()) {
            throw new IllegalArgumentException("physicalSessionIds cannot be empty");
        }
        this.physicalSessionIds = List.copyOf(physicalSessionIds);
        this.status = Objects.requireNonNull(status, "status cannot be null");
    }

    public static Purchase pendingFulfillment(PaymentId paymentId, List<String> physicalSessionIds) {
        return new Purchase(UUID.randomUUID(), paymentId, physicalSessionIds, FulfillmentStatus.PENDING_FULFILLMENT);
    }

    public Purchase assigned() {
        return new Purchase(id, paymentId, physicalSessionIds, FulfillmentStatus.ASSIGNED);
    }

    public Purchase exception() {
        return new Purchase(id, paymentId, physicalSessionIds, FulfillmentStatus.EXCEPTION);
    }

    /** No asistencia se habilita hasta este punto (docs/06-BILLING-API.md) — Physical's own responsibility. */
    public boolean grantsAttendance() {
        return status == FulfillmentStatus.ASSIGNED;
    }

    public UUID getId() {
        return id;
    }

    public PaymentId getPaymentId() {
        return paymentId;
    }

    public List<String> getPhysicalSessionIds() {
        return physicalSessionIds;
    }

    public FulfillmentStatus getStatus() {
        return status;
    }
}
