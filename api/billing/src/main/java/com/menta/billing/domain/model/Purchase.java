package com.menta.billing.domain.model;

import java.util.Objects;
import java.util.UUID;

/**
 * Physical-class capacity fulfillment for a {@link Payment} (US-BILLING-002).
 * Created once the payment reaches {@link PaymentStatus.Completed}; never
 * before — the payment itself is the financial settlement and does not wait
 * on fulfillment succeeding.
 */
public final class Purchase {

    private final UUID id;
    private final PaymentId paymentId;
    private final String physicalSessionId;
    private final FulfillmentStatus status;

    public Purchase(UUID id, PaymentId paymentId, String physicalSessionId, FulfillmentStatus status) {
        this.id = Objects.requireNonNull(id, "id cannot be null");
        this.paymentId = Objects.requireNonNull(paymentId, "paymentId cannot be null");
        this.physicalSessionId = Objects.requireNonNull(physicalSessionId, "physicalSessionId cannot be null");
        this.status = Objects.requireNonNull(status, "status cannot be null");
    }

    public static Purchase pendingFulfillment(PaymentId paymentId, String physicalSessionId) {
        return new Purchase(UUID.randomUUID(), paymentId, physicalSessionId, FulfillmentStatus.PENDING_FULFILLMENT);
    }

    public Purchase assigned() {
        return new Purchase(id, paymentId, physicalSessionId, FulfillmentStatus.ASSIGNED);
    }

    public Purchase exception() {
        return new Purchase(id, paymentId, physicalSessionId, FulfillmentStatus.EXCEPTION);
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

    public String getPhysicalSessionId() {
        return physicalSessionId;
    }

    public FulfillmentStatus getStatus() {
        return status;
    }
}
