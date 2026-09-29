package com.menta.billing.infrastructure.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.menta.billing.application.dto.PaymentDecisionNotification;
import com.menta.shared.auth.UserContactPort;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * #33, US-BILLING-005, design D5/D8/C8/C9: two Spanish templates, address resolved through {@link
 * UserContactPort}, and the {@code afterCommit} deferral that makes D8's "buyer email failure
 * never rolls back the decision" hold in code. There is no existing {@code afterCommit} precedent
 * anywhere in {@code api/} (design C9) so every branch of {@link
 * org.springframework.transaction.support.TransactionSynchronizationManager} usage is covered
 * here directly, without a Spring test context.
 */
@ExtendWith(MockitoExtension.class)
class SpringMailPaymentDecisionNotificationAdapterTest {

    private static final UUID PAYMENT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID BUYER_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");

    @Mock private JavaMailSender mailSender;
    @Mock private UserContactPort userContactPort;

    private SpringMailPaymentDecisionNotificationAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new SpringMailPaymentDecisionNotificationAdapter(mailSender, userContactPort, "no-reply@menta.local");
    }

    @AfterEach
    void clearSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    private static PaymentDecisionNotification approvedNotification() {
        return new PaymentDecisionNotification(
            PAYMENT_ID, BUYER_ID, "plan-1", BigDecimal.TEN, "ARS", null
        );
    }

    private static PaymentDecisionNotification rejectedNotification(String reason) {
        return new PaymentDecisionNotification(
            PAYMENT_ID, BUYER_ID, "plan-1", BigDecimal.TEN, "ARS", reason
        );
    }

    @Test
    void notifyApproved_sends_the_spanish_confirmation_template_outside_a_transaction() {
        when(userContactPort.emailOf(BUYER_ID)).thenReturn(Optional.of("buyer@menta.local"));

        adapter.notifyApproved(approvedNotification());

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender, times(1)).send(captor.capture());
        SimpleMailMessage message = captor.getValue();
        assertThat(message.getTo()).containsExactly("buyer@menta.local");
        assertThat(message.getFrom()).isEqualTo("no-reply@menta.local");
        assertThat(message.getSubject()).containsIgnoringCase("aprobado");
        assertThat(message.getText()).contains(PAYMENT_ID.toString());
    }

    @Test
    void notifyRejected_sends_the_rejection_template_with_the_reason_interpolated_verbatim() {
        when(userContactPort.emailOf(BUYER_ID)).thenReturn(Optional.of("buyer@menta.local"));

        adapter.notifyRejected(rejectedNotification("Comprobante ilegible"));

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender, times(1)).send(captor.capture());
        SimpleMailMessage message = captor.getValue();
        assertThat(message.getSubject()).containsIgnoringCase("rechazado");
        assertThat(message.getText()).contains("Comprobante ilegible");
    }

    @Test
    void both_methods_resolve_the_address_through_userContactPort() {
        when(userContactPort.emailOf(BUYER_ID)).thenReturn(Optional.of("buyer@menta.local"));

        adapter.notifyApproved(approvedNotification());

        verify(userContactPort, times(1)).emailOf(BUYER_ID);
    }

    @Test
    void a_missing_address_is_handled_without_throwing_and_sends_nothing() {
        when(userContactPort.emailOf(BUYER_ID)).thenReturn(Optional.empty());

        assertThatCode(() -> adapter.notifyApproved(approvedNotification())).doesNotThrowAnyException();

        verify(mailSender, never()).send(any(SimpleMailMessage.class));
    }

    @Test
    void outside_an_active_transaction_the_email_sends_inline() {
        when(userContactPort.emailOf(BUYER_ID)).thenReturn(Optional.of("buyer@menta.local"));

        adapter.notifyApproved(approvedNotification());

        verify(mailSender, times(1)).send(any(SimpleMailMessage.class));
    }

    @Test
    void afterCommit_fires_only_after_a_real_commit_never_on_rollback() {
        TransactionSynchronizationManager.initSynchronization();

        adapter.notifyApproved(approvedNotification());
        verify(mailSender, never()).send(any(SimpleMailMessage.class));

        TransactionSynchronizationManager.getSynchronizations()
            .forEach(org.springframework.transaction.support.TransactionSynchronization::beforeCompletion);
        // Simulate a rollback: afterCompletion(STATUS_ROLLED_BACK) never triggers afterCommit.
        TransactionSynchronizationManager.getSynchronizations()
            .forEach(sync -> sync.afterCompletion(
                org.springframework.transaction.support.TransactionSynchronization.STATUS_ROLLED_BACK
            ));

        verify(mailSender, never()).send(any(SimpleMailMessage.class));
    }

    @Test
    void afterCommit_sends_after_a_real_commit() {
        when(userContactPort.emailOf(BUYER_ID)).thenReturn(Optional.of("buyer@menta.local"));
        TransactionSynchronizationManager.initSynchronization();

        adapter.notifyApproved(approvedNotification());
        verify(mailSender, never()).send(any(SimpleMailMessage.class));

        TransactionSynchronizationManager.getSynchronizations()
            .forEach(org.springframework.transaction.support.TransactionSynchronization::afterCommit);

        verify(mailSender, times(1)).send(any(SimpleMailMessage.class));
    }

    /**
     * D8/C9: the {@code try/catch} inside {@code afterCommit} is load-bearing — Spring propagates
     * an exception thrown from {@code afterCommit} back to the caller of {@code commit()}. Without
     * it, a dead SMTP host surfaces as a {@code 500} on an approval that already committed, exactly
     * what D8 forbids.
     */
    @Test
    void a_runtimeException_during_send_is_caught_and_logged_never_rethrown() {
        when(userContactPort.emailOf(BUYER_ID)).thenReturn(Optional.of("buyer@menta.local"));
        doThrow(new org.springframework.mail.MailSendException("smtp down"))
            .when(mailSender).send(any(SimpleMailMessage.class));
        TransactionSynchronizationManager.initSynchronization();

        adapter.notifyApproved(approvedNotification());

        assertThatCode(() -> TransactionSynchronizationManager.getSynchronizations()
            .forEach(org.springframework.transaction.support.TransactionSynchronization::afterCommit))
            .doesNotThrowAnyException();
    }

    @Test
    void a_runtimeException_during_an_inline_send_outside_a_transaction_is_also_caught() {
        when(userContactPort.emailOf(BUYER_ID)).thenReturn(Optional.of("buyer@menta.local"));
        doThrow(new org.springframework.mail.MailSendException("smtp down"))
            .when(mailSender).send(any(SimpleMailMessage.class));

        assertThatCode(() -> adapter.notifyApproved(approvedNotification())).doesNotThrowAnyException();
    }

    @Test
    void constructor_rejects_a_blank_from_address() {
        assertThatCode(() -> new SpringMailPaymentDecisionNotificationAdapter(mailSender, userContactPort, " "))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
