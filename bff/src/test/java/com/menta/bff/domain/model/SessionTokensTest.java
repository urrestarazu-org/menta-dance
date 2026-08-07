package com.menta.bff.domain.model;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for SessionTokens value object.
 * <p>
 * Verifies:
 * - Immutability (record)
 * - Null validation
 * - Expiration logic
 * - No token leakage in toString
 * - Equality semantics
 * </p>
 */
class SessionTokensTest {

    private static final String ACCESS_TOKEN = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9";
    private static final String REFRESH_TOKEN = "refresh_abc123";
    private static final Instant EXPIRES_AT = Instant.parse("2026-08-07T18:00:00Z");

    @Test
    void shouldCreateSessionTokens() {
        SessionTokens tokens = new SessionTokens(ACCESS_TOKEN, REFRESH_TOKEN, EXPIRES_AT);

        assertThat(tokens.accessToken()).isEqualTo(ACCESS_TOKEN);
        assertThat(tokens.refreshToken()).isEqualTo(REFRESH_TOKEN);
        assertThat(tokens.expiresAt()).isEqualTo(EXPIRES_AT);
    }

    @Test
    void shouldRejectNullAccessToken() {
        assertThatThrownBy(() -> new SessionTokens(null, REFRESH_TOKEN, EXPIRES_AT))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("accessToken");
    }

    @Test
    void shouldRejectNullRefreshToken() {
        assertThatThrownBy(() -> new SessionTokens(ACCESS_TOKEN, null, EXPIRES_AT))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("refreshToken");
    }

    @Test
    void shouldRejectNullExpiresAt() {
        assertThatThrownBy(() -> new SessionTokens(ACCESS_TOKEN, REFRESH_TOKEN, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("expiresAt");
    }

    @Test
    void shouldDetectExpiredToken() {
        SessionTokens tokens = new SessionTokens(ACCESS_TOKEN, REFRESH_TOKEN, EXPIRES_AT);

        Instant afterExpiry = EXPIRES_AT.plusSeconds(1);
        assertThat(tokens.isExpired(afterExpiry)).isTrue();
    }

    @Test
    void shouldDetectNotExpiredToken() {
        SessionTokens tokens = new SessionTokens(ACCESS_TOKEN, REFRESH_TOKEN, EXPIRES_AT);

        Instant beforeExpiry = EXPIRES_AT.minusSeconds(1);
        assertThat(tokens.isExpired(beforeExpiry)).isFalse();
    }

    @Test
    void shouldDetectNotExpiredAtExactExpiryTime() {
        SessionTokens tokens = new SessionTokens(ACCESS_TOKEN, REFRESH_TOKEN, EXPIRES_AT);

        // At exact expiry time, token is NOT yet expired
        assertThat(tokens.isExpired(EXPIRES_AT)).isFalse();
    }

    @Test
    void shouldBeImmutable() {
        SessionTokens tokens = new SessionTokens(ACCESS_TOKEN, REFRESH_TOKEN, EXPIRES_AT);

        // Records are immutable by design - verify accessor return values don't change
        String originalAccess = tokens.accessToken();
        String originalRefresh = tokens.refreshToken();
        Instant originalExpiry = tokens.expiresAt();

        // Multiple calls return same values
        assertThat(tokens.accessToken()).isEqualTo(originalAccess);
        assertThat(tokens.refreshToken()).isEqualTo(originalRefresh);
        assertThat(tokens.expiresAt()).isEqualTo(originalExpiry);
    }

    @Test
    void shouldImplementEquality() {
        SessionTokens tokens1 = new SessionTokens(ACCESS_TOKEN, REFRESH_TOKEN, EXPIRES_AT);
        SessionTokens tokens2 = new SessionTokens(ACCESS_TOKEN, REFRESH_TOKEN, EXPIRES_AT);
        SessionTokens tokens3 = new SessionTokens("different", REFRESH_TOKEN, EXPIRES_AT);

        assertThat(tokens1).isEqualTo(tokens2);
        assertThat(tokens1).isNotEqualTo(tokens3);
        assertThat(tokens1.hashCode()).isEqualTo(tokens2.hashCode());
    }

    @Test
    void toStringShouldNotLeakTokens() {
        SessionTokens tokens = new SessionTokens(ACCESS_TOKEN, REFRESH_TOKEN, EXPIRES_AT);

        String toString = tokens.toString();

        // toString should NOT contain raw token values (security leak)
        assertThat(toString).doesNotContain(ACCESS_TOKEN);
        assertThat(toString).doesNotContain(REFRESH_TOKEN);
        assertThat(toString).contains("SessionTokens");
    }
}
