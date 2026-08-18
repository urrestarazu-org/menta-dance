package com.menta.billing.domain.model;

import java.util.Objects;
import java.util.UUID;

/**
 * Virtual-course access fulfillment for a {@link Payment} (US-BILLING-002).
 * Mirrors {@link Purchase}'s fulfillment shape — see {@link
 * FulfillmentStatus}'s Javadoc for why the two share one state machine.
 */
public final class Subscription {

    private final UUID id;
    private final PaymentId paymentId;
    private final String virtualCourseId;
    private final FulfillmentStatus status;

    public Subscription(UUID id, PaymentId paymentId, String virtualCourseId, FulfillmentStatus status) {
        this.id = Objects.requireNonNull(id, "id cannot be null");
        this.paymentId = Objects.requireNonNull(paymentId, "paymentId cannot be null");
        this.virtualCourseId = Objects.requireNonNull(virtualCourseId, "virtualCourseId cannot be null");
        this.status = Objects.requireNonNull(status, "status cannot be null");
    }

    public static Subscription pendingFulfillment(PaymentId paymentId, String virtualCourseId) {
        return new Subscription(
            UUID.randomUUID(), paymentId, virtualCourseId, FulfillmentStatus.PENDING_FULFILLMENT
        );
    }

    public Subscription assigned() {
        return new Subscription(id, paymentId, virtualCourseId, FulfillmentStatus.ASSIGNED);
    }

    public Subscription exception() {
        return new Subscription(id, paymentId, virtualCourseId, FulfillmentStatus.EXCEPTION);
    }

    public boolean grantsAccess() {
        return status == FulfillmentStatus.ASSIGNED;
    }

    public UUID getId() {
        return id;
    }

    public PaymentId getPaymentId() {
        return paymentId;
    }

    public String getVirtualCourseId() {
        return virtualCourseId;
    }

    public FulfillmentStatus getStatus() {
        return status;
    }
}
