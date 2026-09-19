package com.menta.billing.infrastructure.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.menta.billing.application.dto.PaymentProofNotification;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

@ExtendWith(MockitoExtension.class)
class SpringMailPaymentProofNotificationAdapterTest {

    private static final UUID PAYMENT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID USER_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final Instant OCCURRED_AT = Instant.parse("2026-09-19T12:00:00Z");

    @Mock private JavaMailSender mailSender;

    private SpringMailPaymentProofNotificationAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new SpringMailPaymentProofNotificationAdapter(mailSender, "ops@menta.local", "no-reply@menta.local");
    }

    @Test
    void sends_exactly_one_message_to_the_configured_ops_address() {
        PaymentProofNotification notification = new PaymentProofNotification(
            PAYMENT_ID, USER_ID, "comprobante.png", OCCURRED_AT
        );

        adapter.notifyProofSubmitted(notification);

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender, times(1)).send(captor.capture());
        SimpleMailMessage ops = captor.getValue();
        assertThat(ops.getTo()).containsExactly("ops@menta.local");
        assertThat(ops.getFrom()).isEqualTo("no-reply@menta.local");
        assertThat(ops.getText()).contains(PAYMENT_ID.toString());
        assertThat(ops.getText()).contains(USER_ID.toString());
        assertThat(ops.getText()).contains("comprobante.png");
    }

    @Test
    void propagates_mail_exceptions_unchanged_so_the_caller_fails_the_request() {
        MailSendException smtpDown = new MailSendException("smtp unavailable");
        doThrow(smtpDown).when(mailSender).send(any(SimpleMailMessage.class));
        PaymentProofNotification notification = new PaymentProofNotification(
            PAYMENT_ID, USER_ID, "comprobante.pdf", OCCURRED_AT
        );

        assertThatThrownBy(() -> adapter.notifyProofSubmitted(notification)).isSameAs(smtpDown);
    }

    @Test
    void constructor_rejects_a_blank_ops_address() {
        assertThatThrownBy(() -> new SpringMailPaymentProofNotificationAdapter(mailSender, " ", "no-reply@menta.local"))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
