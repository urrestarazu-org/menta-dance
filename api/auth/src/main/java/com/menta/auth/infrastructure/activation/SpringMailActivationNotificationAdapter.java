package com.menta.auth.infrastructure.activation;

import com.menta.auth.application.port.out.ActivationDeliveryCipher;
import com.menta.auth.application.port.out.ActivationNotificationPort;
import com.menta.auth.application.port.out.DeliveryEnvelope;
import com.menta.auth.infrastructure.persistence.entity.ActivationTokenJpaEntity;
import com.menta.auth.infrastructure.persistence.repository.ActivationTokenJpaRepository;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/** Spring Mail adapter that sends encrypted durable activation deliveries. */
@Component
public class SpringMailActivationNotificationAdapter implements ActivationNotificationPort {

    private static final String ACTIVATION_PATH = "/api/v1/auth/activate/";

    private final ActivationTokenJpaRepository tokenRepository;
    private final ActivationDeliveryCipher deliveryCipher;
    private final JavaMailSender mailSender;
    private final String publicBaseUrl;
    private final String fromAddress;

    public SpringMailActivationNotificationAdapter(
        ActivationTokenJpaRepository tokenRepository,
        ActivationDeliveryCipher deliveryCipher,
        JavaMailSender mailSender,
        @Value("${auth.activation.public-base-url:http://localhost:8080}") String publicBaseUrl,
        @Value("${auth.activation.from-address:no-reply@menta.local}") String fromAddress
    ) {
        this.tokenRepository = tokenRepository;
        this.deliveryCipher = deliveryCipher;
        this.mailSender = mailSender;
        this.publicBaseUrl = stripTrailingSlash(publicBaseUrl);
        this.fromAddress = requiredValue(fromAddress, "activation from address");
    }

    @Override
    public void sendActivationEmail(UUID activationTokenId) {
        ActivationTokenJpaEntity token = tokenRepository.findById(activationTokenId)
            .orElseThrow(() -> new IllegalArgumentException("Activation token not found"));
        DeliveryEnvelope envelope = deliveryEnvelope(token);
        String[] delivery = deliveryCipher.decrypt(envelope).split("\\|", -1);
        if (delivery.length != 2 || delivery[0].isBlank() || delivery[1].isBlank()) {
            throw new IllegalStateException("Invalid activation delivery payload");
        }

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromAddress);
        message.setTo(delivery[0]);
        message.setSubject("Activate your Menta Dance account");
        message.setText("Activate your account: " + publicBaseUrl + ACTIVATION_PATH + delivery[1]);
        mailSender.send(message);
        tokenRepository.clearDeliveryEnvelope(activationTokenId);
    }

    private static DeliveryEnvelope deliveryEnvelope(ActivationTokenJpaEntity token) {
        byte[] ciphertext = token.getDeliveryCiphertext();
        byte[] nonce = token.getDeliveryNonce();
        Short keyVersion = token.getDeliveryKeyVersion();
        if (ciphertext == null || nonce == null || keyVersion == null) {
            throw new IllegalStateException("Activation delivery envelope is unavailable");
        }
        return DeliveryEnvelope.of(ciphertext, nonce, keyVersion);
    }

    private static String stripTrailingSlash(String value) {
        value = requiredValue(value, "activation public base URL");
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    private static String requiredValue(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " cannot be blank");
        }
        return value;
    }
}
