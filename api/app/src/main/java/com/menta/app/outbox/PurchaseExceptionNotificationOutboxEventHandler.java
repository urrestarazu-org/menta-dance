package com.menta.app.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.menta.auth.infrastructure.persistence.entity.OutboxRowJpaEntity;
import com.menta.billing.application.contract.BillingOutboxEventTypes;
import com.menta.billing.application.dto.PurchaseExceptionNotification;
import com.menta.billing.application.port.out.PurchaseExceptionNotificationPort;
import com.menta.shared.billing.PaymentFulfillmentFailedOutboxPayload;
import com.menta.shared.billing.PurchaseExceptionedOutboxPayload;
import org.springframework.stereotype.Component;

/**
 * Dispatches BOTH purchase-exception producers to the billing module's
 * notification port (proposal #209 Phase C, the first phase that actually
 * SENDS a notification): D1's {@code billing.PurchaseExceptioned} (carries a
 * {@code purchaseId}) and D10's {@code billing.PaymentFulfillmentFailed} (no
 * {@code Purchase} row exists at those call sites, design C1).
 *
 * <p>Unexpected/send failures propagate so the worker keeps the
 * {@code FAILED}/backoff lifecycle, same discipline as {@link
 * ActivationOutboxEventHandler}.</p>
 */
@Component
public class PurchaseExceptionNotificationOutboxEventHandler implements OutboxEventHandler {

    private final PurchaseExceptionNotificationPort notificationPort;
    private final ObjectMapper objectMapper;

    public PurchaseExceptionNotificationOutboxEventHandler(
        PurchaseExceptionNotificationPort notificationPort, ObjectMapper objectMapper
    ) {
        this.notificationPort = notificationPort;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean supports(String eventType) {
        return BillingOutboxEventTypes.PURCHASE_EXCEPTIONED.equals(eventType)
            || BillingOutboxEventTypes.PAYMENT_FULFILLMENT_FAILED.equals(eventType);
    }

    @Override
    public void handle(OutboxRowJpaEntity row) {
        notificationPort.notify(toNotification(row));
    }

    private PurchaseExceptionNotification toNotification(OutboxRowJpaEntity row) {
        if (BillingOutboxEventTypes.PURCHASE_EXCEPTIONED.equals(row.getEventType())) {
            PurchaseExceptionedOutboxPayload payload = parse(row, PurchaseExceptionedOutboxPayload.class);
            return new PurchaseExceptionNotification(
                payload.paymentId(), payload.purchaseId(), payload.userId(), payload.reason(),
                payload.occurredAt(), row.getEventType()
            );
        }
        PaymentFulfillmentFailedOutboxPayload payload = parse(row, PaymentFulfillmentFailedOutboxPayload.class);
        return new PurchaseExceptionNotification(
            payload.paymentId(), null, payload.userId(), payload.reason(), payload.occurredAt(), row.getEventType()
        );
    }

    private <T> T parse(OutboxRowJpaEntity row, Class<T> type) {
        try {
            return objectMapper.readValue(row.getPayload(), type);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(
                "Failed to deserialize " + row.getEventType() + " payload", e
            );
        }
    }
}
