package com.menta.bff.infrastructure.adapter;

import com.menta.bff.domain.model.SessionTokens;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("SpringSessionTokenRepository")
class SpringSessionTokenRepositoryTest {

    private static final String SESSION_ATTRIBUTE_KEY = "AUTH_TOKENS";

    @Mock
    private HttpSession httpSession;

    private SpringSessionTokenRepository repository;

    @BeforeEach
    void setUp() {
        repository = new SpringSessionTokenRepository(() -> httpSession);
    }

    @Test
    @DisplayName("should store tokens in HTTP session")
    void shouldStoreTokensInHttpSession() {
        // Given
        SessionTokens tokens = new SessionTokens(
                "access_token",
                "refresh_token",
                Instant.now().plusSeconds(900)
        );

        // When
        repository.store(tokens);

        // Then
        verify(httpSession).setAttribute(SESSION_ATTRIBUTE_KEY, tokens);
    }

    @Test
    @DisplayName("should load tokens from HTTP session")
    void shouldLoadTokensFromHttpSession() {
        // Given
        SessionTokens tokens = new SessionTokens(
                "access_token",
                "refresh_token",
                Instant.now().plusSeconds(900)
        );
        when(httpSession.getAttribute(SESSION_ATTRIBUTE_KEY)).thenReturn(tokens);

        // When
        Optional<SessionTokens> result = repository.load();

        // Then
        assertThat(result).isPresent();
        assertThat(result.get()).isEqualTo(tokens);
    }

    @Test
    @DisplayName("should return empty when no tokens in session")
    void shouldReturnEmptyWhenNoTokensInSession() {
        // Given
        when(httpSession.getAttribute(SESSION_ATTRIBUTE_KEY)).thenReturn(null);

        // When
        Optional<SessionTokens> result = repository.load();

        // Then
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("should clear tokens from HTTP session")
    void shouldClearTokensFromHttpSession() {
        // When
        repository.clear();

        // Then
        verify(httpSession).removeAttribute(SESSION_ATTRIBUTE_KEY);
    }

    @Test
    @DisplayName("should be idempotent when clearing multiple times")
    void shouldBeIdempotentWhenClearingMultipleTimes() {
        // When
        repository.clear();
        repository.clear();
        repository.clear();

        // Then
        verify(httpSession, times(3)).removeAttribute(SESSION_ATTRIBUTE_KEY);
    }

    @Test
    @DisplayName("should throw IllegalStateException when no HTTP session available")
    void shouldThrowIllegalStateExceptionWhenNoHttpSessionAvailable() {
        // Given
        SpringSessionTokenRepository repositoryWithoutSession = new SpringSessionTokenRepository(() -> null);

        SessionTokens tokens = new SessionTokens(
                "access_token",
                "refresh_token",
                Instant.now().plusSeconds(900)
        );

        // When / Then
        assertThatThrownBy(() -> repositoryWithoutSession.store(tokens))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No HTTP session available");

        assertThatThrownBy(() -> repositoryWithoutSession.load())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No HTTP session available");

        assertThatThrownBy(() -> repositoryWithoutSession.clear())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No HTTP session available");
    }

    @Test
    @DisplayName("should overwrite existing tokens when storing")
    void shouldOverwriteExistingTokensWhenStoring() {
        // Given
        SessionTokens oldTokens = new SessionTokens(
                "old_access_token",
                "old_refresh_token",
                Instant.now().plusSeconds(900)
        );

        SessionTokens newTokens = new SessionTokens(
                "new_access_token",
                "new_refresh_token",
                Instant.now().plusSeconds(900)
        );

        // When
        repository.store(oldTokens);
        repository.store(newTokens);

        // Then
        verify(httpSession).setAttribute(SESSION_ATTRIBUTE_KEY, oldTokens);
        verify(httpSession).setAttribute(SESSION_ATTRIBUTE_KEY, newTokens);
    }
}
