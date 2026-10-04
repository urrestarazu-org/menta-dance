package com.menta.app.billing;

import com.menta.billing.application.port.in.PublishPaymentFulfillmentFailedPort;
import com.menta.billing.domain.model.PaymentId;
import com.menta.billing.domain.model.Reason;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Typed callable inside {@code api:app} that delegates the cross-module
 * call into Billing's {@link PublishPaymentFulfillmentFailedPort}. Mirrors
 * {@link MarkPurchaseExceptionAdapter} exactly (proposal D10; design C1/C2).
 */
@Component
public class PublishPaymentFulfillmentFailedAdapter {

    private final PublishPaymentFulfillmentFailedPort publishPaymentFulfillmentFailedPort;

    public PublishPaymentFulfillmentFailedAdapter(
        PublishPaymentFulfillmentFailedPort publishPaymentFulfillmentFailedPort
    ) {
        this.publishPaymentFulfillmentFailedPort = publishPaymentFulfillmentFailedPort;
    }

    /**
     * Publishes the payment-level fulfillment-failed event through the billing port.
     *
     * <p>The port commits the publication independently of the caller's transaction, so the event
     * survives even when that transaction is about to roll back.</p>
     *
     * @param paymentId the payment whose fulfillment could not be completed
     * @param userId the buyer to notify; may be {@code null} when the payment row itself is absent
     * @param reason the codified fallback reason
     */
    public void publish(PaymentId paymentId, UUID userId, Reason reason) {
        publishPaymentFulfillmentFailedPort.publish(paymentId, userId, reason);
    }
}
