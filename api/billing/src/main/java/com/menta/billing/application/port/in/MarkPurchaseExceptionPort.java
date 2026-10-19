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

    /**
     * Marks the purchase of a payment as {@code EXCEPTION}, recording why.
     *
     * <p>Implemented by {@code MarkPurchaseExceptionUseCase}: an unknown payment fails with {@code
     * PaymentNotFoundException}, a purchase already in {@code EXCEPTION} is left untouched, and one
     * that is {@code ASSIGNED} is refused with {@code IllegalPurchaseStateTransitionException}. The
     * reason is not stored on the purchase; it only travels in the {@code
     * billing.PurchaseExceptioned} outbox payload, which is appended on the first transition.
     */
    void markException(PaymentId paymentId, Reason reason);
}
