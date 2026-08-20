package com.menta.auth.infrastructure.passwordreset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.menta.auth.application.port.out.DeliveryEnvelope;
import com.menta.auth.application.port.out.PasswordResetDeliveryCipher;
import com.menta.auth.infrastructure.persistence.entity.PasswordResetTokenJpaEntity;
import com.menta.auth.infrastructure.persistence.repository.PasswordResetTokenJpaRepository;
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
class SpringMailPasswordResetNotificationAdapterTest {

    private static final UUID TOKEN_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");

    @Mock private PasswordResetTokenJpaRepository repository;
    @Mock private PasswordResetDeliveryCipher deliveryCipher;
    @Mock private JavaMailSender mailSender;

    @Test
    void sends_the_link_then_clears_the_envelope_only_after_smtp_accepts_it() {
        when(repository.findById(TOKEN_ID)).thenReturn(Optional.of(tokenWithDeliveryEnvelope()));
        when(deliveryCipher.decrypt(any(DeliveryEnvelope.class)))
            .thenReturn("student@example.com|raw-reset-token");
        SpringMailPasswordResetNotificationAdapter adapter = new SpringMailPasswordResetNotificationAdapter(
            repository, deliveryCipher, mailSender, "https://menta.example", "no-reply@menta.example"
        );

        adapter.sendPasswordResetEmail(TOKEN_ID);

        ArgumentCaptor<SimpleMailMessage> message = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(message.capture());
        assertThat(message.getValue().getTo()).containsExactly("student@example.com");
        assertThat(message.getValue().getFrom()).isEqualTo("no-reply@menta.example");
        assertThat(message.getValue().getText())
            .contains("https://menta.example/reset-password?token=raw-reset-token");
        verify(repository).clearDeliveryEnvelope(TOKEN_ID);
    }

    @Test
    void strips_trailing_slash_from_the_public_base_url() {
        when(repository.findById(TOKEN_ID)).thenReturn(Optional.of(tokenWithDeliveryEnvelope()));
        when(deliveryCipher.decrypt(any(DeliveryEnvelope.class)))
            .thenReturn("student@example.com|raw-reset-token");
        SpringMailPasswordResetNotificationAdapter adapter = new SpringMailPasswordResetNotificationAdapter(
            repository, deliveryCipher, mailSender, "https://menta.example/", "no-reply@menta.example"
        );

        adapter.sendPasswordResetEmail(TOKEN_ID);

        ArgumentCaptor<SimpleMailMessage> message = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(message.capture());
        assertThat(message.getValue().getText())
            .contains("https://menta.example/reset-password?token=raw-reset-token");
    }

    @Test
    void retains_the_envelope_when_smtp_rejects_the_message() {
        when(repository.findById(TOKEN_ID)).thenReturn(Optional.of(tokenWithDeliveryEnvelope()));
        when(deliveryCipher.decrypt(any(DeliveryEnvelope.class)))
            .thenReturn("student@example.com|raw-reset-token");
        doThrow(new RuntimeException("smtp unavailable")).when(mailSender).send(any(SimpleMailMessage.class));
        SpringMailPasswordResetNotificationAdapter adapter = new SpringMailPasswordResetNotificationAdapter(
            repository, deliveryCipher, mailSender, "https://menta.example", "no-reply@menta.example"
        );

        assertThatThrownBy(() -> adapter.sendPasswordResetEmail(TOKEN_ID))
            .isInstanceOf(RuntimeException.class)
            .hasMessage("smtp unavailable");

        verify(repository, never()).clearDeliveryEnvelope(TOKEN_ID);
    }

    @Test
    void throws_when_the_token_is_not_found() {
        when(repository.findById(TOKEN_ID)).thenReturn(Optional.empty());
        SpringMailPasswordResetNotificationAdapter adapter = new SpringMailPasswordResetNotificationAdapter(
            repository, deliveryCipher, mailSender, "https://menta.example", "no-reply@menta.example"
        );

        assertThatThrownBy(() -> adapter.sendPasswordResetEmail(TOKEN_ID))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Password reset token not found");
    }

    @Test
    void throws_when_the_delivery_envelope_is_unavailable() {
        PasswordResetTokenJpaEntity tokenWithoutEnvelope = new PasswordResetTokenJpaEntity(
            TOKEN_ID, UUID.randomUUID(), "a".repeat(64), null, null,
            null, Instant.parse("2026-08-16T12:00:00Z"),
            Instant.parse("2026-08-15T12:00:00Z"), null, null
        );
        when(repository.findById(TOKEN_ID)).thenReturn(Optional.of(tokenWithoutEnvelope));
        SpringMailPasswordResetNotificationAdapter adapter = new SpringMailPasswordResetNotificationAdapter(
            repository, deliveryCipher, mailSender, "https://menta.example", "no-reply@menta.example"
        );

        assertThatThrownBy(() -> adapter.sendPasswordResetEmail(TOKEN_ID))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("Password reset delivery envelope is unavailable");
    }

    @Test
    void throws_when_the_decrypted_delivery_payload_is_malformed() {
        when(repository.findById(TOKEN_ID)).thenReturn(Optional.of(tokenWithDeliveryEnvelope()));
        when(deliveryCipher.decrypt(any(DeliveryEnvelope.class))).thenReturn("not-a-valid-payload");
        SpringMailPasswordResetNotificationAdapter adapter = new SpringMailPasswordResetNotificationAdapter(
            repository, deliveryCipher, mailSender, "https://menta.example", "no-reply@menta.example"
        );

        assertThatThrownBy(() -> adapter.sendPasswordResetEmail(TOKEN_ID))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("Invalid password reset delivery payload");
    }

    @Test
    void rejects_a_blank_public_base_url() {
        assertThatThrownBy(() -> new SpringMailPasswordResetNotificationAdapter(
            repository, deliveryCipher, mailSender, "  ", "no-reply@menta.example"
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejects_a_blank_from_address() {
        assertThatThrownBy(() -> new SpringMailPasswordResetNotificationAdapter(
            repository, deliveryCipher, mailSender, "https://menta.example", " "
        )).isInstanceOf(IllegalArgumentException.class);
    }

    private static PasswordResetTokenJpaEntity tokenWithDeliveryEnvelope() {
        return new PasswordResetTokenJpaEntity(
            TOKEN_ID, UUID.randomUUID(), "a".repeat(64), new byte[] {1, 2, 3}, new byte[12],
            (short) 1, Instant.parse("2026-08-16T12:00:00Z"),
            Instant.parse("2026-08-15T12:00:00Z"), null, null
        );
    }
}
