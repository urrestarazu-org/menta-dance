package com.menta.billing.application.usecase;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.module.paramnames.ParameterNamesModule;
import com.menta.billing.application.contract.BillingOutboxEventTypes;
import com.menta.billing.application.port.in.MarkPurchaseExceptionPort;
import com.menta.billing.application.port.out.BillingOutboxAppenderPort;
import com.menta.billing.application.port.out.Clock;
import com.menta.billing.application.port.out.PaymentRepository;
import com.menta.billing.application.port.out.PurchaseRepository;
import com.menta.billing.domain.exception.IllegalPurchaseStateTransitionException;
import com.menta.billing.domain.exception.PaymentNotFoundException;
import com.menta.billing.domain.model.FulfillmentStatus;
import com.menta.billing.domain.model.Payment;
import com.menta.billing.domain.model.PaymentId;
import com.menta.billing.domain.model.Purchase;
import com.menta.billing.domain.model.Reason;
import com.menta.shared.billing.PurchaseExceptionedOutboxPayload;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * State-machine guard for the residual {@code EXCEPTION} terminal state
 * (proposal §4; design §4.2):
 *
 * <ul>
 *   <li>{@code PENDING_FULFILLMENT} → {@code EXCEPTION} — accepted, the
 *       spec scenario "Capacity invariant trips — Purchase flips to
 *       EXCEPTION". Also appends {@code billing.PurchaseExceptioned}
 *       (proposal D1) after the save, inside this same {@code REQUIRED}
 *       transaction, exactly as {@link PublishPhysicalPaymentCompletedUseCase}
 *       appends {@code billing.PhysicalPaymentCompleted}.</li>
 *   <li>{@code EXCEPTION} → {@code EXCEPTION} — idempotent no-op (concurrent
 *       retry of the same handler call); no append (design D5).</li>
 *   <li>{@code ASSIGNED} → {@code EXCEPTION} — refused; throws
 *       {@link IllegalPurchaseStateTransitionException}. Per ADR-0028
 *       §Decisión, once assigned, the residual path is no longer reachable;
 *       no append.</li>
 * </ul>
 *
 * <p>The {@link Reason} parameter is recorded via logs and now also carried
 * in the outbox payload as its enum name — the {@code EXCEPTION} status
 * itself still doesn't carry a reason field on {@code billing_purchases}
 * (design D6 — no migration).</p>
 */
@Component
public class MarkPurchaseExceptionUseCase implements MarkPurchaseExceptionPort {

    private final PurchaseRepository purchaseRepository;
    private final PaymentRepository paymentRepository;
    private final BillingOutboxAppenderPort outboxAppender;
    private final Clock clock;
    private final ObjectWriter writer;

    public MarkPurchaseExceptionUseCase(
        PurchaseRepository purchaseRepository, PaymentRepository paymentRepository,
        BillingOutboxAppenderPort outboxAppender, Clock clock
    ) {
        this(purchaseRepository, paymentRepository, outboxAppender, clock, new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .registerModule(new ParameterNamesModule())
            .configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
            .writerFor(PurchaseExceptionedOutboxPayload.class));
    }

    MarkPurchaseExceptionUseCase(
        PurchaseRepository purchaseRepository, PaymentRepository paymentRepository,
        BillingOutboxAppenderPort outboxAppender, Clock clock, ObjectWriter writer
    ) {
        this.purchaseRepository = purchaseRepository;
        this.paymentRepository = paymentRepository;
        this.outboxAppender = outboxAppender;
        this.clock = clock;
        this.writer = writer;
    }

    /**
     * {@code noRollbackFor} (#41 PR8): a refused {@code ASSIGNED -> EXCEPTION}
     * transition is the documented, correct terminal outcome of a redelivered
     * outbox event (ADR-0028 §Decisión) — not a failure that should poison
     * the ambient transaction. Without this, {@link IllegalPurchaseStateTransitionException}
     * propagating out of this {@code REQUIRED}-participating method marks the
     * caller's transaction rollback-only even when the caller catches and
     * swallows it, so a later commit throws {@code UnexpectedRollbackException}
     * for a case that was already handled. This changes only Spring's
     * transactional bookkeeping — the exception itself is still thrown
     * unchanged for every caller.
     */
    @Override
    @Transactional(propagation = Propagation.REQUIRED, noRollbackFor = IllegalPurchaseStateTransitionException.class)
    public void markException(PaymentId paymentId, Reason reason) {
        Optional<Purchase> maybe = purchaseRepository.findByPaymentIdForUpdate(paymentId);
        if (maybe.isEmpty()) {
            throw new PaymentNotFoundException(paymentId);
        }
        Purchase purchase = maybe.get();
        if (purchase.getStatus() == FulfillmentStatus.ASSIGNED) {
            throw new IllegalPurchaseStateTransitionException(
                paymentId, FulfillmentStatus.ASSIGNED, FulfillmentStatus.EXCEPTION
            );
        }
        if (purchase.getStatus() == FulfillmentStatus.EXCEPTION) {
            return;
        }
        Purchase saved = purchaseRepository.save(purchase.exception());
        outboxAppender.append(
            BillingOutboxEventTypes.PURCHASE_EXCEPTIONED,
            paymentId.getValue().toString(),
            writeJson(toPayload(paymentId, saved, reason))
        );
    }

    private PurchaseExceptionedOutboxPayload toPayload(PaymentId paymentId, Purchase purchase, Reason reason) {
        UUID userId = paymentRepository.findById(paymentId)
            .map(Payment::getUserId)
            .orElse(null);
        return new PurchaseExceptionedOutboxPayload(
            paymentId.getValue(), purchase.getId(), userId, reason.name(), clock.now()
        );
    }

    private String writeJson(PurchaseExceptionedOutboxPayload payload) {
        try {
            return writer.writeValueAsString(payload);
        } catch (JsonProcessingException impossible) {
            throw new IllegalStateException(
                "PurchaseExceptionedOutboxPayload JSON serialization failed", impossible
            );
        }
    }
}
