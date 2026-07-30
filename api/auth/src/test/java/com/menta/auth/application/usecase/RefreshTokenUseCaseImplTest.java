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
import com.menta.auth.application.dto.RefreshCommand;
import com.menta.auth.application.dto.TokenPair;
import com.menta.auth.application.port.out.AccessTokenIssuer;
import com.menta.auth.application.port.out.AuthDegradedGuard;
import com.menta.auth.application.port.out.IssuedAccessToken;
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
 * RED-GREEN discipline: this test references RefreshTokenUseCaseImpl BEFORE
 * it exists, so the file must not compile until 2.4 GREEN provides the impl.
 *
 * Covers auth-refresh spec scenarios:
 *   - "Rotación exitosa emite par nuevo y conserva familia"
 *   - "Refresh USED dispara revocación de familia"
 *   - "Refresh con más de 7 días se rechaza"
 *   - "Refresh con tokenVersion viejo dispara familia revocada"
 *   - "Refresh REVOKED se rechaza inmutablemente"
 *
 * Also covers auth-login spec scenario "Refresh ya rotado activa
 * revocación de familia" via the family-revoke outbox event.
 *
 * Pure Mockito — no Spring context, no DB.
 */
@ExtendWith(MockitoExtension.class)
class RefreshTokenUseCaseImplTest {

    private static final Duration ACCESS_TTL = Duration.ofMinutes(15);
    private static final Duration REFRESH_TTL = Duration.ofDays(7);
    private static final UUID USER_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final UUID FAMILY_ID = UUID.fromString("44444444-4444-4444-4444-444444444444");
    private static final UUID JTI = UUID.fromString("55555555-5555-5555-5555-555555555555");
    private static final long TOKEN_VERSION = 1L;
    private static final Email EMAIL = Email.of("alice@example.com");

    @Mock private UserRepository userRepository;
    @Mock private RefreshTokenRepository refreshTokenRepository;
    @Mock private AccessTokenIssuer accessTokenIssuer;
    @Mock private TokenHasher tokenHasher;
    @Mock private OutboxAppender outboxAppender;
    @Mock private AuthDegradedGuard authDegradedGuard;

    private RefreshTokenUseCaseImpl useCase;

@BeforeEach
        void setUp() {
            useCase = new RefreshTokenUseCaseImpl(
                userRepository,
                refreshTokenRepository,
                accessTokenIssuer,
                tokenHasher,
                outboxAppender,
                authDegradedGuard
            );
        }

    @Nested
    @DisplayName("Spec: Rotación exitosa emite par nuevo y conserva familia")
    class SuccessfulRotationScenario {

        @Test
        void rotates_active_refresh_with_matching_token_version() {
            String raw = UUID.randomUUID().toString();
            String presentedHash = paddedHash("presented-hash-");
            UUID newRefreshId = UUID.fromString("77777777-7777-7777-7777-777777777777");
            String newRefreshHash = paddedHash("new-refresh-hash");

            RefreshToken presented = activeRefresh(
                UUID.fromString("66666666-6666-6666-6666-666666666666"),
                presentedHash,
                TOKEN_VERSION,
                Instant.now().plus(REFRESH_TTL)
            );
            User user = activeUser(USER_ID, Role.STUDENT, TOKEN_VERSION);
            IssuedAccessToken access = new IssuedAccessToken("jwt.compact", JTI.toString(), ACCESS_TTL);

            when(tokenHasher.hash(anyString())).thenReturn(presentedHash).thenReturn(newRefreshHash);
            when(refreshTokenRepository.findByHash(anyString())).thenReturn(Optional.of(presented));
            when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
            when(accessTokenIssuer.issue(user)).thenReturn(access);
            when(refreshTokenRepository.save(any(RefreshToken.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

            TokenPair pair = useCase.execute(new RefreshCommand(raw));

            assertThat(pair.accessToken()).isEqualTo("jwt.compact");
            // The new refresh id MUST be a non-empty UUID-shaped string;
            // its concrete value comes from UUID.randomUUID() inside the impl.
            assertThat(pair.refreshToken()).isNotBlank();
            assertThat(pair.refreshToken()).hasSize(36);
            assertThat(pair.tokenType()).isEqualTo("Bearer");

            // Old refresh marked USED in the same transaction.
            assertThat(presented.getStatus()).isEqualTo(RefreshTokenStatus.USED);
            assertThat(presented.getRotatedAt()).isNotNull();

            // PR3 fix: persist the USED transition on the rotated parent
            // refresh token. Without this, the row stays ACTIVE and the
            // same refresh could be presented again. See LogoutUseCaseImpl
            // for the matching fix and the integration test that surfaced it.
            verify(refreshTokenRepository).save(presented);

            // New refresh persisted, same family, status=ACTIVE.
            verify(refreshTokenRepository, times(2)).save(any(RefreshToken.class));
            verify(outboxAppender).append(
                eq(AuthOutboxEventTypes.REFRESH_ROTATED),
                anyString(),
                anyString()
            );
        }
    }

    @Nested
    @DisplayName("Spec: Refresh USED dispara revocación de familia")
    class UsedRefreshTriggersFamilyRevocation {

        @Test
        void bumps_token_version_and_revokes_family() {
            String raw = UUID.randomUUID().toString();
            String presentedHash = paddedHash("used-hash-");
            RefreshToken presented = usedRefresh(
                UUID.fromString("68686868-6868-6868-6868-686868686868"),
                presentedHash,
                TOKEN_VERSION
            );
            User user = activeUser(USER_ID, Role.STUDENT, TOKEN_VERSION + 1);

            when(tokenHasher.hash(anyString())).thenReturn(presentedHash);
            when(refreshTokenRepository.findByHash(anyString())).thenReturn(Optional.of(presented));
            when(userRepository.findById(any())).thenReturn(Optional.of(user));
            when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

            assertThatThrownBy(() -> useCase.execute(new RefreshCommand(raw)))
                .isInstanceOf(RefreshTokenCompromisedException.class);

            // User.bumpTokenVersion() invoked (use case persists the bumped user).
            assertThat(user.getTokenVersion()).isGreaterThan(TOKEN_VERSION);
            verify(userRepository).save(any(User.class));
            // Family revoked atomically.
            verify(refreshTokenRepository).revokeFamily(FAMILY_ID);
            // RefreshRevoked outbox event.
            verify(outboxAppender).append(
                eq(AuthOutboxEventTypes.REFRESH_REVOKED),
                anyString(),
                anyString()
            );
            // Never re-issued tokens.
            verify(accessTokenIssuer, never()).issue(any());
            verify(refreshTokenRepository, never()).save(any(RefreshToken.class));
        }
    }

    @Nested
    @DisplayName("Spec: Refresh con más de 7 días se rechaza")
    class ExpiredRefreshRejected {

        @Test
        void expired_presentation_is_rejected_without_state_change() {
            String raw = UUID.randomUUID().toString();
            String presentedHash = paddedHash("expired-hash");
            RefreshToken presented = activeRefresh(
                UUID.fromString("69696969-6969-6969-6969-696969696969"),
                presentedHash,
                TOKEN_VERSION,
                Instant.now().minus(Duration.ofDays(8))
            );

            when(tokenHasher.hash(anyString())).thenReturn(presentedHash);
            when(refreshTokenRepository.findByHash(anyString())).thenReturn(Optional.of(presented));

            assertThatThrownBy(() -> useCase.execute(new RefreshCommand(raw)))
                .isInstanceOf(RefreshTokenCompromisedException.class);

            // No state mutation: refresh stays ACTIVE in storage.
            assertThat(presented.getStatus()).isEqualTo(RefreshTokenStatus.ACTIVE);
            verify(userRepository, never()).save(any(User.class));
            verify(refreshTokenRepository, never()).save(any(RefreshToken.class));
            verify(refreshTokenRepository, never()).revokeFamily(any());
            verify(outboxAppender, never()).append(anyString(), anyString(), anyString());
        }
    }

    @Nested
    @DisplayName("Spec: Refresh con tokenVersion viejo dispara familia revocada")
    class StaleTokenVersionTriggersFamilyRevocation {

        @Test
        void mismatched_token_version_revokes_family_and_keeps_users_version() {
            String raw = UUID.randomUUID().toString();
            String presentedHash = paddedHash("stale-tv-hash");
            RefreshToken presented = activeRefresh(
                UUID.fromString("71717171-7171-7171-7171-717171717171"),
                presentedHash,
                /* presented.tokenVersion = */ 1L,
                Instant.now().plus(REFRESH_TTL)
            );
            User user = activeUser(USER_ID, Role.STUDENT, /* users.tokenVersion */ 2L);

            when(tokenHasher.hash(anyString())).thenReturn(presentedHash);
            when(refreshTokenRepository.findByHash(anyString())).thenReturn(Optional.of(presented));
            when(userRepository.findById(presented.getUserId())).thenReturn(Optional.of(user));

            assertThatThrownBy(() -> useCase.execute(new RefreshCommand(raw)))
                .isInstanceOf(RefreshTokenCompromisedException.class);

            // User token_version is NOT decremented (spec: "ya estaba en 2").
            assertThat(user.getTokenVersion()).isEqualTo(2L);
            verify(userRepository, never()).save(any(User.class));
            verify(refreshTokenRepository).revokeFamily(FAMILY_ID);
            verify(outboxAppender).append(
                eq(AuthOutboxEventTypes.REFRESH_REVOKED),
                anyString(),
                anyString()
            );
        }
    }

    @Nested
    @DisplayName("Spec: Refresh REVOKED se rechaza inmutablemente")
    class RevokedRefreshIsImmutable {

        @Test
        void revoked_presentation_is_refused_and_no_event_is_appended() {
            String raw = UUID.randomUUID().toString();
            String presentedHash = paddedHash("revoked-hash");
            RefreshToken presented = revokedRefresh(
                UUID.fromString("72727272-7272-7272-7272-727272727272"),
                presentedHash,
                TOKEN_VERSION
            );

            when(tokenHasher.hash(anyString())).thenReturn(presentedHash);
            when(refreshTokenRepository.findByHash(anyString())).thenReturn(Optional.of(presented));

            assertThatThrownBy(() -> useCase.execute(new RefreshCommand(raw)))
                .isInstanceOf(RefreshTokenCompromisedException.class);

            // Immutable — refresh stays REVOKED in storage.
            assertThat(presented.getStatus()).isEqualTo(RefreshTokenStatus.REVOKED);
            verify(refreshTokenRepository, never()).revokeFamily(any());
            verify(userRepository, never()).save(any(User.class));
            // No event: spec: "no se publica ningún evento adicional".
            verify(outboxAppender, never()).append(anyString(), anyString(), anyString());
            verify(accessTokenIssuer, never()).issue(any());
        }
    }

    @Nested
    @DisplayName("Spec: Reconciliador atrasado bloquea refresh")
    class DegradedReconcilerBlocksRefresh {

        @Test
        void degraded_guard_blocks_refresh_even_with_valid_token() {
            when(authDegradedGuard.isDegraded()).thenReturn(true);

            assertThatThrownBy(() ->
                useCase.execute(new RefreshCommand("any-raw-token"))
            ).isInstanceOf(AuthDegradedException.class);

            verify(refreshTokenRepository, never()).findByHash(anyString());
            verify(accessTokenIssuer, never()).issue(any());
            verify(outboxAppender, never()).append(anyString(), anyString(), anyString());
        }

        @Test
        void unimplemented_unknown_hash_does_not_consume_outbox() {
            // Spec mentions "Cuerpo igual al refresh comprometido". Unknown
            // hash is treated strictly as compromise: revoke family of the
            // token we do know about, but here we don't have any matched
            // family. The use case MUST NOT throw an exception that proves
            // email existence — treat all unknown hashes as compromised.
            when(tokenHasher.hash(anyString())).thenReturn("unknown-hash");
            when(refreshTokenRepository.findByHash("unknown-hash")).thenReturn(Optional.empty());

            assertThatThrownBy(() ->
                useCase.execute(new RefreshCommand("any-raw"))
            ).isInstanceOf(RefreshTokenCompromisedException.class);

            verify(outboxAppender, never()).append(anyString(), anyString(), anyString());
            verify(accessTokenIssuer, never()).issue(any());
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
        // The 7-arg constructor defaults tokenVersion to 1; bump to the
        // version the test wants before returning.
        for (long i = 1; i < tokenVersion; i++) {
            user.bumpTokenVersion();
        }
        return user;
    }

    /** Pad any short prefix to exactly 64 chars (SHA-256 hex length). */
    private static String paddedHash(String prefix) {
        StringBuilder sb = new StringBuilder(64);
        while (sb.length() < 64) {
            sb.append(prefix);
        }
        sb.setLength(64);
        return sb.toString();
    }

    private RefreshToken activeRefresh(UUID ignoredId, String tokenHash, long tokenVersion, Instant expiresAt) {
        return RefreshToken.rotate(
            UserId.of(USER_ID),
            FAMILY_ID,
            paddedHash(tokenHash),
            tokenVersion,
            expiresAt
        );
    }

    private RefreshToken usedRefresh(UUID ignoredId, String tokenHash, long tokenVersion) {
        RefreshToken token = RefreshToken.rotate(
            UserId.of(USER_ID),
            FAMILY_ID,
            paddedHash(tokenHash),
            tokenVersion,
            Instant.now().plus(REFRESH_TTL)
        );
        token.markUsed();
        return token;
    }

    private RefreshToken revokedRefresh(UUID ignoredId, String tokenHash, long tokenVersion) {
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
