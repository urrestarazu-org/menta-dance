package com.menta.auth.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Single-use password reset credential (US-AUTH-005 / US-AUTH-006).
 *
 * <p>Mirrors {@link ActivationToken}: the raw secret never enters the aggregate,
 * only its digest. Unlike activation, the distinct terminal states matter to the
 * caller — expired and already-used map to different HTTP responses so the user
 * gets told to request a new link instead of a generic failure.</p>
 */
class PasswordResetTokenTest {

    private static final UserId USER_ID = UserId.of(UUID.randomUUID());
    private static final String TOKEN_HASH = "a".repeat(64);
    private static final Instant NOW = Instant.parse("2026-08-16T12:00:00Z");
    private static final Instant EXPIRES_AT = NOW.plus(Duration.ofHours(1));

    private static PasswordResetToken activeToken() {
        return PasswordResetToken.issue(USER_ID, TOKEN_HASH, EXPIRES_AT, NOW);
    }

    @Nested
    @DisplayName("Emisión")
    class Issue {

        @Test
        void issues_an_active_token() {
            assertThat(activeToken().statusAt(NOW)).isEqualTo(PasswordResetTokenStatus.ACTIVE);
        }

        @Test
        void rejects_a_hash_that_is_not_sha256_hex() {
            // The aggregate must never hold a raw token by accident. Enforcing
            // the digest shape is what makes that mistake impossible.
            assertThatThrownBy(() ->
                PasswordResetToken.issue(USER_ID, "not-a-digest", EXPIRES_AT, NOW))
                .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void rejects_an_expiry_that_is_not_after_creation() {
            assertThatThrownBy(() ->
                PasswordResetToken.issue(USER_ID, TOKEN_HASH, NOW, NOW))
                .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("Estados terminales")
    class TerminalStates {

        @Test
        void becomes_used_once_consumed() {
            PasswordResetToken token = activeToken();

            token.consume(NOW);

            assertThat(token.statusAt(NOW)).isEqualTo(PasswordResetTokenStatus.USED);
        }

        @Test
        void becomes_expired_once_the_hour_passes() {
            // US-AUTH-006 escenario 2: expira a la hora.
            assertThat(activeToken().statusAt(EXPIRES_AT))
                .isEqualTo(PasswordResetTokenStatus.EXPIRED);
        }

        @Test
        void becomes_invalidated_when_superseded() {
            // US-AUTH-005 escenario 3: pedir un reset nuevo invalida los anteriores.
            PasswordResetToken token = activeToken();

            token.invalidate(NOW);

            assertThat(token.statusAt(NOW)).isEqualTo(PasswordResetTokenStatus.INVALIDATED);
        }

        @Test
        void expiry_is_exclusive_at_the_boundary() {
            assertThat(activeToken().statusAt(EXPIRES_AT.minusMillis(1)))
                .isEqualTo(PasswordResetTokenStatus.ACTIVE);
        }

        @Test
        void a_used_token_cannot_be_consumed_again() {
            // Single use is the whole point: replaying the link must not work.
            PasswordResetToken token = activeToken();
            token.consume(NOW);

            assertThatThrownBy(() -> token.consume(NOW)).isInstanceOf(IllegalStateException.class);
        }

        @Test
        void an_expired_token_cannot_be_consumed() {
            assertThatThrownBy(() -> activeToken().consume(EXPIRES_AT))
                .isInstanceOf(IllegalStateException.class);
        }

        @Test
        void an_invalidated_token_cannot_be_consumed() {
            PasswordResetToken token = activeToken();
            token.invalidate(NOW);

            assertThatThrownBy(() -> token.consume(NOW)).isInstanceOf(IllegalStateException.class);
        }

        @Test
        void a_token_cannot_be_both_used_and_invalidated() {
            assertThatThrownBy(() -> PasswordResetToken.reconstitute(
                UUID.randomUUID(), USER_ID, TOKEN_HASH, EXPIRES_AT, NOW, NOW, NOW))
                .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("Custodia del secreto")
    class Custody {

        @Test
        void never_renders_the_hash_in_toString() {
            // Even the digest is sensitive: it is the lookup key for the reset.
            assertThat(activeToken().toString()).doesNotContain(TOKEN_HASH);
        }
    }
}
