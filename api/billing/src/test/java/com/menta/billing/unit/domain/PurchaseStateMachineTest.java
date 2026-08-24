package com.menta.billing.unit.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.menta.billing.domain.model.FulfillmentStatus;
import com.menta.billing.domain.model.PaymentId;
import com.menta.billing.domain.model.Purchase;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * RED-GREEN: pure domain test for {@link Purchase}'s state-machine. Maps
 * every factory / transition described in design §5.5 to a literal
 * assertion.
 *
 * <p>purchases stay in PENDING_FULFILLMENT when the outbox handler exits
 * (design §5.5 "Open by design"). {@code grantsAttendance} is the only
 * observable signal consumers care about: ONLY {@code ASSIGNED} grants
 * attendance; {@code EXCEPTION} and {@code PENDING_FULFILLMENT} do NOT.</p>
 */
class PurchaseStateMachineTest {

    private static final PaymentId PAYMENT_ID = PaymentId.of(UUID.fromString("55555555-5555-5555-5555-555555555555"));
    private static final String SESSION_ID = "33333333-3333-3333-3333-333333333333";

    @Test
    void pendingFulfillment_supplier_does_not_grant_attendance() {
        Purchase p = Purchase.pendingFulfillment(PAYMENT_ID, SESSION_ID);
        assertThat(p.getStatus()).isEqualTo(FulfillmentStatus.PENDING_FULFILLMENT);
        assertThat(p.grantsAttendance()).isFalse();
    }

    @Test
    void assigned_supplier_grants_attendance() {
        Purchase assigned = Purchase.pendingFulfillment(PAYMENT_ID, SESSION_ID).assigned();
        assertThat(assigned.getStatus()).isEqualTo(FulfillmentStatus.ASSIGNED);
        assertThat(assigned.grantsAttendance()).isTrue();
    }

    @Test
    void exception_supplier_does_not_grant_attendance_and_keeps_identity() {
        Purchase exception = Purchase.pendingFulfillment(PAYMENT_ID, SESSION_ID).exception();
        assertThat(exception.getStatus()).isEqualTo(FulfillmentStatus.EXCEPTION);
        assertThat(exception.grantsAttendance()).isFalse();
        // Identity preservation — same paymentId and sessionId across transitions.
        assertThat(exception.getPaymentId()).isEqualTo(PAYMENT_ID);
        assertThat(exception.getPhysicalSessionId()).isEqualTo(SESSION_ID);
    }
}
