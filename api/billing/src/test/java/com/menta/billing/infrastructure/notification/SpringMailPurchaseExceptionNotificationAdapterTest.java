package com.menta.billing.infrastructure.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.menta.billing.application.dto.PurchaseExceptionNotification;
import com.menta.shared.auth.UserEmailLookupPort;
import java.time.Instant;
import java.util.Optional;
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
class SpringMailPurchaseExceptionNotificationAdapterTest {

    private static final UUID PAYMENT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID PURCHASE_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID USER_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final Instant OCCURRED_AT = Instant.parse("2026-09-17T12:00:00Z");

    @Mock private JavaMailSender mailSender;
    @Mock private UserEmailLookupPort userEmailLookupPort;

    private SpringMailPurchaseExceptionNotificationAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new SpringMailPurchaseExceptionNotificationAdapter(
            userEmailLookupPort, mailSender, "ops@menta.local", "no-reply@menta.local"
        );
    }

    @Test
    void sends_student_and_ops_messages_when_the_buyer_email_resolves() {
        when(userEmailLookupPort.findEmailById(USER_ID)).thenReturn(Optional.of("buyer@example.com"));
        PurchaseExceptionNotification notification = new PurchaseExceptionNotification(
            PAYMENT_ID, PURCHASE_ID, USER_ID, "CAPACITY_BELOW_ASSIGNED", OCCURRED_AT, "billing.PurchaseExceptioned"
        );

        adapter.notify(notification);

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender, times(2)).send(captor.capture());
        SimpleMailMessage student = captor.getAllValues().get(0);
        SimpleMailMessage ops = captor.getAllValues().get(1);

        assertThat(student.getTo()).containsExactly("buyer@example.com");
        assertThat(student.getText()).doesNotContain("CAPACITY_BELOW_ASSIGNED");
        assertThat(student.getText()).doesNotContain("reembolso");
        assertThat(student.getText()).doesNotContain("reintento");

        assertThat(ops.getTo()).containsExactly("ops@menta.local");
        assertThat(ops.getText()).contains("CAPACITY_BELOW_ASSIGNED");
        assertThat(ops.getText()).contains(PAYMENT_ID.toString());
        assertThat(ops.getText()).contains(PURCHASE_ID.toString());
    }

    @Test
    void skips_the_student_message_but_still_notifies_ops_when_the_email_does_not_resolve() {
        when(userEmailLookupPort.findEmailById(USER_ID)).thenReturn(Optional.empty());
        PurchaseExceptionNotification notification = new PurchaseExceptionNotification(
            PAYMENT_ID, PURCHASE_ID, USER_ID, "TARGET_NOT_SCHEDULED", OCCURRED_AT, "billing.PaymentFulfillmentFailed"
        );

        adapter.notify(notification);

        verify(mailSender, times(1)).send(any(SimpleMailMessage.class));
    }

    @Test
    void skips_the_student_message_when_userId_is_null() {
        PurchaseExceptionNotification notification = new PurchaseExceptionNotification(
            PAYMENT_ID, null, null, "TARGET_NOT_SCHEDULED", OCCURRED_AT, "billing.PaymentFulfillmentFailed"
        );

        adapter.notify(notification);

        verify(mailSender, times(1)).send(any(SimpleMailMessage.class));
    }

    @Test
    void propagates_mail_exceptions_unchanged_so_the_worker_retries() {
        when(userEmailLookupPort.findEmailById(USER_ID)).thenReturn(Optional.of("buyer@example.com"));
        MailSendException smtpDown = new MailSendException("smtp unavailable");
        doThrow(smtpDown).when(mailSender).send(any(SimpleMailMessage.class));
        PurchaseExceptionNotification notification = new PurchaseExceptionNotification(
            PAYMENT_ID, PURCHASE_ID, USER_ID, "CAPACITY_BELOW_ASSIGNED", OCCURRED_AT, "billing.PurchaseExceptioned"
        );

        assertThatThrownBy(() -> adapter.notify(notification)).isSameAs(smtpDown);
    }
}
