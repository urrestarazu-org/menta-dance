package com.menta.app.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.menta.app.billing.MarkPurchaseExceptionAdapter;
import com.menta.app.billing.PhysicalCapacityAssignmentAdapter;
import com.menta.auth.infrastructure.persistence.entity.OutboxRowJpaEntity;
import com.menta.billing.application.contract.BillingOutboxEventTypes;
import com.menta.billing.application.port.in.PurchaseCreationFromEventPort;
import com.menta.billing.application.port.out.PaymentRepository;
import com.menta.billing.domain.model.Payment;
import com.menta.billing.domain.model.PaymentTarget;
import com.menta.billing.domain.model.Reason;
import com.menta.shared.billing.PaymentCompletedOutboxPayload;
import com.menta.shared.physical.CapacityAssignmentCommand;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Spring-discovered handler (proposal §4 handler; design §6 reconciler
 * integration). Resolves {@code billing.PhysicalPaymentCompleted} events:
 * upserts a {@code Purchase(PENDING_FULFILLMENT)}, assigns capacity via
 * Physical's IN port, and routes any {@link
 * com.menta.physical.domain.exception.CapacityBelowAssignedException} to
 * {@link MarkPurchaseExceptionAdapter} (the EXCEPTION residual).
 *
 * <p>Unexpected exceptions propagate so the worker keeps the
 * {@code FAILED/backoff} lifecycle per design §9 R9.</p>
 *
 * <h2>studentId resolution</h2>
 * <p>{@code CapacityAssignmentCommand} requires a {@code studentId}; the
 * payload only carries {@code paymentId, targetReference}. We load the
 * underlying {@link Payment} via {@link PaymentRepository} and reuse its
 * {@link Payment#userId} as the student id. If the payment row is missing
 * (e.g. deletion by an administrator before reconciliation), we route
 * through {@link MarkPurchaseExceptionAdapter} with
 * {@link Reason#TARGET_NOT_SCHEDULED} — a conservative terminal
 * classification rather than a silent dead-letter.</p>
 */
@Component
public class PhysicalCapacityAssignmentOutboxEventHandler implements OutboxEventHandler {

    private static final Logger log = LoggerFactory.getLogger(
        PhysicalCapacityAssignmentOutboxEventHandler.class
    );

    private final PhysicalCapacityAssignmentAdapter capacityAdapter;
    private final MarkPurchaseExceptionAdapter exceptionAdapter;
    private final PurchaseCreationFromEventPort purchaseCreationFromEventPort;
    private final PaymentRepository paymentRepository;
    private final ObjectMapper objectMapper;

    public PhysicalCapacityAssignmentOutboxEventHandler(
        PhysicalCapacityAssignmentAdapter capacityAdapter,
        MarkPurchaseExceptionAdapter exceptionAdapter,
        PurchaseCreationFromEventPort purchaseCreationFromEventPort,
        PaymentRepository paymentRepository,
        ObjectMapper objectMapper
    ) {
        this.capacityAdapter = capacityAdapter;
        this.exceptionAdapter = exceptionAdapter;
        this.purchaseCreationFromEventPort = purchaseCreationFromEventPort;
        this.paymentRepository = paymentRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean supports(String eventType) {
        return BillingOutboxEventTypes.PHYSICAL_PAYMENT_COMPLETED.equals(eventType);
    }

    @Override
    public void handle(OutboxRowJpaEntity row) {
        PaymentCompletedOutboxPayload payload = parse(row);
        com.menta.billing.domain.model.PaymentId paymentId = com.menta.billing.domain.model.PaymentId.of(
            payload.paymentId()
        );

        // Resolve the buyer from the payment row BEFORE upserting the
        // Purchase: a missing target or non-Physical target means the
        // hold already expired / coverage was rewritten — route directly
        // to EXCEPTION and skip the upsert so we don't write a
        // PENDING_FULFILLMENT row that has to be re-read on every retry.
        Payment payment = paymentRepository.findById(paymentId).orElse(null);
        if (payment == null || !(payment.getTarget() instanceof PaymentTarget.Physical physical)) {
            log.warn(
                "Payment row absent or non-Physical for outbox event paymentId={}; routing to EXCEPTION",
                payload.paymentId()
            );
            exceptionAdapter.markException(paymentId, Reason.TARGET_NOT_SCHEDULED);
            return;
        }

        purchaseCreationFromEventPort.createPurchaseFromPaymentEvent(payload);

        CapacityAssignmentCommand cmd = new CapacityAssignmentCommand(
            UUID.fromString(physical.sessionId()),
            payment.getUserId(),
            payload.paymentId()
        );

        try {
            capacityAdapter.assign(cmd);
        } catch (com.menta.physical.domain.exception.CapacityBelowAssignedException capacityTripped) {
            // Spec scenario: capacity invariant trips — Purchase flips to EXCEPTION.
            exceptionAdapter.markException(paymentId, Reason.CAPACITY_BELOW_ASSIGNED);
            // V7 UNIQUE race on (session_id, student_id) also rolls up here
            // — the adapter rethrows CapacityBelowAssignedException on a V7
            // UNIQUE collision.
        }
    }

    private PaymentCompletedOutboxPayload parse(OutboxRowJpaEntity row) {
        try {
            return objectMapper.readValue(row.getPayload(), PaymentCompletedOutboxPayload.class);
        } catch (Exception e) {
            throw new IllegalStateException(
                "Failed to deserialize billing.PhysicalPaymentCompleted payload", e
            );
        }
    }
}
