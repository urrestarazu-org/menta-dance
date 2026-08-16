package com.menta.auth.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.menta.auth.application.dto.ResetPasswordCommand;
import com.menta.auth.application.port.out.LoginRateLimitPort;
import com.menta.auth.application.port.out.PasswordEncoderPort;
import com.menta.auth.application.port.out.PasswordResetAttemptRateLimitPort;
import com.menta.auth.application.port.out.PasswordResetTokenHasher;
import com.menta.auth.application.port.out.PasswordResetTokenRepository;
import com.menta.auth.application.port.out.RateLimitDecision;
import com.menta.auth.application.port.out.RefreshTokenRepository;
import com.menta.auth.domain.exception.PasswordResetRateLimitedException;
import com.menta.auth.domain.exception.PasswordResetTokenAlreadyUsedException;
import com.menta.auth.domain.exception.PasswordResetTokenExpiredException;
import com.menta.auth.domain.exception.PasswordResetTokenNotFoundException;
import com.menta.auth.domain.exception.SamePasswordException;
import com.menta.auth.domain.exception.WeakPasswordException;
import com.menta.auth.domain.model.PasswordResetToken;
import com.menta.auth.domain.model.Role;
import com.menta.auth.domain.model.User;
import com.menta.auth.domain.model.UserId;
import com.menta.auth.domain.model.UserStatus;
import com.menta.auth.domain.repository.UserRepository;
import com.menta.shared.domain.vo.Email;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * US-AUTH-006: reset-password.
 *
 * <p>The validation order is security-relevant, not incidental: a weak or
 * repeated password must reject WITHOUT consuming the token, so the user can
 * retry with the same link (escenario 5: "El token NO debe marcarse como
 * usado"). Consumption only happens after every check passes.</p>
 */
@ExtendWith(MockitoExtension.class)
class ResetPasswordUseCaseImplTest {

    private static final String RAW_TOKEN = "raw-reset-token";
    private static final String TOKEN_HASH = "a".repeat(64);
    private static final UserId USER_ID = UserId.of(UUID.randomUUID());
    private static final String CURRENT_HASH = "current-bcrypt-hash";
    private static final String NEW_RAW_PASSWORD = "NewSecurePass1";
    private static final String NEW_ENCODED_PASSWORD = "new-bcrypt-hash";
    private static final Instant NOW = Instant.parse("2026-08-16T12:00:00Z");
    private static final Instant EXPIRES_AT = NOW.plus(Duration.ofHours(1));
    private static final String EMAIL_FP =
        com.menta.auth.domain.crypto.Sha256Hex.hash("student@example.com");

    @Mock private PasswordResetTokenRepository passwordResetTokenRepository;
    @Mock private PasswordResetTokenHasher passwordResetTokenHasher;
    @Mock private PasswordResetAttemptRateLimitPort attemptRateLimitPort;
    @Mock private UserRepository userRepository;
    @Mock private PasswordEncoderPort passwordEncoder;
    @Mock private RefreshTokenRepository refreshTokenRepository;
    @Mock private LoginRateLimitPort loginRateLimitPort;
    @Mock private com.menta.auth.application.port.out.Clock clock;

    private ResetPasswordUseCaseImpl useCase;

    @BeforeEach
    void setUp() {
        useCase = new ResetPasswordUseCaseImpl(
            passwordResetTokenRepository, passwordResetTokenHasher, attemptRateLimitPort,
            userRepository, passwordEncoder, refreshTokenRepository, loginRateLimitPort, clock
        );
    }

    private void allowRateLimit() {
        when(attemptRateLimitPort.consume(anyString())).thenReturn(RateLimitDecision.allowed());
    }

    private static PasswordResetToken activeToken() {
        return PasswordResetToken.issue(USER_ID, TOKEN_HASH, EXPIRES_AT, NOW);
    }

    private static User activeUser() {
        return new User(
            USER_ID, Email.of("student@example.com"), CURRENT_HASH, Role.STUDENT,
            UserStatus.ACTIVE, LocalDateTime.now(), LocalDateTime.now()
        );
    }

    private ResetPasswordCommand command() {
        return new ResetPasswordCommand(RAW_TOKEN, NEW_RAW_PASSWORD, "client-fp");
    }

    private void stubHappyPathUpTo(PasswordResetToken token, User user) {
        allowRateLimit();
        when(passwordResetTokenHasher.hash(RAW_TOKEN)).thenReturn(TOKEN_HASH);
        when(passwordResetTokenRepository.findByHash(TOKEN_HASH)).thenReturn(Optional.of(token));
        when(clock.now()).thenReturn(NOW);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
    }

    @Nested
    @DisplayName("Rate limiting")
    class RateLimiting {

        @Test
        void exhausted_budget_rejects_before_any_lookup() {
            when(attemptRateLimitPort.consume(anyString()))
                .thenReturn(RateLimitDecision.limited(Duration.ofMinutes(15)));

            assertThatThrownBy(() -> useCase.reset(command()))
                .isInstanceOf(PasswordResetRateLimitedException.class)
                .extracting(ex -> ((PasswordResetRateLimitedException) ex).getRetryAfter())
                .isEqualTo(Duration.ofMinutes(15));

            verify(passwordResetTokenRepository, never()).findByHash(anyString());
        }
    }

    @Nested
    @DisplayName("Búsqueda del token")
    class TokenLookup {

        @Test
        void unknown_hash_throws_not_found() {
            // US-AUTH-006 escenario 3.
            allowRateLimit();
            when(passwordResetTokenHasher.hash(RAW_TOKEN)).thenReturn(TOKEN_HASH);
            when(passwordResetTokenRepository.findByHash(TOKEN_HASH)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> useCase.reset(command()))
                .isInstanceOf(PasswordResetTokenNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("Estado del token")
    class TokenState {

        @Test
        void an_already_used_token_throws_already_used() {
            // US-AUTH-006 escenario 4.
            PasswordResetToken token = activeToken();
            token.consume(NOW);
            allowRateLimit();
            when(passwordResetTokenHasher.hash(RAW_TOKEN)).thenReturn(TOKEN_HASH);
            when(passwordResetTokenRepository.findByHash(TOKEN_HASH)).thenReturn(Optional.of(token));
            when(clock.now()).thenReturn(NOW);

            assertThatThrownBy(() -> useCase.reset(command()))
                .isInstanceOf(PasswordResetTokenAlreadyUsedException.class);
        }

        @Test
        void an_expired_token_throws_expired() {
            // US-AUTH-006 escenario 2.
            PasswordResetToken token = activeToken();
            allowRateLimit();
            when(passwordResetTokenHasher.hash(RAW_TOKEN)).thenReturn(TOKEN_HASH);
            when(passwordResetTokenRepository.findByHash(TOKEN_HASH)).thenReturn(Optional.of(token));
            when(clock.now()).thenReturn(EXPIRES_AT);

            assertThatThrownBy(() -> useCase.reset(command()))
                .isInstanceOf(PasswordResetTokenExpiredException.class);
        }

        @Test
        void an_invalidated_superseded_token_throws_expired_not_already_used() {
            // A superseded token was never used by its holder — telling them
            // "already used" would be false. "Expired, request a new one" is
            // the honest message and the remedy is identical either way.
            PasswordResetToken token = activeToken();
            token.invalidate(NOW);
            allowRateLimit();
            when(passwordResetTokenHasher.hash(RAW_TOKEN)).thenReturn(TOKEN_HASH);
            when(passwordResetTokenRepository.findByHash(TOKEN_HASH)).thenReturn(Optional.of(token));
            when(clock.now()).thenReturn(NOW);

            assertThatThrownBy(() -> useCase.reset(command()))
                .isInstanceOf(PasswordResetTokenExpiredException.class)
                .isNotInstanceOf(PasswordResetTokenAlreadyUsedException.class);
        }
    }

    @Nested
    @DisplayName("Validación de la contraseña nueva — no consume el token si falla")
    class PasswordValidation {

        @Test
        void a_weak_password_is_rejected_without_consuming_the_token() {
            // US-AUTH-006 escenario 5.
            PasswordResetToken token = activeToken();
            stubHappyPathUpTo(token, activeUser());

            assertThatThrownBy(() ->
                useCase.reset(new ResetPasswordCommand(RAW_TOKEN, "weak", "client-fp")))
                .isInstanceOf(WeakPasswordException.class);

            verify(passwordResetTokenRepository, never()).consumeIfActive(any(), any());
            verify(userRepository, never()).save(any());
            verify(refreshTokenRepository, never()).revokeAllByUserId(any());
        }

        @Test
        void the_same_password_as_the_current_one_is_rejected_without_consuming_the_token() {
            // US-AUTH-006 escenario 6.
            PasswordResetToken token = activeToken();
            stubHappyPathUpTo(token, activeUser());
            when(passwordEncoder.matches(NEW_RAW_PASSWORD, CURRENT_HASH)).thenReturn(true);

            assertThatThrownBy(() -> useCase.reset(command()))
                .isInstanceOf(SamePasswordException.class);

            verify(passwordResetTokenRepository, never()).consumeIfActive(any(), any());
            verify(userRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("Reset exitoso")
    class SuccessfulReset {

        @BeforeEach
        void setUpSuccess() {
            PasswordResetToken token = activeToken();
            stubHappyPathUpTo(token, activeUser());
            when(passwordEncoder.matches(NEW_RAW_PASSWORD, CURRENT_HASH)).thenReturn(false);
            // Lenient: the lost-race test below throws before reaching
            // encode(), which would otherwise flag this as an unused stub.
            org.mockito.Mockito.lenient().when(passwordEncoder.encode(NEW_RAW_PASSWORD))
                .thenReturn(NEW_ENCODED_PASSWORD);
            // Lenient: the lost-race test overrides this to return false.
            org.mockito.Mockito.lenient()
                .when(passwordResetTokenRepository.consumeIfActive(eq(token), eq(NOW)))
                .thenReturn(true);
        }

        @Test
        void updates_the_password_hash() {
            useCase.reset(command());

            verify(userRepository).save(argThat(user -> user.getPasswordHash().equals(NEW_ENCODED_PASSWORD)));
        }

        @Test
        void bumps_token_version_by_changing_the_password_through_the_domain_method() {
            // resetPassword() is the domain method that bumps tokenVersion —
            // verified indirectly: the saved user's version moved from the
            // fixture's default.
            User before = activeUser();
            long versionBefore = before.getTokenVersion();

            useCase.reset(command());

            verify(userRepository).save(argThat(user -> user.getTokenVersion() > versionBefore));
        }

        @Test
        void marks_the_token_as_consumed() {
            useCase.reset(command());

            verify(passwordResetTokenRepository).consumeIfActive(any(), eq(NOW));
        }

        @Test
        void revokes_every_refresh_token_for_the_user_not_just_one_family() {
            // US-AUTH-006 escenario 1: "invalidar TODOS los refresh tokens".
            useCase.reset(command());

            verify(refreshTokenRepository).revokeAllByUserId(USER_ID);
        }

        @Test
        void clears_the_login_failure_budget_for_the_email() {
            // Translated from the issue's "reiniciar failedLoginAttempts y
            // desbloquear la cuenta": that field/state does not exist
            // (ADR-0035 — the throttle never locks the account). The
            // equivalent real mechanism is clearing the Redis failure budget,
            // so a user who reset their password does not stay throttled by
            // their own earlier mistakes.
            useCase.reset(command());

            verify(loginRateLimitPort).resetEmail(EMAIL_FP);
        }

        @Test
        void a_lost_race_on_consumeIfActive_surfaces_as_expired() {
            when(passwordResetTokenRepository.consumeIfActive(any(), eq(NOW))).thenReturn(false);

            assertThatThrownBy(() -> useCase.reset(command()))
                .isInstanceOf(PasswordResetTokenExpiredException.class);

            verify(userRepository, never()).save(any());
            verify(refreshTokenRepository, never()).revokeAllByUserId(any());
        }
    }

    private static User argThat(java.util.function.Predicate<User> predicate) {
        return org.mockito.ArgumentMatchers.argThat(predicate::test);
    }
}
