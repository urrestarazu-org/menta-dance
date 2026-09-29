package com.menta.billing.application.port.out;

import com.menta.billing.application.dto.PaymentDecisionNotification;

/**
 * Out-port for the buyer-facing approve/reject decision email (#33, US-BILLING-005, design
 * D5/D8/C8). Two methods, not one taking a decision, so D5's "two distinct templates" is a
 * compile-time property and {@link PaymentProofNotificationPort} (the unrelated ops-mailbox
 * adapter) can never be the one invoked.
 *
 * <p>Both methods MUST run outside the transaction that commits the payment/subscription change
 * and MUST NOT let a send failure propagate — D8: an otherwise-valid approve, reject, or
 * correction never rolls back because a buyer email failed to send.</p>
 */
public interface PaymentDecisionNotificationPort {

    /** Sends the Spanish confirmation template. */
    void notifyApproved(PaymentDecisionNotification notification);

    /** Sends the Spanish rejection template, carrying {@code notification.reason()} verbatim. */
    void notifyRejected(PaymentDecisionNotification notification);
}
