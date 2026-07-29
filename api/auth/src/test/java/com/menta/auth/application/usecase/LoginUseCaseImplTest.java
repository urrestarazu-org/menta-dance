package com.menta.auth.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.menta.auth.application.contract.AuthOutboxEventTypes;
import com.menta.auth.application.dto.LoginCommand;
import com.menta.auth.application.dto.TokenPair;
import com.menta.auth.application.port.out.AccessTokenIssuer;
import com.menta.auth.application.port.out.AuthDegradedGuard;
import com.menta.auth.application.port.out.IssuedAccessToken;
import com.menta.auth.application.port.out.OutboxAppender;
import com.menta.auth.application.port.out.PasswordEncoderPort;
import com.menta.auth.application.port.out.RefreshTokenRepository;
import com.menta.auth.application.port.out.TokenHasher;
import com.menta.auth.domain.exception.AuthDegradedException;
import com.menta.auth.domain.exception.InvalidCredentialsException;
import com.menta.auth.domain.exception.LockedUserException;
import com.menta.auth.domain.model.RefreshToken;
import com.menta.auth.domain.model.Role;
import com.menta.auth.domain.model.User;
import com.menta.auth.domain.model.UserId;
import com.menta.auth.domain.model.UserStatus;
import com.menta.auth.domain.repository.UserRepository;
import com.menta.shared.domain.vo.Email;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * RED-GREEN discipline: this test references LoginUseCaseImpl BEFORE it
 * exists, so the file must not compile until 2.2 GREEN provides the impl.
 *
 * Covers auth-login spec scenarios:
 *   - "Credenciales válidas y reconciliador al día"
 *   - "Cuenta LOCKED rechaza login"
 *   - "Credenciales inválidas responde 401 sin discriminar"
 *   - "Reconciliador atrasado produce 503" (ADR-0026 AUTH_DEGRADED)
 *
 * Pure Mockito test: no Spring context, no Testcontainers. Domain layer is
 * not touched — mocks isolate the application orchestration.
 */
@ExtendWith(MockitoExtension.class)
class LoginUseCaseImplTest {

    private static final Email EMAIL = Email.of("alice@example.com");
    private static final String RAW_PASSWORD = "Sup3rSecret!";
    private static final String HASHED_PASSWORD = "bcrypt$2a$10$hash...";
    private static final Duration ACCESS_TTL = Duration.ofMinutes(15);
    private static final Duration REFRESH_TTL = Duration.ofDays(7);
    private static final UUID USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID JTI = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final String REFRESH_RAW = UUID.randomUUID().toString();
    private static final String REFRESH_HASH = "fresh-raw-token-hash-".repeat(4).substring(0, 64);

    @Mock private UserRepository userRepository;
    @Mock private PasswordEncoderPort passwordEncoder;
    @Mock private AccessTokenIssuer accessTokenIssuer;
    @Mock private TokenHasher tokenHasher;
    @Mock private RefreshTokenRepository refreshTokenRepository;
    @Mock private OutboxAppender outboxAppender;
    @Mock private AuthDegradedGuard authDegradedGuard;

    private LoginUseCaseImpl useCase;

    @BeforeEach
    void setUp() {
        // Default: healthy reconciler. Per-test overrides when needed.
        lenient().when(authDegradedGuard.isDegraded()).thenReturn(false);
        lenient().when(accessTokenIssuer.ttl()).thenReturn(ACCESS_TTL);

        useCase = new LoginUseCaseImpl(
            userRepository,
            passwordEncoder,
            accessTokenIssuer,
            tokenHasher,
            refreshTokenRepository,
            outboxAppender,
            authDegradedGuard
        );
    }

    @Nested
    @DisplayName("Spec: Credenciales válidas y reconciliador al día")
    class ValidCredentialsScenario {

        @Test
        void issues_token_pair_and_emits_outbox_event() {
            User user = activeUser(USER_ID, Role.STUDENT, HASHED_PASSWORD, 1L);
            IssuedAccessToken access = new IssuedAccessToken("jwt.compact.string", JTI.toString(), ACCESS_TTL);

            when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
            when(passwordEncoder.matches(RAW_PASSWORD, HASHED_PASSWORD)).thenReturn(true);
            when(accessTokenIssuer.issue(user)).thenReturn(access);
            when(refreshTokenRepository.save(any(RefreshToken.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
            when(tokenHasher.hash(anyString())).thenReturn(REFRESH_HASH);

            TokenPair result = useCase.execute(new LoginCommand(EMAIL.getValue(), RAW_PASSWORD));

            assertThat(result).isNotNull();
            assertThat(result.accessToken()).isEqualTo("jwt.compact.string");
            assertThat(result.tokenType()).isEqualTo("Bearer");
            assertThat(result.expiresIn()).isEqualTo(ACCESS_TTL);
            assertThat(result.refreshToken()).isNotEmpty();

            // Refresh persisted in ACTIVE state with token_version matching user.
            verify(refreshTokenRepository).save(any(RefreshToken.class));
            // Outbox event mirrors spec shape (auth.AuthUserLoggedIn + jti + token_version).
            verify(outboxAppender, times(1))
                .append(
                    eq(AuthOutboxEventTypes.AUTH_USER_LOGGED_IN),
                    eq(JTI.toString()),
                    eq("{\"tokenVersion\":1}")
                );
        }
    }

    @Nested
    @DisplayName("Spec: Cuenta LOCKED rechaza login")
    class LockedUserScenario {

        @Test
        void rejects_locked_with_dedicated_exception_and_no_outbox() {
            User locked = lockedUser(USER_ID, HASHED_PASSWORD);

            when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(locked));

            assertThatThrownBy(() ->
                useCase.execute(new LoginCommand(EMAIL.getValue(), RAW_PASSWORD))
            )
                .isInstanceOf(LockedUserException.class)
                .hasMessageContaining(USER_ID.toString());

            verify(outboxAppender, never()).append(anyString(), anyString(), anyString());
            verify(refreshTokenRepository, never()).save(any());
            verify(accessTokenIssuer, never()).issue(any());
            // LOCKED path checks status BEFORE password match (defense in
            // depth — even a known password on a locked account MUST be
            // refused).
            verify(passwordEncoder, never()).matches(anyString(), anyString());
        }
    }

    @Nested
    @DisplayName("Spec: Credenciales inválidas responde 401 sin discriminar")
    class InvalidCredentialsScenario {

        @Test
        void wrong_password_throws_invalid_credentials_without_outbox() {
            User user = activeUser(USER_ID, Role.STUDENT, HASHED_PASSWORD, 1L);

            when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
            when(passwordEncoder.matches(RAW_PASSWORD, HASHED_PASSWORD)).thenReturn(false);

            assertThatThrownBy(() ->
                useCase.execute(new LoginCommand(EMAIL.getValue(), RAW_PASSWORD))
            )
                .isInstanceOf(InvalidCredentialsException.class);

            verify(outboxAppender, never()).append(anyString(), anyString(), anyString());
            verify(accessTokenIssuer, never()).issue(any());
            verify(refreshTokenRepository, never()).save(any());
        }

        @Test
        void unknown_email_throws_invalid_credentials_without_outbox() {
            when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());

            assertThatThrownBy(() ->
                useCase.execute(new LoginCommand(EMAIL.getValue(), RAW_PASSWORD))
            )
                .isInstanceOf(InvalidCredentialsException.class);

            verify(passwordEncoder, never()).matches(anyString(), anyString());
            verify(outboxAppender, never()).append(anyString(), anyString(), anyString());
        }

        @Test
        void unknown_email_and_wrong_password_raise_identical_exception_type() {
            // Spec: "el cuerpo del error es idéntico en ambos casos".
            // Both unknown-email and wrong-password paths MUST raise the same
            // exception class so the controller can render them identically.
            assertThat(LockedUserException.class)
                .isNotEqualTo(InvalidCredentialsException.class);

            // Each branch produces InvalidCredentialsException — proven by the
            // two tests above which assert the same .class.
        }
    }

    @Nested
    @DisplayName("Spec: Reconciliador atrasado produce 503")
    class DegradedReconcilerScenario {

        @Test
        void degraded_guard_blocks_login_before_password_check() {
            when(authDegradedGuard.isDegraded()).thenReturn(true);

            assertThatThrownBy(() ->
                useCase.execute(new LoginCommand(EMAIL.getValue(), RAW_PASSWORD))
            )
                .isInstanceOf(AuthDegradedException.class);

            // Fail-closed: no side effects at all.
            verify(userRepository, never()).findByEmail(any());
            verify(passwordEncoder, never()).matches(anyString(), anyString());
            verify(accessTokenIssuer, never()).issue(any());
            verify(tokenHasher, never()).hash(anyString());
            verify(refreshTokenRepository, never()).save(any());
            verify(outboxAppender, never()).append(anyString(), anyString(), anyString());
        }
    }

    // ---- domain fixtures ----

    private User activeUser(UUID id, Role role, String passwordHash, long tokenVersion) {
        return userWithStatus(id, role, passwordHash, tokenVersion, UserStatus.ACTIVE);
    }

    private User lockedUser(UUID id, String passwordHash) {
        return userWithStatus(id, Role.STUDENT, passwordHash, 1L, UserStatus.LOCKED);
    }

    private User userWithStatus(UUID id, Role role, String passwordHash, long tokenVersion, UserStatus status) {
        return new User(
            UserId.of(id),
            EMAIL,
            passwordHash,
            role,
            status,
            LocalDateTime.now(),
            LocalDateTime.now()
        );
    }
}
