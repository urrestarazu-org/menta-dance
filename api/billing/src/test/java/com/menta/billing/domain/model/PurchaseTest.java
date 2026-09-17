package com.menta.billing.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PurchaseTest {

    @Test
    void pendingFulfillment_starts_in_pending_fulfillment() {
        Purchase purchase = Purchase.pendingFulfillment(PaymentId.generate(), List.of("session-1"));

        assertThat(purchase.getStatus()).isEqualTo(FulfillmentStatus.PENDING_FULFILLMENT);
        assertThat(purchase.getPhysicalSessionIds()).containsExactly("session-1");
        assertThat(purchase.grantsAttendance()).isFalse();
    }

    @Test
    void assigned_grants_attendance() {
        Purchase purchase = Purchase.pendingFulfillment(PaymentId.generate(), List.of("session-1")).assigned();

        assertThat(purchase.getStatus()).isEqualTo(FulfillmentStatus.ASSIGNED);
        assertThat(purchase.grantsAttendance()).isTrue();
    }

    @Test
    void exception_never_grants_attendance() {
        Purchase purchase = Purchase.pendingFulfillment(PaymentId.generate(), List.of("session-1")).exception();

        assertThat(purchase.getStatus()).isEqualTo(FulfillmentStatus.EXCEPTION);
        assertThat(purchase.grantsAttendance()).isFalse();
    }

    @Test
    void assigned_and_exception_preserve_id_and_payment_id() {
        Purchase original = Purchase.pendingFulfillment(PaymentId.generate(), List.of("session-1"));

        assertThat(original.assigned().getId()).isEqualTo(original.getId());
        assertThat(original.assigned().getPaymentId()).isEqualTo(original.getPaymentId());
    }

    @Test
    void pendingFulfillment_accepts_an_ordered_multi_session_list() {
        Purchase purchase =
            Purchase.pendingFulfillment(PaymentId.generate(), List.of("session-1", "session-2", "session-3"));

        assertThat(purchase.getPhysicalSessionIds()).containsExactly("session-1", "session-2", "session-3");
    }

    @Test
    void assigned_preserves_the_ordered_session_list() {
        Purchase purchase = Purchase
            .pendingFulfillment(PaymentId.generate(), List.of("session-1", "session-2"))
            .assigned();

        assertThat(purchase.getPhysicalSessionIds()).containsExactly("session-1", "session-2");
        assertThat(purchase.grantsAttendance()).isTrue();
    }

    @Test
    void exception_preserves_the_ordered_session_list() {
        Purchase purchase = Purchase
            .pendingFulfillment(PaymentId.generate(), List.of("session-1", "session-2"))
            .exception();

        assertThat(purchase.getPhysicalSessionIds()).containsExactly("session-1", "session-2");
        assertThat(purchase.grantsAttendance()).isFalse();
    }

    @Test
    void physicalSessionIds_is_defensively_copied_from_the_constructor_argument() {
        List<String> mutable = new ArrayList<>(List.of("session-1", "session-2"));
        Purchase purchase = Purchase.pendingFulfillment(PaymentId.generate(), mutable);

        mutable.add("session-3");

        assertThat(purchase.getPhysicalSessionIds()).containsExactly("session-1", "session-2");
    }

    @Test
    void physicalSessionIds_returned_by_the_getter_is_immutable() {
        Purchase purchase = Purchase.pendingFulfillment(PaymentId.generate(), List.of("session-1"));

        assertThatThrownBy(() -> purchase.getPhysicalSessionIds().add("session-2"))
            .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void rejects_an_empty_session_list_at_construction() {
        assertThatThrownBy(() -> Purchase.pendingFulfillment(PaymentId.generate(), List.<String>of()))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejects_a_null_session_list_at_construction() {
        assertThatThrownBy(() -> new Purchase(
            UUID.randomUUID(), PaymentId.generate(), null, FulfillmentStatus.PENDING_FULFILLMENT
        )).isInstanceOf(NullPointerException.class);
    }

    @Test
    void exception_status_accepts_an_empty_session_list_at_construction() {
        Purchase purchase = new Purchase(
            UUID.randomUUID(), PaymentId.generate(), List.<String>of(), FulfillmentStatus.EXCEPTION
        );

        assertThat(purchase.getStatus()).isEqualTo(FulfillmentStatus.EXCEPTION);
        assertThat(purchase.getPhysicalSessionIds()).isEmpty();
        assertThat(purchase.grantsAttendance()).isFalse();
    }

    @Test
    void rejects_an_empty_session_list_for_assigned_status_at_construction() {
        assertThatThrownBy(() -> new Purchase(
            UUID.randomUUID(), PaymentId.generate(), List.<String>of(), FulfillmentStatus.ASSIGNED
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejects_an_empty_session_list_for_pending_fulfillment_status_at_construction() {
        assertThatThrownBy(() -> new Purchase(
            UUID.randomUUID(), PaymentId.generate(), List.<String>of(), FulfillmentStatus.PENDING_FULFILLMENT
        )).isInstanceOf(IllegalArgumentException.class);
    }

    // #238: Purchase.exception(...) builds a row directly at EXCEPTION —
    // there is no legitimate intermediate PENDING_FULFILLMENT state for a
    // payment that never resolved to any schedulable session.
    @Test
    void exception_factory_builds_a_purchase_directly_at_exception_status() {
        Purchase purchase = Purchase.exception(PaymentId.generate(), List.of());

        assertThat(purchase.getStatus()).isEqualTo(FulfillmentStatus.EXCEPTION);
        assertThat(purchase.getPhysicalSessionIds()).isEmpty();
        assertThat(purchase.grantsAttendance()).isFalse();
    }

    @Test
    void exception_factory_accepts_a_non_empty_session_list_too() {
        Purchase purchase = Purchase.exception(PaymentId.generate(), List.of("session-1"));

        assertThat(purchase.getStatus()).isEqualTo(FulfillmentStatus.EXCEPTION);
        assertThat(purchase.getPhysicalSessionIds()).containsExactly("session-1");
    }

    @Test
    void exception_factory_generates_a_fresh_random_id() {
        Purchase first = Purchase.exception(PaymentId.generate(), List.of());
        Purchase second = Purchase.exception(PaymentId.generate(), List.of());

        assertThat(first.getId()).isNotEqualTo(second.getId());
    }
}
