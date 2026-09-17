package com.menta.billing.application.usecase;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.module.paramnames.ParameterNamesModule;
import com.menta.billing.application.contract.BillingOutboxEventTypes;
import com.menta.billing.application.port.in.PublishPaymentFulfillmentFailedPort;
import com.menta.billing.application.port.out.BillingOutboxAppenderPort;
import com.menta.billing.application.port.out.Clock;
import com.menta.billing.domain.model.PaymentId;
import com.menta.billing.domain.model.Reason;
import com.menta.shared.billing.PaymentFulfillmentFailedOutboxPayload;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Producer for the payment-level {@code billing.PaymentFulfillmentFailed}
 * outbox event (proposal D10; design C1/C2).
 *
 * <h2>Why {@code REQUIRES_NEW}, never {@code REQUIRED} (C2)</h2>
 * <p>The three call sites this use case covers
 * ({@code PhysicalCapacityAssignmentOutboxEventHandler} lines 130/202/239)
 * call {@code markException} with no {@code Purchase} row existing yet
 * (design C1) — that call throws {@code PaymentNotFoundException}, a
 * pre-existing defect this change does not fix, which propagates and marks
 * the worker's ambient transaction rollback-only. {@code REQUIRES_NEW} is
 * the only propagation that lets this event commit independently, in its
 * own transaction, BEFORE the caller's doomed transaction is even attempted
 * to roll back. {@code REQUIRED} here would guarantee the notification is
 * never delivered on exactly the paths this use case exists to cover.</p>
 *
 * <h2>Idempotency</h2>
 * <p>Certain on redelivery: the worker retries the same doomed path every
 * time. V2 {@code idx_common_outbox_aggregate_event_type} absorbs the
 * duplicate as {@code DataIntegrityViolationException}, which the caller
 * (the handler) catches and logs — "already notified", same discipline as
 * {@link PublishPhysicalPaymentCompletedUseCase}.</p>
 */
@Component
public class PublishPaymentFulfillmentFailedUseCase implements PublishPaymentFulfillmentFailedPort {

    private final BillingOutboxAppenderPort outboxAppender;
    private final Clock clock;
    private final ObjectWriter writer;

    public PublishPaymentFulfillmentFailedUseCase(BillingOutboxAppenderPort outboxAppender, Clock clock) {
        this(outboxAppender, clock, new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .registerModule(new ParameterNamesModule())
            .configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
            .writerFor(PaymentFulfillmentFailedOutboxPayload.class));
    }

    PublishPaymentFulfillmentFailedUseCase(BillingOutboxAppenderPort outboxAppender, Clock clock, ObjectWriter writer) {
        this.outboxAppender = outboxAppender;
        this.clock = clock;
        this.writer = writer;
    }

    /**
     * Appends the event in its own, independent transaction (design C2 —
     * the load-bearing decision). No {@code Purchase} lookup: this event is
     * keyed on {@code PaymentId} alone, unlike D1's
     * {@code billing.PurchaseExceptioned}.
     */
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void publish(PaymentId paymentId, UUID userId, Reason reason) {
        outboxAppender.append(
            BillingOutboxEventTypes.PAYMENT_FULFILLMENT_FAILED,
            paymentId.getValue().toString(),
            writeJson(toPayload(paymentId, userId, reason))
        );
    }

    private PaymentFulfillmentFailedOutboxPayload toPayload(PaymentId paymentId, UUID userId, Reason reason) {
        return new PaymentFulfillmentFailedOutboxPayload(
            paymentId.getValue(), userId, reason.name(), clock.now()
        );
    }

    private String writeJson(PaymentFulfillmentFailedOutboxPayload payload) {
        try {
            return writer.writeValueAsString(payload);
        } catch (JsonProcessingException impossible) {
            throw new IllegalStateException(
                "PaymentFulfillmentFailedOutboxPayload JSON serialization failed", impossible
            );
        }
    }
}
