package com.menta.app.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.menta.auth.infrastructure.persistence.entity.OutboxRowJpaEntity;
import com.menta.billing.application.contract.BillingOutboxEventTypes;
import com.menta.shared.billing.PurchaseResolvedOutboxPayload;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Claims {@code billing.PurchaseResolved} (#539) so {@link OutboxReconciliationWorker} does not
 * fail the row for lack of a handler. The resolution closes the purchase with no fulfillment and
 * touches no other module, so there is no side effect to project: the handler only records the
 * decision in the log. The event carries no admin note and neither does the log line.
 *
 * <p>An unreadable payload propagates so the worker keeps the {@code FAILED}/backoff lifecycle.</p>
 */
@Component
@RequiredArgsConstructor
public class PurchaseResolvedOutboxEventHandler implements OutboxEventHandler {

    private static final Logger log = LoggerFactory.getLogger(PurchaseResolvedOutboxEventHandler.class);

    private final ObjectMapper objectMapper;

    @Override
    public boolean supports(String eventType) {
        return BillingOutboxEventTypes.PURCHASE_RESOLVED.equals(eventType);
    }

    @Override
    public void handle(OutboxRowJpaEntity row) {
        PurchaseResolvedOutboxPayload payload = parse(row);
        log.info(
            "Purchase resolved purchaseId={} paymentId={} resolution={} resolvedBy={}",
            payload.purchaseId(), payload.paymentId(), payload.resolution(), payload.resolvedBy()
        );
    }

    private PurchaseResolvedOutboxPayload parse(OutboxRowJpaEntity row) {
        try {
            return objectMapper.readValue(row.getPayload(), PurchaseResolvedOutboxPayload.class);
        } catch (JsonProcessingException e) {
            throw new OutboxDispatchException("Failed to deserialize " + row.getEventType() + " payload", e);
        }
    }
}
