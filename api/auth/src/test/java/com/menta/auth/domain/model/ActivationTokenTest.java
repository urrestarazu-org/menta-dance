package com.menta.auth.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ActivationTokenTest {

    private static final Instant NOW = Instant.parse("2026-08-11T20:00:00Z");
    private static final UserId USER_ID = UserId.of(
        UUID.fromString("11111111-1111-1111-1111-111111111111")
    );
    private static final String TOKEN_HASH = "a".repeat(64);

    @Test
    void issued_token_is_active_until_expiration() {
        ActivationToken token = ActivationToken.issue(
            USER_ID,
            TOKEN_HASH,
            NOW.plus(Duration.ofHours(24)),
            NOW
        );

        assertThat(token.statusAt(NOW)).isEqualTo(ActivationTokenStatus.ACTIVE);
        assertThat(token.statusAt(NOW.plus(Duration.ofHours(24))))
            .isEqualTo(ActivationTokenStatus.EXPIRED);
    }

    @Test
    void active_token_can_be_consumed_once() {
        ActivationToken token = tokenExpiringIn(Duration.ofHours(1));

        token.consume(NOW.plusSeconds(1));

        assertThat(token.statusAt(NOW.plusSeconds(2))).isEqualTo(ActivationTokenStatus.USED);
        assertThatThrownBy(() -> token.consume(NOW.plusSeconds(3)))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("active");
    }

    @Test
    void expired_token_cannot_be_consumed() {
        ActivationToken token = tokenExpiringIn(Duration.ofSeconds(1));

        assertThatThrownBy(() -> token.consume(NOW.plusSeconds(1)))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("active");
    }

    @Test
    void invalidated_token_cannot_be_consumed() {
        ActivationToken token = tokenExpiringIn(Duration.ofHours(1));

        token.invalidate(NOW.plusSeconds(1));

        assertThat(token.statusAt(NOW.plusSeconds(2)))
            .isEqualTo(ActivationTokenStatus.INVALIDATED);
        assertThatThrownBy(() -> token.consume(NOW.plusSeconds(3)))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("active");
    }

    @Test
    void token_hash_must_be_lowercase_sha256_hex() {
        assertThatThrownBy(() -> ActivationToken.issue(
            USER_ID,
            "not-a-sha256-hash",
            NOW.plusSeconds(1),
            NOW
        ))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("SHA-256");
    }

    @Test
    void to_string_reports_raw_fields_without_deriving_a_live_status() {
        ActivationToken token = tokenExpiringIn(Duration.ofHours(24));

        assertThat(token.toString())
            .contains("id=")
            .contains("userId=" + USER_ID)
            .contains("expiresAt=" + NOW.plus(Duration.ofHours(24)))
            .contains("usedAt=null")
            .contains("invalidatedAt=null")
            .doesNotContain("status=");
    }

    private ActivationToken tokenExpiringIn(Duration duration) {
        return ActivationToken.issue(USER_ID, TOKEN_HASH, NOW.plus(duration), NOW);
    }
}
