package com.menta.billing.infrastructure.notification;

import com.menta.billing.application.dto.PaymentProofNotification;
import com.menta.billing.application.port.out.PaymentProofNotificationPort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/**
 * Spring Mail adapter that notifies a single fixed operations mailbox that a bank-transfer
 * payment proof was submitted (#31, US-BILLING-003, design D2/C12) — mirrors {@link
 * SpringMailPurchaseExceptionNotificationAdapter}'s shape, minus the buyer-facing message: design
 * D2 draws notification identity (this fixed ops mailbox) and authorization identity (the
 * existing {@code ROLE_ADMIN} mechanism, used by {@code PaymentAdminController} in a later phase)
 * as two entirely separate mechanisms; a proof submission has no student-facing exception to
 * report, the buyer is the one who just acted.
 *
 * <p>Only a thrown {@code MailException} escapes, per design C12 step 6 — the caller's request
 * fails and the owner retries within their upload budget rather than the notification being
 * silently swallowed.</p>
 */
@Component
public class SpringMailPaymentProofNotificationAdapter implements PaymentProofNotificationPort {

    private final JavaMailSender mailSender;
    private final String opsAddress;
    private final String fromAddress;

    public SpringMailPaymentProofNotificationAdapter(
        JavaMailSender mailSender,
        @Value("${billing.bank-transfer.proof.ops-address:ops@menta.local}") String opsAddress,
        @Value("${billing.bank-transfer.proof.from-address:no-reply@menta.local}") String fromAddress
    ) {
        this.mailSender = mailSender;
        this.opsAddress = requiredValue(opsAddress, "payment proof ops address");
        this.fromAddress = requiredValue(fromAddress, "payment proof from address");
    }

    @Override
    public void notifyProofSubmitted(PaymentProofNotification notification) {
        mailSender.send(opsMessage(notification));
    }

    private SimpleMailMessage opsMessage(PaymentProofNotification notification) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromAddress);
        message.setTo(opsAddress);
        message.setSubject("[Menta Dance] Comprobante de transferencia recibido — pago " + notification.paymentId());
        message.setText(
            "paymentId=" + notification.paymentId()
                + " userId=" + notification.userId()
                + " archivo=" + notification.originalFilename()
                + " recibido=" + notification.occurredAt()
        );
        return message;
    }

    private static String requiredValue(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " cannot be blank");
        }
        return value;
    }
}
