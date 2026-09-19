package com.menta.billing.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.menta.billing.application.dto.PaymentProofNotification;
import com.menta.billing.application.dto.PaymentProofUpload;
import com.menta.billing.application.dto.RateLimitDecision;
import com.menta.billing.application.dto.SubmitPaymentProofCommand;
import com.menta.billing.application.port.out.BankTransferRateLimitPort;
import com.menta.billing.application.port.out.Clock;
import com.menta.billing.application.port.out.PaymentProofNotificationPort;
import com.menta.billing.application.port.out.PaymentProofRepository;
import com.menta.billing.application.port.out.PaymentProofStoragePort;
import com.menta.billing.application.port.out.PaymentRepository;
import com.menta.billing.domain.exception.BankTransferRateLimitedException;
import com.menta.billing.domain.exception.PaymentNotFoundException;
import com.menta.billing.domain.exception.PaymentProofRejectedException;
import com.menta.billing.domain.model.ManualVerificationDecision;
import com.menta.billing.domain.model.Money;
import com.menta.billing.domain.model.Payment;
import com.menta.billing.domain.model.PaymentId;
import com.menta.billing.domain.model.PaymentProof;
import com.menta.billing.domain.model.PaymentTarget;
import com.menta.billing.domain.service.PaymentProofContentValidator;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

/**
 * Design C12's exact write order, verified with Mockito {@link InOrder} (#31, US-BILLING-003).
 */
class SubmitPaymentProofUseCaseImplTest {

    private static final Instant NOW = Instant.parse("2026-09-19T12:00:00Z");
    private static final UUID USER_ID = UUID.randomUUID();
    private static final PaymentId PAYMENT_ID = PaymentId.generate();
    private static final byte[] PNG_CONTENT = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};

    private PaymentRepository paymentRepository;
    private PaymentProofRepository paymentProofRepository;
    private PaymentProofStoragePort storagePort;
    private PaymentProofNotificationPort notificationPort;
    private BankTransferRateLimitPort rateLimitPort;
    private Clock clock;
    private SubmitPaymentProofUseCaseImpl useCase;

    @BeforeEach
    void setUp() {
        paymentRepository = mock(PaymentRepository.class);
        paymentProofRepository = mock(PaymentProofRepository.class);
        storagePort = mock(PaymentProofStoragePort.class);
        notificationPort = mock(PaymentProofNotificationPort.class);
        rateLimitPort = mock(BankTransferRateLimitPort.class);
        clock = mock(Clock.class);
        when(clock.now()).thenReturn(NOW);
        when(rateLimitPort.consumeProofUpload(any())).thenReturn(RateLimitDecision.allowed());
        when(paymentProofRepository.findByPaymentId(any())).thenReturn(Optional.empty());
        when(paymentProofRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        useCase = new SubmitPaymentProofUseCaseImpl(
            paymentRepository, paymentProofRepository, storagePort, notificationPort, rateLimitPort,
            new PaymentProofContentValidator(), clock
        );
    }

    private static Payment awaitingVerificationPayment(UUID ownerId) {
        return Payment.awaitingManualVerification(
            PAYMENT_ID, ownerId, Money.of(new BigDecimal("15000.00"), "ARS"), "SUB-" + PAYMENT_ID,
            "0000003100000000000000", new PaymentTarget.Virtual("plan-1"), NOW
        );
    }

    private static SubmitPaymentProofCommand command(UUID actingUserId) {
        return new SubmitPaymentProofCommand(
            PAYMENT_ID.toString(), actingUserId,
            new PaymentProofUpload("image/png", "comprobante.png", PNG_CONTENT.length, PNG_CONTENT)
        );
    }

    /** Design C12: consume the limiter after validation, store before the DB write, delete last. */
    @Test
    void proceeds_in_the_exact_C12_order() {
        when(paymentRepository.findById(PAYMENT_ID)).thenReturn(Optional.of(awaitingVerificationPayment(USER_ID)));

        useCase.submit(command(USER_ID));

        InOrder order = inOrder(rateLimitPort, storagePort, paymentProofRepository, notificationPort);
        order.verify(rateLimitPort).consumeProofUpload(PAYMENT_ID);
        order.verify(storagePort).store(any(), any());
        order.verify(paymentProofRepository).findByPaymentId(PAYMENT_ID);
        order.verify(paymentProofRepository).save(any());
        order.verify(notificationPort).notifyProofSubmitted(any());
    }

    /** C8: anti-enumeration — never a 403, the same exception a missing payment throws. */
    @Test
    void a_non_owner_gets_payment_not_found_with_zero_writes() {
        when(paymentRepository.findById(PAYMENT_ID)).thenReturn(Optional.of(awaitingVerificationPayment(USER_ID)));
        UUID otherUser = UUID.randomUUID();

        assertThatThrownBy(() -> useCase.submit(command(otherUser))).isInstanceOf(PaymentNotFoundException.class);

        verify(rateLimitPort, never()).consumeProofUpload(any());
        verify(storagePort, never()).store(any(), any());
        verify(paymentProofRepository, never()).save(any());
        verify(notificationPort, never()).notifyProofSubmitted(any());
    }

    @Test
    void an_unknown_payment_id_is_rejected_without_any_write() {
        when(paymentRepository.findById(PAYMENT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.submit(command(USER_ID))).isInstanceOf(PaymentNotFoundException.class);

        verify(rateLimitPort, never()).consumeProofUpload(any());
        verify(storagePort, never()).store(any(), any());
        verify(paymentProofRepository, never()).save(any());
    }

    /** C12 step 2: a payment already resolved (or still awaiting a provider) refuses a proof, zero writes. */
    @Test
    void a_payment_not_awaiting_manual_verification_is_rejected_without_any_write() {
        Payment completed =
            awaitingVerificationPayment(USER_ID).resolveManually(ManualVerificationDecision.APPROVED, NOW);
        when(paymentRepository.findById(PAYMENT_ID)).thenReturn(Optional.of(completed));

        assertThatThrownBy(() -> useCase.submit(command(USER_ID))).isInstanceOf(PaymentNotFoundException.class);

        verify(rateLimitPort, never()).consumeProofUpload(any());
        verify(storagePort, never()).store(any(), any());
        verify(paymentProofRepository, never()).save(any());
    }

    /** Design C7: invalid content is rejected before the budget or the storage adapter is ever touched. */
    @Test
    void invalid_content_never_touches_the_limiter_or_storage() {
        when(paymentRepository.findById(PAYMENT_ID)).thenReturn(Optional.of(awaitingVerificationPayment(USER_ID)));
        SubmitPaymentProofCommand invalid = new SubmitPaymentProofCommand(
            PAYMENT_ID.toString(), USER_ID, new PaymentProofUpload("application/pdf", "fake.pdf", 3, new byte[]{1, 2, 3})
        );

        assertThatThrownBy(() -> useCase.submit(invalid)).isInstanceOf(PaymentProofRejectedException.class);

        verify(rateLimitPort, never()).consumeProofUpload(any());
        verify(storagePort, never()).store(any(), any());
        verify(paymentProofRepository, never()).save(any());
        verify(notificationPort, never()).notifyProofSubmitted(any());
    }

    @Test
    void an_exhausted_upload_budget_is_rejected_after_validation_but_before_any_write() {
        when(paymentRepository.findById(PAYMENT_ID)).thenReturn(Optional.of(awaitingVerificationPayment(USER_ID)));
        when(rateLimitPort.consumeProofUpload(PAYMENT_ID)).thenReturn(RateLimitDecision.limited(Duration.ofHours(1)));

        assertThatThrownBy(() -> useCase.submit(command(USER_ID)))
            .isInstanceOf(BankTransferRateLimitedException.class);

        verify(storagePort, never()).store(any(), any());
        verify(paymentProofRepository, never()).save(any());
    }

    /** Design C12 step 7: deleting the previous blob is the last mutating step, after the new row commits. */
    @Test
    void a_replacement_deletes_the_old_storage_key_only_after_the_new_row_is_saved() {
        when(paymentRepository.findById(PAYMENT_ID)).thenReturn(Optional.of(awaitingVerificationPayment(USER_ID)));
        PaymentProof existing = PaymentProof.create(PAYMENT_ID, "image/png", "old.png", 8, NOW.minusSeconds(60));
        when(paymentProofRepository.findByPaymentId(PAYMENT_ID)).thenReturn(Optional.of(existing));

        useCase.submit(command(USER_ID));

        InOrder order = inOrder(paymentProofRepository, notificationPort, storagePort);
        order.verify(paymentProofRepository).save(any());
        order.verify(notificationPort).notifyProofSubmitted(any());
        order.verify(storagePort).delete(existing.getStorageKey());
    }

    @Test
    void the_notification_carries_the_payment_owner_and_original_filename() {
        when(paymentRepository.findById(PAYMENT_ID)).thenReturn(Optional.of(awaitingVerificationPayment(USER_ID)));

        useCase.submit(command(USER_ID));

        ArgumentCaptor<PaymentProofNotification> notification =
            ArgumentCaptor.forClass(PaymentProofNotification.class);
        verify(notificationPort).notifyProofSubmitted(notification.capture());
        assertThat(notification.getValue().paymentId()).isEqualTo(PAYMENT_ID.getValue());
        assertThat(notification.getValue().userId()).isEqualTo(USER_ID);
        assertThat(notification.getValue().originalFilename()).isEqualTo("comprobante.png");
    }
}
