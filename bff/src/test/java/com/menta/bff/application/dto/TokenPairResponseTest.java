package com.menta.bff.application.dto;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for TokenPairResponse DTO.
 */
class TokenPairResponseTest {

    private static final String ACCESS_TOKEN = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9";
    private static final String REFRESH_TOKEN = "refresh_abc123";

    @Test
    void shouldCreateTokenPairResponse() {
        TokenPairResponse response = new TokenPairResponse(ACCESS_TOKEN, REFRESH_TOKEN, 900);

        assertThat(response.accessToken()).isEqualTo(ACCESS_TOKEN);
        assertThat(response.refreshToken()).isEqualTo(REFRESH_TOKEN);
        assertThat(response.expiresIn()).isEqualTo(900);
    }

    @Test
    void shouldRejectNullAccessToken() {
        assertThatThrownBy(() -> new TokenPairResponse(null, REFRESH_TOKEN, 900))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("accessToken");
    }

    @Test
    void shouldRejectNullRefreshToken() {
        assertThatThrownBy(() -> new TokenPairResponse(ACCESS_TOKEN, null, 900))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("refreshToken");
    }

    @Test
    void shouldRejectZeroExpiresIn() {
        assertThatThrownBy(() -> new TokenPairResponse(ACCESS_TOKEN, REFRESH_TOKEN, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("expiresIn must be positive");
    }

    @Test
    void shouldRejectNegativeExpiresIn() {
        assertThatThrownBy(() -> new TokenPairResponse(ACCESS_TOKEN, REFRESH_TOKEN, -100))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("expiresIn must be positive");
    }

    @Test
    void toStringShouldNotLeakTokens() {
        TokenPairResponse response = new TokenPairResponse(ACCESS_TOKEN, REFRESH_TOKEN, 900);

        String toString = response.toString();

        assertThat(toString).doesNotContain(ACCESS_TOKEN);
        assertThat(toString).doesNotContain(REFRESH_TOKEN);
        assertThat(toString).contains("900");
    }
}
