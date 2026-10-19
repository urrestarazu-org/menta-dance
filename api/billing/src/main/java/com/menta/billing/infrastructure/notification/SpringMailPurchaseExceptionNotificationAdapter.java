package com.menta.billing.infrastructure.notification;

import com.menta.billing.application.dto.PurchaseExceptionNotification;
import com.menta.billing.application.port.out.PurchaseExceptionNotificationPort;
import com.menta.shared.auth.UserEmailLookupPort;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/**
 * Spring Mail adapter that sends the two purchase-exception recipients
 * (design C4/C5): the buyer (best-effort — a missing email never suppresses
 * ops) and a configured operations address.
 *
 * <h2>Delivery order (C4)</h2>
 * <ol>
 *   <li>Resolve the buyer email. {@code Optional.empty()} (or a null {@code
 *       userId}, site 130 with {@code payment == null}) → log, skip the
 *       student message, continue.</li>
 *   <li>Send the student message (Spanish, D9 — no name, no refund, states
 *       operations will follow up).</li>
 *   <li>Send the ops message (may carry {@code Reason}, per C5).</li>
 * </ol>
 *
 * <p>Only a thrown {@code MailException} escapes, so the worker retries the
 * row — never lost. Delivery is at-least-once, identical to the activation
 * email; a retry after a partial send may duplicate one message.</p>
 */
@Component
public class SpringMailPurchaseExceptionNotificationAdapter implements PurchaseExceptionNotificationPort {

    private static final Logger log = LoggerFactory.getLogger(
        SpringMailPurchaseExceptionNotificationAdapter.class
    );

    private final UserEmailLookupPort userEmailLookupPort;
    private final JavaMailSender mailSender;
    private final String opsAddress;
    private final String fromAddress;

    public SpringMailPurchaseExceptionNotificationAdapter(
        UserEmailLookupPort userEmailLookupPort,
        JavaMailSender mailSender,
        @Value("${billing.purchase-exception.ops-address:ops@menta.local}") String opsAddress,
        @Value("${billing.purchase-exception.from-address:no-reply@menta.local}") String fromAddress
    ) {
        this.userEmailLookupPort = userEmailLookupPort;
        this.mailSender = mailSender;
        this.opsAddress = requiredValue(opsAddress, "purchase exception ops address");
        this.fromAddress = requiredValue(fromAddress, "purchase exception from address");
    }

    @Override
    public void notify(PurchaseExceptionNotification notification) {
        Optional<String> studentEmail = resolveStudentEmail(notification.userId());
        if (studentEmail.isPresent()) {
            mailSender.send(studentMessage(studentEmail.get(), notification));
        } else {
            log.warn(
                "No email resolved for purchase exception userId={}, paymentId={} — skipping student notification",
                notification.userId(), notification.paymentId()
            );
        }
        mailSender.send(opsMessage(notification));
    }

    private Optional<String> resolveStudentEmail(UUID userId) {
        if (userId == null) {
            return Optional.empty();
        }
        return userEmailLookupPort.findEmailById(userId);
    }

    /** No name (D7), no {@code Reason} wording, no refund (D9). */
    private SimpleMailMessage studentMessage(String to, PurchaseExceptionNotification notification) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromAddress);
        message.setTo(to);
        message.setSubject("Tu compra en Menta Dance necesita atención");
        message.setText(
            "Referencia de pago: " + notification.paymentId() + "\n"
                + "Fecha: " + notification.occurredAt() + "\n"
                + "No pudimos confirmar tu lugar. Nuestro equipo se comunicará contigo."
        );
        return message;
    }

    /** Carries the technical reason (C5). */
    private SimpleMailMessage opsMessage(PurchaseExceptionNotification notification) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromAddress);
        message.setTo(opsAddress);
        message.setSubject("[Menta Dance] Compra en EXCEPTION — pago " + notification.paymentId());
        message.setText(
            "paymentId=" + notification.paymentId()
                + " purchaseId=" + orDash(notification.purchaseId())
                + " userId=" + orUnresolved(notification.userId())
                + " reason=" + notification.reason()
                + " occurredAt=" + notification.occurredAt()
                + " evento=" + notification.eventType()
        );
        return message;
    }

    private static String orDash(UUID value) {
        return value == null ? "—" : value.toString();
    }

    private static String orUnresolved(UUID value) {
        return value == null ? "no resuelto" : value.toString();
    }

    private static String requiredValue(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " cannot be blank");
        }
        return value;
    }
}
