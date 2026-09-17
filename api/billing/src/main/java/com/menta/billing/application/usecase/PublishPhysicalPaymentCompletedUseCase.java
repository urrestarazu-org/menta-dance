package com.menta.billing.application.usecase;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.module.paramnames.ParameterNamesModule;
import com.menta.billing.application.contract.BillingOutboxEventTypes;
import com.menta.billing.application.port.out.BillingOutboxAppenderPort;
import com.menta.billing.domain.model.Payment;
import com.menta.billing.domain.model.PaymentStatus;
import com.menta.billing.domain.model.PaymentTarget;
import com.menta.shared.billing.PaymentCompletedOutboxPayload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

/**
 * Producer for the {@code billing.PhysicalPaymentCompleted} outbox event
 * (proposal §5 Approach — "Event production"; design §5.1 — transactional
 * outbox semantics; tasks TASK-003).
 *
 * <h2>Lifecycle</h2>
 * <p>Called from {@link PaymentVerificationService#ensureFulfillment}
 * inside the payment-status commit. The use case persists the outbox row
 * synchronously through an appender that joins that transaction. Consequently,
 * a rollback removes both the payment update and its outbox row, while a
 * commit makes them visible atomically to the reconciler.</p>
 *
 * <h2>Scope</h2>
 * <p>Only fires for {@code PaymentTarget.Physical} that has reached
 * {@link PaymentStatus.Completed}: Virtual fulfillment already lives in
 * {@code ensureSubscription} (this class never participates in the Virtual
 * path). A non-Completed or non-Physical payment is a silent no-op at this
 * layer.</p>
 *
 * <h2>Idempotency (#242)</h2>
 * <p>Two complementary layers:
 * <ul>
 *   <li><b>App-level pre-check (the normal redelivery path)</b> — {@code
 *       BillingOutboxAppenderPort#existsForAggregateAndEventType} runs BEFORE
 *       {@code append}. An ordinary sequential redelivery (the webhook worker
 *       retrying the same {@code providerPaymentId}) finds the prior call's
 *       row already committed and skips the insert entirely, so it never
 *       touches the DB constraint. This matters because {@code append} joins
 *       the caller's ambient transaction (REQUIRED, for the atomicity this
 *       class's Lifecycle section describes) — a {@code
 *       DataIntegrityViolationException} thrown from inside that call marks
 *       the WHOLE ambient transaction rollback-only at the framework level
 *       the instant it escapes {@code append}'s own proxy, regardless of
 *       whether a caller further up subsequently catches it (confirmed via
 *       {@code UnexpectedRollbackException} at the worker's commit boundary
 *       under a real Testcontainers redelivery before this pre-check
 *       existed) — so relying on the exception alone here would silently
 *       revert the caller's successful work on every ordinary retry.</li>
 *   <li><b>V2 {@code uk_common_outbox_aggregate_event_type} (the race backstop)</b>
 *       — if two redeliveries race past the pre-check concurrently, the
 *       loser's {@code append} still raises {@code DataIntegrityViolationException}.
 *       It is caught here and logged as a no-op (same discipline as {@code
 *       PhysicalCapacityAssignmentOutboxEventHandler#publishPaymentFulfillmentFailed}),
 *       purely so nothing propagates an unhandled exception out of this
 *       method for that rare case — the loser's own ambient transaction
 *       still rolls back at commit (same framework mechanics as above), and
 *       the next redelivery attempt finds the winner's row via the pre-check
 *       and skips cleanly.</li>
 * </ul></p>
 */
@Component
public final class PublishPhysicalPaymentCompletedUseCase {

    private static final Logger log = LoggerFactory.getLogger(PublishPhysicalPaymentCompletedUseCase.class);

    private final BillingOutboxAppenderPort outboxAppender;
    private final ObjectWriter writer;

    public PublishPhysicalPaymentCompletedUseCase(BillingOutboxAppenderPort outboxAppender) {
        this(outboxAppender, new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .registerModule(new ParameterNamesModule())
            .configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
            .writerFor(PaymentCompletedOutboxPayload.class));
    }

    PublishPhysicalPaymentCompletedUseCase(BillingOutboxAppenderPort outboxAppender, ObjectWriter writer) {
        this.outboxAppender = outboxAppender;
        this.writer = writer;
    }

    /**
     * Append the event in the caller's active payment transaction for a
     * Completed physical payment; no-op for any other status or non-Physical target so the caller
     * (which already gates by status) can safely call this from any branch.
     *
     * @param payment the payment currently being committed.
     */
    public void handle(Payment payment) {
        if (!(payment.getTarget() instanceof PaymentTarget.Physical)) {
            return;
        }
        if (!(payment.getStatus() instanceof PaymentStatus.Completed)) {
            return;
        }
        String aggregateId = payment.getId().getValue().toString();
        if (outboxAppender.existsForAggregateAndEventType(
            BillingOutboxEventTypes.PHYSICAL_PAYMENT_COMPLETED, aggregateId
        )) {
            log.info(
                "Redelivered billing.PhysicalPaymentCompleted for paymentId={}; already published, no-op",
                payment.getId().getValue()
            );
            return;
        }
        try {
            outboxAppender.append(
                BillingOutboxEventTypes.PHYSICAL_PAYMENT_COMPLETED,
                aggregateId,
                writeJson(toPayload(payment))
            );
        } catch (DataIntegrityViolationException alreadyPublished) {
            // Race backstop: two redeliveries slipped past the check above
            // concurrently. See this class's Idempotency Javadoc — the
            // ambient transaction still rolls back regardless of this catch;
            // it exists only so nothing propagates unhandled from here.
            log.info(
                "Redelivered billing.PhysicalPaymentCompleted for paymentId={}; lost a concurrent race, no-op",
                payment.getId().getValue()
            );
        }
    }

    private static PaymentCompletedOutboxPayload toPayload(Payment payment) {
        String quoteId = ((PaymentTarget.Physical) payment.getTarget()).quoteId();
        return new PaymentCompletedOutboxPayload(
            payment.getId().getValue(),
            payment.getProviderPaymentId()
                .orElseThrow(() -> new IllegalStateException(
                    "Completed payment without providerPaymentId cannot be published; paymentId=" + payment.getId()
                )),
            payment.getExpectedExternalReference(),
            payment.getExpectedMerchantAccountId(),
            quoteId,
            payment.getExpectedAmount().getAmount(),
            payment.getExpectedAmount().getCurrency(),
            ((PaymentStatus.Completed) payment.getStatus()).confirmedAt()
        );
    }

    private String writeJson(PaymentCompletedOutboxPayload payload) {
        try {
            return writer.writeValueAsString(payload);
        } catch (JsonProcessingException impossible) {
            throw new IllegalStateException(
                "PaymentCompletedOutboxPayload JSON serialization failed", impossible
            );
        }
    }
}
