package com.menta.auth.infrastructure.activation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.menta.auth.application.port.out.ActivationDeliveryCipher;
import com.menta.auth.application.port.out.DeliveryEnvelope;
import com.menta.auth.infrastructure.persistence.entity.ActivationTokenJpaEntity;
import com.menta.auth.infrastructure.persistence.repository.ActivationTokenJpaRepository;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

@ExtendWith(MockitoExtension.class)
class SpringMailActivationNotificationAdapterTest {

    private static final UUID TOKEN_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @Mock private ActivationTokenJpaRepository repository;
    @Mock private ActivationDeliveryCipher deliveryCipher;
    @Mock private JavaMailSender mailSender;

    @Test
    void sends_the_link_then_clears_the_envelope_only_after_smtp_accepts_it() {
        ActivationTokenJpaEntity token = tokenWithDeliveryEnvelope();
        when(repository.findById(TOKEN_ID)).thenReturn(Optional.of(token));
        when(deliveryCipher.decrypt(any(DeliveryEnvelope.class)))
            .thenReturn("student@example.com|raw-activation-token");
        SpringMailActivationNotificationAdapter adapter = new SpringMailActivationNotificationAdapter(
            repository, deliveryCipher, mailSender, "https://menta.example", "no-reply@menta.example"
        );

        adapter.sendActivationEmail(TOKEN_ID);

        ArgumentCaptor<SimpleMailMessage> message = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(message.capture());
        assertThat(message.getValue().getTo()).containsExactly("student@example.com");
        assertThat(message.getValue().getFrom()).isEqualTo("no-reply@menta.example");
        assertThat(message.getValue().getText())
            .contains("https://menta.example/api/v1/auth/activate/raw-activation-token");
        verify(repository).clearDeliveryEnvelope(TOKEN_ID);
    }

    @Test
    void retains_the_envelope_when_smtp_rejects_the_message() {
        when(repository.findById(TOKEN_ID)).thenReturn(Optional.of(tokenWithDeliveryEnvelope()));
        when(deliveryCipher.decrypt(any(DeliveryEnvelope.class)))
            .thenReturn("student@example.com|raw-activation-token");
        doThrow(new RuntimeException("smtp unavailable")).when(mailSender).send(any(SimpleMailMessage.class));
        SpringMailActivationNotificationAdapter adapter = new SpringMailActivationNotificationAdapter(
            repository, deliveryCipher, mailSender, "https://menta.example", "no-reply@menta.example"
        );

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> adapter.sendActivationEmail(TOKEN_ID))
            .isInstanceOf(RuntimeException.class)
            .hasMessage("smtp unavailable");

        verify(repository, never()).clearDeliveryEnvelope(TOKEN_ID);
    }

    private static ActivationTokenJpaEntity tokenWithDeliveryEnvelope() {
        return new ActivationTokenJpaEntity(
            TOKEN_ID, UUID.randomUUID(), "a".repeat(64), new byte[] {1, 2, 3}, new byte[12],
            (short) 1, Instant.parse("2026-08-16T12:00:00Z"),
            Instant.parse("2026-08-15T12:00:00Z"), null, null
        );
    }
}
