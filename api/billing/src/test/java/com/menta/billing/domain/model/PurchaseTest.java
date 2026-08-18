package com.menta.billing.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PurchaseTest {

    @Test
    void pendingFulfillment_starts_in_pending_fulfillment() {
        Purchase purchase = Purchase.pendingFulfillment(PaymentId.generate(), "session-1");

        assertThat(purchase.getStatus()).isEqualTo(FulfillmentStatus.PENDING_FULFILLMENT);
        assertThat(purchase.getPhysicalSessionId()).isEqualTo("session-1");
        assertThat(purchase.grantsAttendance()).isFalse();
    }

    @Test
    void assigned_grants_attendance() {
        Purchase purchase = Purchase.pendingFulfillment(PaymentId.generate(), "session-1").assigned();

        assertThat(purchase.getStatus()).isEqualTo(FulfillmentStatus.ASSIGNED);
        assertThat(purchase.grantsAttendance()).isTrue();
    }

    @Test
    void exception_never_grants_attendance() {
        Purchase purchase = Purchase.pendingFulfillment(PaymentId.generate(), "session-1").exception();

        assertThat(purchase.getStatus()).isEqualTo(FulfillmentStatus.EXCEPTION);
        assertThat(purchase.grantsAttendance()).isFalse();
    }

    @Test
    void assigned_and_exception_preserve_id_and_payment_id() {
        Purchase original = Purchase.pendingFulfillment(PaymentId.generate(), "session-1");

        assertThat(original.assigned().getId()).isEqualTo(original.getId());
        assertThat(original.assigned().getPaymentId()).isEqualTo(original.getPaymentId());
    }
}
