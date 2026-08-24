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
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Producer for the {@code billing.PhysicalPaymentCompleted} outbox event
 * (proposal §5 Approach — "Event production"; design §5.1 — after-commit
 * semantics; tasks TASK-003).
 *
 * <h2>Lifecycle</h2>
 * <p>Called from {@link PaymentVerificationService#ensureFulfillment}
 * inside the payment-status commit. The use case does NOT persist the
 * outbox row synchronously: it registers a
 * {@link TransactionSynchronization#afterCommit()} hook on the caller's
 * transaction, so the row only exists if and when the payment transaction
 * itself commits. On rollback, Spring drops the registered synchronization
 * and the appender is never called — the spec scenario "Rolled-back payment
 * leaves empty outbox and empty purchases" is preserved mechanically.</p>
 *
 * <h2>Scope</h2>
 * <p>Only fires for {@code PaymentTarget.Physical} that has reached
 * {@link PaymentStatus.Completed}: Virtual fulfillment already lives in
 * {@code ensureSubscription} (this class never participates in the Virtual
 * path). A non-Completed or non-Physical payment is a silent no-op at this
 * layer.</p>
 *
 * <h2>Idempotency</h2>
 * <p>Two layers, both DB-enforced (design §5.1, R6):
 * <ul>
 *   <li>V8 {@code uq_billing_purchases_payment_id} — same payment row rejected on second handler delivery.</li>
 *   <li>V2 {@code idx_common_outbox_aggregate_event_type} — second publisher call for the same
 *       ({@code paymentId}, {@code BillingOutboxEventTypes.PHYSICAL_PAYMENT_COMPLETED}) pair
 *       raises {@code DataIntegrityViolationException} which propagates unchanged so the
 *       caller / reconciler decides what to do.</li>
 * </ul></p>
 */
@Component
public final class PublishPhysicalPaymentCompletedUseCase {

    private final BillingOutboxAppenderPort outboxAppender;
    private final ObjectWriter writer;

    public PublishPhysicalPaymentCompletedUseCase(BillingOutboxAppenderPort outboxAppender) {
        this.outboxAppender = outboxAppender;
        this.writer = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .registerModule(new ParameterNamesModule())
            .configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
            .writerFor(PaymentCompletedOutboxPayload.class);
    }

    /**
     * Register the after-commit appender for a Completed physical payment;
     * no-op for any other status or non-Physical target so the caller
     * (which already gates by status) can safely call this from any branch.
     *
     * @param payment the payment currently being committed.
     */
    public void handle(Payment payment) {
        if (!(payment.getTarget() instanceof PaymentTarget)) {
            return;
        }
        if (!(payment.getTarget() instanceof PaymentTarget.Physical)) {
            return;
        }
        if (!(payment.getStatus() instanceof PaymentStatus.Completed)) {
            return;
        }
        PaymentCompletedOutboxPayload payload = toPayload(payment);
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                outboxAppender.append(
                    BillingOutboxEventTypes.PHYSICAL_PAYMENT_COMPLETED,
                    payment.getId().getValue().toString(),
                    writeJson(payload)
                );
            }
        });
    }

    private static PaymentCompletedOutboxPayload toPayload(Payment payment) {
        return new PaymentCompletedOutboxPayload(
            payment.getId().getValue(),
            payment.getProviderPaymentId()
                .orElseThrow(() -> new IllegalStateException(
                    "Completed payment without providerPaymentId cannot be published; paymentId=" + payment.getId()
                )),
            payment.getExpectedExternalReference(),
            payment.getExpectedMerchantAccountId(),
            ((PaymentTarget.Physical) payment.getTarget()).sessionId(),
            payment.getExpectedAmount().getAmount(),
            payment.getExpectedAmount().getCurrency(),
            payment.confirmedAt().orElseThrow(() -> new IllegalStateException(
                "Completed payment without confirmedAt cannot be published; paymentId=" + payment.getId()
            ))
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
