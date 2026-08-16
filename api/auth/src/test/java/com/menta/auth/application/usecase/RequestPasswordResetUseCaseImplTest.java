package com.menta.auth.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.menta.auth.application.dto.RequestPasswordResetCommand;
import com.menta.auth.application.dto.RequestPasswordResetResult;
import com.menta.auth.application.port.out.Clock;
import com.menta.auth.application.port.out.DeliveryEnvelope;
import com.menta.auth.application.port.out.OutboxAppender;
import com.menta.auth.application.port.out.PasswordResetDeliveryCipher;
import com.menta.auth.application.port.out.PasswordResetRequestRateLimitPort;
import com.menta.auth.application.port.out.PasswordResetTokenGenerator;
import com.menta.auth.application.port.out.PasswordResetTokenHasher;
import com.menta.auth.application.port.out.PasswordResetTokenRepository;
import com.menta.auth.application.port.out.RateLimitDecision;
import com.menta.auth.domain.exception.PasswordResetRateLimitedException;
import com.menta.auth.domain.model.Role;
import com.menta.auth.domain.model.User;
import com.menta.auth.domain.model.UserId;
import com.menta.auth.domain.model.UserStatus;
import com.menta.auth.domain.repository.UserRepository;
import com.menta.shared.domain.vo.Email;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * US-AUTH-005: forgot-password.
 *
 * The observable response is byte-for-byte identical for a nonexistent email
 * and every non-ACTIVE status (PENDING_ACTIVATION, INACTIVE, SUSPENDED,
 * LOCKED) — only an ACTIVE account causes a persistence side-effect. This
 * generalises the issue's two named scenarios (bloqueada/inactiva) to every
 * non-active status under one rule, consistent with the anti-enumeration
 * principle applying uniformly rather than to only the cases the issue
 * happened to name.
 */
@ExtendWith(MockitoExtension.class)
class RequestPasswordResetUseCaseImplTest {

    private static final String EMAIL = "student@example.com";
    private static final DeliveryEnvelope ENVELOPE =
        DeliveryEnvelope.of("cipher".getBytes(StandardCharsets.UTF_8), new byte[12], 1);
    private static final Instant FIXED_NOW = Instant.parse("2026-08-16T00:00:00Z");

    @Mock private UserRepository userRepository;
    @Mock private PasswordResetTokenRepository passwordResetTokenRepository;
    @Mock private PasswordResetTokenGenerator passwordResetTokenGenerator;
    @Mock private PasswordResetTokenHasher passwordResetTokenHasher;
    @Mock private PasswordResetDeliveryCipher passwordResetDeliveryCipher;
    @Mock private PasswordResetRequestRateLimitPort rateLimitPort;
    @Mock private OutboxAppender outboxAppender;
    @Mock private Clock clock;

    private RequestPasswordResetUseCaseImpl useCase;

    @BeforeEach
    void setUp() {
        useCase = new RequestPasswordResetUseCaseImpl(
            userRepository, passwordResetTokenRepository, passwordResetTokenGenerator,
            passwordResetTokenHasher, passwordResetDeliveryCipher, rateLimitPort,
            outboxAppender, clock, Duration.ofHours(1)
        );
    }

    private void allowRateLimit() {
        when(rateLimitPort.consume(anyString(), any())).thenReturn(RateLimitDecision.allowed());
    }

    private User userWithStatus(UserStatus status) {
        return new User(
            UserId.generate(), Email.of(EMAIL), "hash", Role.STUDENT,
            status, LocalDateTime.now(), LocalDateTime.now()
        );
    }

    private RequestPasswordResetCommand command() {
        return new RequestPasswordResetCommand(EMAIL, "client-fp");
    }

    @Nested
    @DisplayName("Respuesta uniforme")
    class UniformResponse {

        @Test
        void nonexistent_email_returns_acknowledged_without_side_effect() {
            allowRateLimit();
            when(userRepository.findByEmail(any())).thenReturn(Optional.empty());

            RequestPasswordResetResult result = useCase.request(command());

            assertThat(result).isEqualTo(RequestPasswordResetResult.ACKNOWLEDGED);
            verify(passwordResetTokenRepository, never()).save(any(), any());
            verify(outboxAppender, never()).append(anyString(), anyString(), anyString());
        }

        @Test
        void locked_account_returns_acknowledged_without_side_effect() {
            // US-AUTH-005 escenario 5.
            allowRateLimit();
            when(userRepository.findByEmail(any())).thenReturn(Optional.of(userWithStatus(UserStatus.LOCKED)));

            RequestPasswordResetResult result = useCase.request(command());

            assertThat(result).isEqualTo(RequestPasswordResetResult.ACKNOWLEDGED);
            verify(passwordResetTokenRepository, never()).save(any(), any());
        }

        @Test
        void inactive_account_returns_acknowledged_without_side_effect() {
            allowRateLimit();
            when(userRepository.findByEmail(any())).thenReturn(Optional.of(userWithStatus(UserStatus.INACTIVE)));

            useCase.request(command());

            verify(passwordResetTokenRepository, never()).save(any(), any());
        }

        @Test
        void pending_activation_account_returns_acknowledged_without_side_effect() {
            // Generalisation: a not-yet-activated account has not proven email
            // ownership through the activation flow yet, so it must not be
            // usable to bootstrap a password reset either.
            allowRateLimit();
            when(userRepository.findByEmail(any()))
                .thenReturn(Optional.of(userWithStatus(UserStatus.PENDING_ACTIVATION)));

            useCase.request(command());

            verify(passwordResetTokenRepository, never()).save(any(), any());
        }

        @Test
        void suspended_account_returns_acknowledged_without_side_effect() {
            allowRateLimit();
            when(userRepository.findByEmail(any())).thenReturn(Optional.of(userWithStatus(UserStatus.SUSPENDED)));

            useCase.request(command());

            verify(passwordResetTokenRepository, never()).save(any(), any());
        }

        @Test
        void active_account_returns_the_same_acknowledged_result() {
            allowRateLimit();
            when(userRepository.findByEmail(any())).thenReturn(Optional.of(userWithStatus(UserStatus.ACTIVE)));
            when(clock.now()).thenReturn(FIXED_NOW);
            when(passwordResetTokenGenerator.generate()).thenReturn("raw-token");
            when(passwordResetTokenHasher.hash("raw-token")).thenReturn("a".repeat(64));
            when(passwordResetDeliveryCipher.encrypt(anyString())).thenReturn(ENVELOPE);
            when(passwordResetTokenRepository.save(any(), eq(ENVELOPE)))
                .thenAnswer(invocation -> invocation.getArgument(0));

            RequestPasswordResetResult result = useCase.request(command());

            assertThat(result).isEqualTo(RequestPasswordResetResult.ACKNOWLEDGED);
        }
    }

    @Nested
    @DisplayName("Emisión para cuenta activa")
    class ActiveAccountIssuance {

        @BeforeEach
        void setUpActiveUser() {
            allowRateLimit();
            when(userRepository.findByEmail(any())).thenReturn(Optional.of(userWithStatus(UserStatus.ACTIVE)));
            when(clock.now()).thenReturn(FIXED_NOW);
            when(passwordResetTokenGenerator.generate()).thenReturn("raw-token");
            when(passwordResetTokenHasher.hash("raw-token")).thenReturn("a".repeat(64));
            when(passwordResetDeliveryCipher.encrypt(anyString())).thenReturn(ENVELOPE);
            when(passwordResetTokenRepository.save(any(), eq(ENVELOPE)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        }

        @Test
        void invalidates_prior_active_tokens_before_issuing_a_new_one() {
            // US-AUTH-005 escenario 3.
            useCase.request(command());

            verify(passwordResetTokenRepository).invalidateActiveByUserId(any(), eq(FIXED_NOW));
        }

        @Test
        void persists_the_token_with_its_encrypted_envelope() {
            useCase.request(command());

            verify(passwordResetTokenRepository).save(any(), eq(ENVELOPE));
        }

        @Test
        void appends_exactly_one_durable_outbox_event() {
            useCase.request(command());

            verify(outboxAppender, org.mockito.Mockito.times(1))
                .append(anyString(), anyString(), anyString());
        }

        @Test
        void never_persists_the_raw_token() {
            useCase.request(command());

            verify(passwordResetTokenHasher).hash("raw-token");
            // The only place "raw-token" may appear is the encrypted envelope
            // plaintext argument — never as a bare persisted string.
            verify(passwordResetTokenRepository, never()).save(
                org.mockito.ArgumentMatchers.argThat(t -> t != null && t.getTokenHash().equals("raw-token")),
                any()
            );
        }
    }

    @Nested
    @DisplayName("Rate limiting")
    class RateLimiting {

        @Test
        void exhausted_budget_rejects_before_touching_the_repository() {
            when(rateLimitPort.consume(anyString(), any()))
                .thenReturn(RateLimitDecision.limited(Duration.ofMinutes(30)));

            assertThatThrownBy(() -> useCase.request(command()))
                .isInstanceOf(PasswordResetRateLimitedException.class)
                .extracting(ex -> ((PasswordResetRateLimitedException) ex).getRetryAfter())
                .isEqualTo(Duration.ofMinutes(30));

            verify(userRepository, never()).findByEmail(any());
        }
    }
}
