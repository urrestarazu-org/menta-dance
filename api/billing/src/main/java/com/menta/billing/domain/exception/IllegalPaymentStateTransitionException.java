package com.menta.billing.domain.exception;

import com.menta.billing.domain.model.ManualVerificationDecision;
import com.menta.billing.domain.model.PaymentId;
import com.menta.billing.domain.model.PaymentStatus;
import com.menta.shared.domain.exceptions.BusinessException;

/**
 * A manual resolution ({@link ManualVerificationDecision#APPROVED}/{@link
 * ManualVerificationDecision#REJECTED}) was attempted on a {@code Payment} that is not currently
 * {@link PaymentStatus.AwaitingManualVerification} (design C4).
 *
 * <p>Thrown loudly — unlike the 72h sweep's silent no-op — because a second admin decision on an
 * already-resolved payment is a human acting on stale information and must never resurrect or
 * re-terminate a subscription. Maps to {@code 409}.</p>
 */
public class IllegalPaymentStateTransitionException extends BusinessException {

    private static final String ERROR_CODE = "ILLEGAL_PAYMENT_STATE_TRANSITION";

    private final PaymentId paymentId;
    private final PaymentStatus status;
    private final ManualVerificationDecision decision;

    public IllegalPaymentStateTransitionException(
        PaymentId paymentId, PaymentStatus status, ManualVerificationDecision decision
    ) {
        super(
            ERROR_CODE,
            "Cannot apply decision " + decision + " to payment " + paymentId
                + ": current status is " + status.getClass().getSimpleName()
                + ", expected AwaitingManualVerification"
        );
        this.paymentId = paymentId;
        this.status = status;
        this.decision = decision;
    }

    public PaymentId getPaymentId() {
        return paymentId;
    }

    public PaymentStatus getStatus() {
        return status;
    }

    public ManualVerificationDecision getDecision() {
        return decision;
    }
}
