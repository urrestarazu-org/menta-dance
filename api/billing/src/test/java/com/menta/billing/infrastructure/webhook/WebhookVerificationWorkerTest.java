package com.menta.billing.infrastructure.webhook;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.menta.billing.application.port.out.PaymentRepository;
import com.menta.billing.application.usecase.PaymentVerificationService;
import com.menta.billing.application.usecase.VerificationOutcome;
import com.menta.billing.domain.model.Money;
import com.menta.billing.domain.model.Payment;
import com.menta.billing.domain.model.PaymentId;
import com.menta.billing.domain.model.PaymentStatus;
import com.menta.billing.domain.model.PaymentTarget;
import com.menta.billing.infrastructure.persistence.entity.ReconciliationTaskJpaEntity;
import com.menta.billing.infrastructure.persistence.entity.WebhookInboxJpaEntity;
import com.menta.billing.infrastructure.persistence.repository.ReconciliationTaskJpaRepository;
import com.menta.billing.infrastructure.persistence.repository.WebhookInboxJpaRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class WebhookVerificationWorkerTest {

    private static final Instant NOW = Instant.parse("2026-08-18T12:00:00Z");

    private WebhookInboxJpaRepository inboxRepository;
    private ReconciliationTaskJpaRepository reconciliationTaskRepository;
    private PaymentRepository paymentRepository;
    private PaymentVerificationService verificationService;
    private WebhookVerificationWorker worker;

    @BeforeEach
    void setUp() {
        inboxRepository = mock(WebhookInboxJpaRepository.class);
        reconciliationTaskRepository = mock(ReconciliationTaskJpaRepository.class);
        paymentRepository = mock(PaymentRepository.class);
        verificationService = mock(PaymentVerificationService.class);
        worker = new WebhookVerificationWorker(
            inboxRepository, reconciliationTaskRepository, paymentRepository, verificationService, 30, 2
        );
    }

    private static WebhookInboxJpaEntity row(int attemptCount) {
        return new WebhookInboxJpaEntity(
            "data-1:req-1", "mp-1", "req-1", WebhookInboxStatus.RECEIVED, attemptCount, null, null, NOW, null
        );
    }

    private static Payment payment(PaymentStatus status) {
        return new Payment(
            PaymentId.generate(), "mp-1", Money.of(BigDecimal.TEN, "ARS"), "ext-1", "merchant-1",
            new PaymentTarget.Physical("session-1"), status, NOW
        );
    }

    @Test
    void marks_processed_when_verification_applies_cleanly() {
        WebhookInboxJpaEntity row = row(0);
        when(verificationService.verify("mp-1"))
            .thenReturn(new VerificationOutcome.Applied(payment(new PaymentStatus.Completed(NOW))));

        worker.process(row);

        assertThat(row.getStatus()).isEqualTo(WebhookInboxStatus.PROCESSED);
        assertThat(row.getProcessedAt()).isNotNull();
        verify(reconciliationTaskRepository, never()).save(any());
    }

    @Test
    void creates_a_reconciliation_task_when_verification_applies_a_mismatch() {
        WebhookInboxJpaEntity row = row(0);
        Payment mismatched = payment(new PaymentStatus.ReconciliationRequired("mismatch"));
        when(verificationService.verify("mp-1")).thenReturn(new VerificationOutcome.Applied(mismatched));

        worker.process(row);

        assertThat(row.getStatus()).isEqualTo(WebhookInboxStatus.PROCESSED);
        ArgumentCaptor<ReconciliationTaskJpaEntity> captor = ArgumentCaptor.forClass(ReconciliationTaskJpaEntity.class);
        verify(reconciliationTaskRepository).save(captor.capture());
        assertThat(captor.getValue().getPaymentId()).isEqualTo(mismatched.getId().getValue());
        assertThat(captor.getValue().getReason()).isEqualTo("mismatch");
    }

    @Test
    void creates_a_reconciliation_task_referencing_only_the_event_when_no_local_payment_exists() {
        WebhookInboxJpaEntity row = row(0);
        when(verificationService.verify("mp-1")).thenReturn(new VerificationOutcome.NoLocalPayment());

        worker.process(row);

        assertThat(row.getStatus()).isEqualTo(WebhookInboxStatus.RECONCILIATION_REQUIRED);
        ArgumentCaptor<ReconciliationTaskJpaEntity> captor = ArgumentCaptor.forClass(ReconciliationTaskJpaEntity.class);
        verify(reconciliationTaskRepository).save(captor.capture());
        assertThat(captor.getValue().getPaymentId()).isNull();
        assertThat(captor.getValue().getProviderPaymentId()).isEqualTo("mp-1");
    }

    @Test
    void retries_with_backoff_when_provider_lookup_fails_and_attempts_remain() {
        WebhookInboxJpaEntity row = row(0);
        when(verificationService.verify("mp-1")).thenThrow(new IllegalStateException("timeout"));

        worker.process(row);

        assertThat(row.getStatus()).isEqualTo(WebhookInboxStatus.RETRY_PENDING);
        assertThat(row.getAttemptCount()).isEqualTo(1);
        assertThat(row.getNextAttemptAt()).isAfter(NOW);
        assertThat(row.getLastError()).isEqualTo("timeout");
        verify(reconciliationTaskRepository, never()).save(any());
    }

    @Test
    void exhausting_retries_marks_reconciliation_required_and_creates_a_task_referencing_the_payment() {
        WebhookInboxJpaEntity row = row(1);
        when(verificationService.verify("mp-1")).thenThrow(new IllegalStateException("timeout"));
        Payment pending = payment(new PaymentStatus.AwaitingProvider());
        when(paymentRepository.findByProviderPaymentId("mp-1")).thenReturn(Optional.of(pending));
        when(paymentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        worker.process(row);

        assertThat(row.getStatus()).isEqualTo(WebhookInboxStatus.RECONCILIATION_REQUIRED);
        assertThat(row.getAttemptCount()).isEqualTo(2);
        ArgumentCaptor<Payment> paymentCaptor = ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository).save(paymentCaptor.capture());
        assertThat(paymentCaptor.getValue().getStatus()).isInstanceOf(PaymentStatus.ReconciliationRequired.class);
        ArgumentCaptor<ReconciliationTaskJpaEntity> taskCaptor = ArgumentCaptor.forClass(ReconciliationTaskJpaEntity.class);
        verify(reconciliationTaskRepository).save(taskCaptor.capture());
        assertThat(taskCaptor.getValue().getPaymentId()).isEqualTo(pending.getId().getValue());
    }

    @Test
    void exhausting_retries_with_no_local_payment_creates_a_task_referencing_only_the_event() {
        WebhookInboxJpaEntity row = row(1);
        when(verificationService.verify("mp-1")).thenThrow(new IllegalStateException("timeout"));
        when(paymentRepository.findByProviderPaymentId("mp-1")).thenReturn(Optional.empty());

        worker.process(row);

        verify(paymentRepository, never()).save(any());
        ArgumentCaptor<ReconciliationTaskJpaEntity> taskCaptor = ArgumentCaptor.forClass(ReconciliationTaskJpaEntity.class);
        verify(reconciliationTaskRepository).save(taskCaptor.capture());
        assertThat(taskCaptor.getValue().getPaymentId()).isNull();
    }
}
