package com.menta.billing.application.port.in;

import com.menta.billing.domain.model.PaymentId;
import com.menta.billing.domain.model.Reason;

/**
 * IN port that flips a {@code Purchase} from {@code PENDING_FULFILLMENT}
 * to {@code EXCEPTION} when one of the ADR-0028 residual cases trips
 * (proposal §4; design §4.2).
 *
 * <p>Called from {@code api:app}'s outbox handler after the capacity path
 * fails: {@link com.menta.physical.domain.exception.CapacityBelowAssignedException},
 * V7 {@code UNIQUE} collision, target no longer {@code SCHEDULED}, hold
 * expired, or monthly coverage changed.</p>
 */
public interface MarkPurchaseExceptionPort {

    void markException(PaymentId paymentId, Reason reason);
}
