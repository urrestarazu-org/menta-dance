package com.menta.bff.application.usecase;

import com.menta.bff.application.port.out.AuthApiClient;
import com.menta.bff.application.port.out.SessionTokenRepository;
import com.menta.bff.domain.model.SessionTokens;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("LogoutUseCase")
class LogoutUseCaseTest {

    @Mock
    private AuthApiClient authApiClient;

    @Mock
    private SessionTokenRepository sessionTokenRepository;

    private LogoutUseCase logoutUseCase;

    @BeforeEach
    void setUp() {
        logoutUseCase = new LogoutUseCaseImpl(authApiClient, sessionTokenRepository);
    }

    @Test
    @DisplayName("should revoke refresh token then clear session when session exists")
    void shouldRevokeRefreshTokenThenClearSession() {
        // Given
        SessionTokens tokens = new SessionTokens(
                "access_token",
                "refresh_token_123",
                Instant.now().plusSeconds(900)
        );
        when(sessionTokenRepository.load()).thenReturn(Optional.of(tokens));

        // When
        logoutUseCase.execute();

        // Then - Verify order: revoke THEN clear
        InOrder inOrder = inOrder(authApiClient, sessionTokenRepository);
        inOrder.verify(sessionTokenRepository).load();
        inOrder.verify(authApiClient).logout("refresh_token_123");
        inOrder.verify(sessionTokenRepository).clear();
    }

    @Test
    @DisplayName("should only clear local session when refresh token is missing")
    void shouldOnlyClearLocalSessionWhenRefreshTokenMissing() {
        // Given
        when(sessionTokenRepository.load()).thenReturn(Optional.empty());

        // When
        logoutUseCase.execute();

        // Then - Verify Auth API NOT called, only local clear
        verify(authApiClient, never()).logout(anyString());
        verify(sessionTokenRepository).clear();
    }

    @Test
    @DisplayName("should clear local session even when Auth API revocation fails (fail-open for logout)")
    void shouldClearLocalSessionEvenWhenAuthApiRevocationFails() {
        // Given
        SessionTokens tokens = new SessionTokens(
                "access_token",
                "refresh_token_123",
                Instant.now().plusSeconds(900)
        );
        when(sessionTokenRepository.load()).thenReturn(Optional.of(tokens));
        doThrow(new AuthApiClient.ServiceUnavailableException("Auth API unreachable"))
                .when(authApiClient).logout("refresh_token_123");

        // When / Then - Should NOT throw, continues to clear local session
        assertThatCode(() -> logoutUseCase.execute())
                .doesNotThrowAnyException();

        // Verify local session still cleared despite Auth API failure
        verify(sessionTokenRepository).clear();
    }

    @Test
    @DisplayName("should be idempotent - multiple logout calls are safe")
    void shouldBeIdempotent() {
        // Given
        when(sessionTokenRepository.load()).thenReturn(Optional.empty());

        // When
        logoutUseCase.execute();
        logoutUseCase.execute();
        logoutUseCase.execute();

        // Then - No exceptions, clear called multiple times (idempotent)
        verify(sessionTokenRepository, times(3)).clear();
        verify(authApiClient, never()).logout(anyString());
    }

    @Test
    @DisplayName("should handle Auth API returning 423 (token family revoked)")
    void shouldHandleAuthApiReturning423() {
        // Given
        SessionTokens tokens = new SessionTokens(
                "access_token",
                "refresh_token_123",
                Instant.now().plusSeconds(900)
        );
        when(sessionTokenRepository.load()).thenReturn(Optional.of(tokens));
        doThrow(new AuthApiClient.RefreshTokenRevokedException("Token family revoked"))
                .when(authApiClient).logout("refresh_token_123");

        // When / Then - Should NOT throw, fail-open for logout
        assertThatCode(() -> logoutUseCase.execute())
                .doesNotThrowAnyException();

        // Verify local session still cleared
        verify(sessionTokenRepository).clear();
    }

    @Test
    @DisplayName("should clear session even when revocation throws AuthenticationException")
    void shouldClearSessionEvenWhenRevocationThrowsAuthenticationException() {
        // Given
        SessionTokens tokens = new SessionTokens(
                "access_token",
                "refresh_token_123",
                Instant.now().plusSeconds(900)
        );
        when(sessionTokenRepository.load()).thenReturn(Optional.of(tokens));
        doThrow(new AuthApiClient.AuthenticationException("Invalid refresh token"))
                .when(authApiClient).logout("refresh_token_123");

        // When / Then - Should NOT throw
        assertThatCode(() -> logoutUseCase.execute())
                .doesNotThrowAnyException();

        // Verify local session cleared (fail-open)
        verify(sessionTokenRepository).clear();
    }
}
