package com.menta.billing.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SubscriptionTest {

    @Test
    void pendingFulfillment_starts_in_pending_fulfillment() {
        Subscription subscription = Subscription.pendingFulfillment(PaymentId.generate(), "course-1");

        assertThat(subscription.getStatus()).isEqualTo(FulfillmentStatus.PENDING_FULFILLMENT);
        assertThat(subscription.getVirtualCourseId()).isEqualTo("course-1");
        assertThat(subscription.grantsAccess()).isFalse();
    }

    @Test
    void assigned_grants_access() {
        Subscription subscription = Subscription.pendingFulfillment(PaymentId.generate(), "course-1").assigned();

        assertThat(subscription.getStatus()).isEqualTo(FulfillmentStatus.ASSIGNED);
        assertThat(subscription.grantsAccess()).isTrue();
    }

    @Test
    void exception_never_grants_access() {
        Subscription subscription = Subscription.pendingFulfillment(PaymentId.generate(), "course-1").exception();

        assertThat(subscription.getStatus()).isEqualTo(FulfillmentStatus.EXCEPTION);
        assertThat(subscription.grantsAccess()).isFalse();
    }

    @Test
    void assigned_and_exception_preserve_id_and_payment_id() {
        Subscription original = Subscription.pendingFulfillment(PaymentId.generate(), "course-1");

        assertThat(original.exception().getId()).isEqualTo(original.getId());
        assertThat(original.exception().getPaymentId()).isEqualTo(original.getPaymentId());
    }
}
