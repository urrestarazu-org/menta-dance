package com.menta.billing.application.port.in;

import com.menta.billing.domain.model.PaymentId;
import com.menta.billing.domain.model.Reason;
import java.util.UUID;

/**
 * IN port for the payment-level {@code billing.PaymentFulfillmentFailed}
 * outbox event (proposal D10; design C1/C2). Called from {@code api:app}'s
 * {@code PhysicalCapacityAssignmentOutboxEventHandler} at the three
 * pre-{@code Purchase} sites, immediately before the (still-unfixed, C1)
 * {@code markException} call that throws {@link
 * com.menta.billing.domain.exception.PaymentNotFoundException} at those
 * exact sites.
 *
 * <p>Implementations MUST run in {@code REQUIRES_NEW} (design C2) so this
 * append commits independently of the caller's ambient transaction, which
 * is about to roll back.</p>
 */
public interface PublishPaymentFulfillmentFailedPort {

    /**
     * @param paymentId the payment whose fulfillment could not be completed.
     * @param userId the buyer to notify; may be {@code null} when the
     *     {@code Payment} row itself is absent (site 130, {@code payment == null}).
     * @param reason the codified fallback reason.
     */
    void publish(PaymentId paymentId, UUID userId, Reason reason);
}
