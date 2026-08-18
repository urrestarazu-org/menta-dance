package com.menta.billing.infrastructure.webhook;

import com.menta.billing.application.port.out.PaymentRepository;
import com.menta.billing.application.usecase.PaymentVerificationService;
import com.menta.billing.application.usecase.VerificationOutcome;
import com.menta.billing.domain.model.Payment;
import com.menta.billing.domain.model.PaymentStatus;
import com.menta.billing.infrastructure.persistence.entity.ReconciliationTaskJpaEntity;
import com.menta.billing.infrastructure.persistence.entity.WebhookInboxJpaEntity;
import com.menta.billing.infrastructure.persistence.repository.ReconciliationTaskJpaRepository;
import com.menta.billing.infrastructure.persistence.repository.WebhookInboxJpaRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Processes one eligible {@code billing_webhook_inbox} row in an
 * independent transaction — mirrors {@code OutboxReconciliationWorker}'s
 * shape exactly (ADR-0038): a row that fails does not abort the batch, and
 * retries never let a duplicate/late webhook double-apply an effect (see
 * {@link PaymentVerificationService}).
 */
@Component
public class WebhookVerificationWorker {

    private static final Logger log = LoggerFactory.getLogger(WebhookVerificationWorker.class);

    private final WebhookInboxJpaRepository inboxRepository;
    private final ReconciliationTaskJpaRepository reconciliationTaskRepository;
    private final PaymentRepository paymentRepository;
    private final PaymentVerificationService verificationService;
    private final Duration backoff;
    private final int maxAttempts;

    public WebhookVerificationWorker(
        WebhookInboxJpaRepository inboxRepository,
        ReconciliationTaskJpaRepository reconciliationTaskRepository,
        PaymentRepository paymentRepository,
        PaymentVerificationService verificationService,
        @Value("${billing.webhook.retry-backoff-seconds:30}") long backoffSeconds,
        @Value("${billing.webhook.max-attempts:5}") int maxAttempts
    ) {
        this.inboxRepository = inboxRepository;
        this.reconciliationTaskRepository = reconciliationTaskRepository;
        this.paymentRepository = paymentRepository;
        this.verificationService = verificationService;
        this.backoff = Duration.ofSeconds(backoffSeconds);
        this.maxAttempts = maxAttempts;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void process(WebhookInboxJpaEntity row) {
        try {
            handleOutcome(row, verificationService.verify(row.getProviderPaymentId()));
        } catch (RuntimeException providerLookupFailed) {
            handleFailure(row, providerLookupFailed);
        }
    }

    private void handleOutcome(WebhookInboxJpaEntity row, VerificationOutcome outcome) {
        switch (outcome) {
            case VerificationOutcome.Applied applied -> {
                if (applied.payment().getStatus() instanceof PaymentStatus.ReconciliationRequired reconciliation) {
                    createReconciliationTask(
                        applied.payment().getId().getValue(), row.getProviderPaymentId(), reconciliation.reason()
                    );
                }
                markProcessed(row);
            }
            case VerificationOutcome.NoLocalPayment noLocalPayment -> {
                log.warn("No local Payment for providerPaymentId={} — creating reconciliation task", row.getProviderPaymentId());
                createReconciliationTask(null, row.getProviderPaymentId(), "no local Payment found for providerPaymentId");
                markReconciliationRequired(row);
            }
        }
    }

    private void handleFailure(WebhookInboxJpaEntity row, RuntimeException failure) {
        int attempts = row.getAttemptCount() + 1;
        row.setAttemptCount(attempts);
        row.setLastError(truncate(failure.getMessage(), 1000));

        if (attempts < maxAttempts) {
            row.setStatus(WebhookInboxStatus.RETRY_PENDING);
            row.setNextAttemptAt(Instant.now().plus(backoff));
            inboxRepository.save(row);
            log.warn(
                "Webhook verification retry-pending providerPaymentId={} attempts={} cause={}",
                row.getProviderPaymentId(), attempts, failure.getMessage()
            );
            return;
        }

        row.setStatus(WebhookInboxStatus.RECONCILIATION_REQUIRED);
        row.setNextAttemptAt(null);
        inboxRepository.save(row);

        Optional<Payment> maybePayment = paymentRepository.findByProviderPaymentId(row.getProviderPaymentId());
        UUID paymentIdForTask = null;
        if (maybePayment.isPresent()) {
            Payment reconciling = maybePayment.get()
                .markReconciliationRequired("provider lookup exhausted retries: " + failure.getMessage());
            paymentIdForTask = paymentRepository.save(reconciling).getId().getValue();
        }
        createReconciliationTask(
            paymentIdForTask, row.getProviderPaymentId(),
            "provider lookup exhausted " + attempts + " attempts: " + failure.getMessage()
        );
        log.warn(
            "Webhook verification exhausted retries providerPaymentId={} attempts={}",
            row.getProviderPaymentId(), attempts
        );
    }

    private void markProcessed(WebhookInboxJpaEntity row) {
        row.setStatus(WebhookInboxStatus.PROCESSED);
        row.setProcessedAt(Instant.now());
        row.setLastError(null);
        row.setNextAttemptAt(null);
        inboxRepository.save(row);
    }

    private void markReconciliationRequired(WebhookInboxJpaEntity row) {
        row.setStatus(WebhookInboxStatus.RECONCILIATION_REQUIRED);
        row.setNextAttemptAt(null);
        inboxRepository.save(row);
    }

    private void createReconciliationTask(UUID paymentId, String providerPaymentId, String reason) {
        reconciliationTaskRepository.save(new ReconciliationTaskJpaEntity(
            UUID.randomUUID(), paymentId, providerPaymentId, reason, Instant.now()
        ));
    }

    private static String truncate(String message, int max) {
        if (message == null) {
            return null;
        }
        return message.length() <= max ? message : message.substring(0, max);
    }
}
