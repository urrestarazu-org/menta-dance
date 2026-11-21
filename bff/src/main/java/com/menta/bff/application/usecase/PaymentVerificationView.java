package com.menta.bff.application.usecase;

import com.menta.bff.application.dto.PaymentStatusType;
import java.time.Instant;

/**
 * A payment as the administrator's screens show it: everything already worded in Spanish, so neither the
 * controller nor the template formats anything. The proof's signed URL is never part of it.
 *
 * @param paymentId     payment identifier
 * @param studentLabel  first characters of the buyer's id: the API gives no name
 * @param amountLabel   amount and currency, es-AR formatted
 * @param createdAt     instant on which the payment was created, for the machine-readable date
 * @param createdLabel  creation date and time in the academy's zone
 * @param methodLabel   payment method; the inbox is always bank transfer
 * @param modalityLabel what the payment buys, in Spanish
 * @param statusType    where the payment stands, to decide which actions the screen offers
 * @param statusLabel   where the payment stands, in Spanish
 * @param hasProof      whether the buyer uploaded a proof of transfer
 */
public record PaymentVerificationView(
        String paymentId, String studentLabel, String amountLabel, Instant createdAt, String createdLabel,
        String methodLabel, String modalityLabel, PaymentStatusType statusType, String statusLabel,
        boolean hasProof) {

    /** Whether the administrator can approve or reject it. */
    public boolean awaitingVerification() {
        return statusType == PaymentStatusType.AWAITING_MANUAL_VERIFICATION;
    }

    /** Whether the administrator can impose an outcome on it. */
    public boolean needsReconciliation() {
        return statusType == PaymentStatusType.RECONCILIATION_REQUIRED;
    }
}
