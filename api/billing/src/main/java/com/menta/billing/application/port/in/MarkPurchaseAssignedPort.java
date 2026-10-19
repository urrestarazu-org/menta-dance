package com.menta.billing.application.port.in;

import com.menta.billing.domain.model.PaymentId;

/**
 * IN port that flips a {@code Purchase} from {@code PENDING_FULFILLMENT} to
 * {@code ASSIGNED} once every eligible session has been successfully claimed
 * (design A3, Data Flow: {@code assignAll(ordered claims) -- ok --> purchase.assigned()}).
 *
 * <p>Called from {@code api:app}'s outbox handler immediately after {@code
 * PhysicalCapacityAssignmentAdapter#assignAll} returns without throwing
 * {@link com.menta.physical.domain.exception.CapacityBelowAssignedException}
 * — the mirror image of {@link MarkPurchaseExceptionPort}, which handles the
 * failure branch of the same call.</p>
 */
public interface MarkPurchaseAssignedPort {

    /**
     * Marks the purchase of a payment as {@code ASSIGNED}.
     *
     * <p>Implemented by {@code MarkPurchaseAssignedUseCase}: an unknown payment fails with {@code
     * PaymentNotFoundException}, a purchase that is already {@code ASSIGNED} is left untouched, and
     * one in {@code EXCEPTION} is refused with {@code IllegalPurchaseStateTransitionException}.
     */
    void markAssigned(PaymentId paymentId);
}
