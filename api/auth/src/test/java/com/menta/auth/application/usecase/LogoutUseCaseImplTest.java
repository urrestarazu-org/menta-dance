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

import com.menta.auth.application.contract.AuthOutboxEventTypes;
import com.menta.auth.application.dto.LogoutCommand;
import com.menta.auth.application.port.out.AuthDegradedGuard;
import com.menta.auth.application.port.out.OutboxAppender;
import com.menta.auth.application.port.out.RefreshTokenRepository;
import com.menta.auth.application.port.out.TokenHasher;
import com.menta.auth.domain.exception.AuthDegradedException;
import com.menta.auth.domain.exception.RefreshTokenCompromisedException;
import com.menta.auth.domain.model.RefreshToken;
import com.menta.auth.domain.model.RefreshTokenStatus;
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
 * RED-GREEN discipline: this test references LogoutUseCaseImpl BEFORE it
 * exists, so the file must not compile until 2.6 GREEN provides the impl.
 *
 * Covers scenarios:
 *   - auth-login spec: "Logout invalida refresh con evento post-commit"
 *   - auth-login spec: "Refresh ya rotado activa revocación de familia" (logout of a rotated refresh).
 *
 * Pure Mockito — no Spring context, no DB.
 */
@ExtendWith(MockitoExtension.class)
class LogoutUseCaseImplTest {

    private static final Duration REFRESH_TTL = Duration.ofDays(7);
    private static final UUID USER_ID = UUID.fromString("88888888-8888-8888-8888-888888888888");
    private static final UUID FAMILY_ID = UUID.fromString("99999999-9999-9999-9999-999999999999");
    private static final long TOKEN_VERSION = 1L;
    private static final Email EMAIL = Email.of("alice@example.com");

    @Mock private UserRepository userRepository;
    @Mock private RefreshTokenRepository refreshTokenRepository;
    @Mock private TokenHasher tokenHasher;
    @Mock private OutboxAppender outboxAppender;
    @Mock private AuthDegradedGuard authDegradedGuard;

    private LogoutUseCaseImpl useCase;

    @BeforeEach
    void setUp() {
        useCase = new LogoutUseCaseImpl(
            userRepository,
            refreshTokenRepository,
            tokenHasher,
            outboxAppender,
            authDegradedGuard
        );
    }

    @Nested
    @DisplayName("Spec: Logout invalida refresh con evento post-commit")
    class LogoutAtomic {

        @Test
        void marks_active_refresh_as_revoked_and_publishes_event() {
            String raw = UUID.randomUUID().toString();
            String presentedHash = paddedHash("logout-hash");
            RefreshToken presented = activeRefresh(
                presentedHash,
                TOKEN_VERSION,
                Instant.now().plus(REFRESH_TTL)
            );

            when(tokenHasher.hash(anyString())).thenReturn(presentedHash);
            when(refreshTokenRepository.findByHash(anyString())).thenReturn(Optional.of(presented));

            useCase.execute(new LogoutCommand(raw));

            assertThat(presented.getStatus()).isEqualTo(RefreshTokenStatus.REVOKED);
            assertThat(presented.getRevokedAt()).isNotNull();

            // PR3 fix: persist the status transition. Without this, the
            // row stays ACTIVE in MySQL and the next presentation re-detects
            // the refresh as ACTIVE rather than REVOKED.
            verify(refreshTokenRepository, times(1)).save(presented);
            verify(outboxAppender, times(1)).append(
                eq(AuthOutboxEventTypes.USER_LOGGED_OUT),
                eq(presented.getId().toString()),
                anyString()
            );
        }

        @Test
        void idempotent_logout_does_not_double_publish_event() {
            String raw = UUID.randomUUID().toString();
            String presentedHash = paddedHash("logout-second-pass");
            RefreshToken presented = revokedRefreshStub(presentedHash, TOKEN_VERSION);

            when(tokenHasher.hash(anyString())).thenReturn(presentedHash);
            when(refreshTokenRepository.findByHash(anyString())).thenReturn(Optional.of(presented));

            assertThatThrownBy(() -> useCase.execute(new LogoutCommand(raw)))
                .isInstanceOf(RefreshTokenCompromisedException.class);

            // Already REVOKED → no event (spec: "no se publica ningún evento adicional").
            verify(outboxAppender, never()).append(anyString(), anyString(), anyString());
        }
    }

    @Nested
    @DisplayName("Spec: Refresh ya rotado activa revocación de familia")
    class LogoutOfRotatedRevokesFamily {

        @Test
        void used_refresh_triggers_family_revoke_on_logout() {
            String raw = UUID.randomUUID().toString();
            String presentedHash = paddedHash("already-rotated");
            RefreshToken presented = activeRefresh(presentedHash, TOKEN_VERSION,
                Instant.now().plus(REFRESH_TTL));
            presented.markUsed();  // presented is now USED.

            User user = activeUser(USER_ID, Role.STUDENT, TOKEN_VERSION + 1);

            when(tokenHasher.hash(anyString())).thenReturn(presentedHash);
            when(refreshTokenRepository.findByHash(anyString())).thenReturn(Optional.of(presented));
            when(userRepository.findById(any())).thenReturn(Optional.of(user));
            when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

            assertThatThrownBy(() -> useCase.execute(new LogoutCommand(raw)))
                .isInstanceOf(RefreshTokenCompromisedException.class);

            // Spec: presented-as-USED on any request MUST revoke family + bump token_version.
            assertThat(user.getTokenVersion()).isGreaterThan(TOKEN_VERSION);
            verify(refreshTokenRepository).revokeFamily(FAMILY_ID);
            verify(outboxAppender).append(
                eq(AuthOutboxEventTypes.REFRESH_REVOKED),
                anyString(),
                anyString()
            );
        }
    }

    @Nested
    @DisplayName("Spec: Reail-closed / fail-closed behaviors")
    class DegradedGuard {

        @Test
        void degraded_guard_blocks_logout() {
            when(authDegradedGuard.isDegraded()).thenReturn(true);

            assertThatThrownBy(() ->
                useCase.execute(new LogoutCommand("any-raw-token"))
            ).isInstanceOf(AuthDegradedException.class);

            verify(refreshTokenRepository, never()).findByHash(anyString());
            verify(outboxAppender, never()).append(anyString(), anyString(), anyString());
        }

        @Test
        void unknown_hash_is_rejected_without_state_change() {
            when(tokenHasher.hash(anyString())).thenReturn("unknown");
            when(refreshTokenRepository.findByHash(anyString())).thenReturn(Optional.empty());

            assertThatThrownBy(() ->
                useCase.execute(new LogoutCommand("any-raw"))
            ).isInstanceOf(RefreshTokenCompromisedException.class);

            verify(refreshTokenRepository, never()).revokeFamily(any());
            verify(outboxAppender, never()).append(anyString(), anyString(), anyString());
        }
    }

    // ---- fixtures ----

    private User activeUser(UUID id, Role role, long tokenVersion) {
        User user = new User(
            UserId.of(id),
            EMAIL,
            "bcrypt$2a$10$hash",
            role,
            UserStatus.ACTIVE,
            LocalDateTime.now(),
            LocalDateTime.now()
        );
        for (long i = 1; i < tokenVersion; i++) {
            user.bumpTokenVersion();
        }
        return user;
    }

    private static String paddedHash(String prefix) {
        StringBuilder sb = new StringBuilder(64);
        while (sb.length() < 64) {
            sb.append(prefix);
        }
        sb.setLength(64);
        return sb.toString();
    }

    private RefreshToken activeRefresh(String tokenHash, long tokenVersion, Instant expiresAt) {
        return RefreshToken.rotate(
            UserId.of(USER_ID),
            FAMILY_ID,
            paddedHash(tokenHash),
            tokenVersion,
            expiresAt
        );
    }

    private RefreshToken revokedRefreshStub(String tokenHash, long tokenVersion) {
        RefreshToken token = RefreshToken.rotate(
            UserId.of(USER_ID),
            FAMILY_ID,
            paddedHash(tokenHash),
            tokenVersion,
            Instant.now().plus(REFRESH_TTL)
        );
        token.markRevoked();
        return token;
    }
}
