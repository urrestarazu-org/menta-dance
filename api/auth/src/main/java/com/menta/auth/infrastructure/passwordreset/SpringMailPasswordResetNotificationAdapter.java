package com.menta.auth.infrastructure.passwordreset;

import com.menta.auth.application.port.out.DeliveryEnvelope;
import com.menta.auth.application.port.out.PasswordResetDeliveryCipher;
import com.menta.auth.application.port.out.PasswordResetNotificationPort;
import com.menta.auth.infrastructure.persistence.entity.PasswordResetTokenJpaEntity;
import com.menta.auth.infrastructure.persistence.repository.PasswordResetTokenJpaRepository;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/**
 * Spring Mail adapter that sends encrypted durable password-reset deliveries.
 *
 * <p>The envelope is cleared only after the SMTP server accepts the message,
 * so a crash mid-send leaves the row intact for the outbox to retry — the raw
 * token exists nowhere else, so losing the envelope before delivery would
 * strand the user with a token they never received.</p>
 */
@Component
public class SpringMailPasswordResetNotificationAdapter implements PasswordResetNotificationPort {

    private static final String RESET_PATH = "/reset-password?token=";

    private final PasswordResetTokenJpaRepository tokenRepository;
    private final PasswordResetDeliveryCipher deliveryCipher;
    private final JavaMailSender mailSender;
    private final String publicBaseUrl;
    private final String fromAddress;

    public SpringMailPasswordResetNotificationAdapter(
        PasswordResetTokenJpaRepository tokenRepository,
        PasswordResetDeliveryCipher deliveryCipher,
        JavaMailSender mailSender,
        @Value("${auth.password-reset.public-base-url:http://localhost:8080}") String publicBaseUrl,
        @Value("${auth.password-reset.from-address:no-reply@menta.local}") String fromAddress
    ) {
        this.tokenRepository = tokenRepository;
        this.deliveryCipher = deliveryCipher;
        this.mailSender = mailSender;
        this.publicBaseUrl = stripTrailingSlash(publicBaseUrl);
        this.fromAddress = requiredValue(fromAddress, "password reset from address");
    }

    @Override
    public void sendPasswordResetEmail(UUID passwordResetTokenId) {
        PasswordResetTokenJpaEntity token = tokenRepository.findById(passwordResetTokenId)
            .orElseThrow(() -> new IllegalArgumentException("Password reset token not found"));
        DeliveryEnvelope envelope = deliveryEnvelope(token);
        String[] delivery = deliveryCipher.decrypt(envelope).split("\\|", -1);
        if (delivery.length != 2 || delivery[0].isBlank() || delivery[1].isBlank()) {
            throw new IllegalStateException("Invalid password reset delivery payload");
        }

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromAddress);
        message.setTo(delivery[0]);
        message.setSubject("Recuperación de contraseña - Menta Dance");
        message.setText(
            "Restablecé tu contraseña: " + publicBaseUrl + RESET_PATH + delivery[1]
                + "\n\nEl enlace vence en una hora. Si no pediste este cambio, ignorá este correo."
        );
        mailSender.send(message);
        tokenRepository.clearDeliveryEnvelope(passwordResetTokenId);
    }

    private static DeliveryEnvelope deliveryEnvelope(PasswordResetTokenJpaEntity token) {
        byte[] ciphertext = token.getDeliveryCiphertext();
        byte[] nonce = token.getDeliveryNonce();
        Short keyVersion = token.getDeliveryKeyVersion();
        if (ciphertext == null || nonce == null || keyVersion == null) {
            throw new IllegalStateException("Password reset delivery envelope is unavailable");
        }
        return DeliveryEnvelope.of(ciphertext, nonce, keyVersion);
    }

    private static String stripTrailingSlash(String value) {
        value = requiredValue(value, "password reset public base URL");
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    private static String requiredValue(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " cannot be blank");
        }
        return value;
    }
}
