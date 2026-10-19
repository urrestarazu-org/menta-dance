package com.menta.billing.infrastructure.notification;

import com.menta.billing.application.dto.PaymentDecisionNotification;
import com.menta.billing.application.port.out.PaymentDecisionNotificationPort;
import com.menta.shared.auth.UserContactPort;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Spring Mail adapter for the buyer-facing approve/reject decision email (#33, US-BILLING-005,
 * design D5/D8/C8/C9) — mirrors {@link
 * com.menta.auth.infrastructure.activation.SpringMailActivationNotificationAdapter}'s shape:
 * {@code JavaMailSender} + {@code SimpleMailMessage} + a {@code @Value} from-address. Deliberately
 * a separate port/adapter/templates from {@code SpringMailPaymentProofNotificationAdapter} (the
 * unrelated ops-mailbox adapter, out of scope for this change).
 *
 * <p>The buyer's email is resolved through {@link UserContactPort} — the address never crosses
 * into billing's {@code application} or {@code domain} layer (ADR-0021). A missing address is
 * logged and skipped, never thrown.</p>
 *
 * <p>D8/C9: both methods defer the actual send to {@link TransactionSynchronization#afterCommit()}
 * when a transaction is active, so the email is sent only once the payment/subscription change
 * has actually committed, and never for a decision that rolled back. Outside an active
 * transaction (tests, correction retries) the send runs inline. Either way, a send failure is
 * caught and logged — never rethrown — so it can never roll back or fail an otherwise-valid
 * approve/reject/correction. The {@code try/catch} inside {@link #guarded} is load-bearing: Spring
 * propagates an exception thrown from {@code afterCommit} back to the caller of {@code commit()},
 * so without it a dead SMTP host would surface as a {@code 500} on an approval that already
 * committed — exactly what D8 forbids.</p>
 */
@Component
public class SpringMailPaymentDecisionNotificationAdapter implements PaymentDecisionNotificationPort {

    private static final Logger log = LoggerFactory.getLogger(SpringMailPaymentDecisionNotificationAdapter.class);

    private final JavaMailSender mailSender;
    private final UserContactPort userContactPort;
    private final String fromAddress;

    public SpringMailPaymentDecisionNotificationAdapter(
        JavaMailSender mailSender, UserContactPort userContactPort,
        @Value("${billing.bank-transfer.proof.from-address:no-reply@menta.local}") String fromAddress
    ) {
        this.mailSender = mailSender;
        this.userContactPort = userContactPort;
        this.fromAddress = requiredValue(fromAddress, "payment decision from address");
    }

    @Override
    public void notifyApproved(PaymentDecisionNotification notification) {
        sendAfterCommit(() -> send(notification, approvedMessage(notification)));
    }

    @Override
    public void notifyRejected(PaymentDecisionNotification notification) {
        sendAfterCommit(() -> send(notification, rejectedMessage(notification)));
    }

    private void send(PaymentDecisionNotification notification, SimpleMailMessage message) {
        Optional<String> buyerEmail = userContactPort.emailOf(notification.buyerUserId());
        if (buyerEmail.isEmpty()) {
            log.warn(
                "Buyer decision email skipped — no address for userId={} paymentId={}",
                notification.buyerUserId(), notification.paymentId()
            );
            return;
        }
        message.setTo(buyerEmail.get());
        mailSender.send(message);
    }

    private SimpleMailMessage approvedMessage(PaymentDecisionNotification notification) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromAddress);
        message.setSubject("[Menta Dance] Tu pago fue aprobado");
        message.setText(
            "Hola,\n\nTu comprobante de transferencia para el pago " + notification.paymentId()
                + " fue verificado y aprobado. Tu acceso a " + notification.targetReference()
                + " ya está activo.\n\nMonto: " + notification.amount() + " " + notification.currency()
                + "\n\nGracias por elegir Menta Dance."
        );
        return message;
    }

    private SimpleMailMessage rejectedMessage(PaymentDecisionNotification notification) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromAddress);
        message.setSubject("[Menta Dance] Tu pago fue rechazado");
        message.setText(
            "Hola,\n\nTu comprobante de transferencia para el pago " + notification.paymentId()
                + " fue revisado y rechazado.\n\nMotivo: " + notification.reason()
                + "\n\nSi creés que se trata de un error, contactá a soporte."
        );
        return message;
    }

    private void sendAfterCommit(Runnable send) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            guarded(send);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                guarded(send);
            }
        });
    }

    private void guarded(Runnable send) {
        try {
            send.run();
        } catch (RuntimeException failure) {
            log.warn("Buyer decision email failed — payment stands", failure);
        }
    }

    private static String requiredValue(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " cannot be blank");
        }
        return value;
    }
}
