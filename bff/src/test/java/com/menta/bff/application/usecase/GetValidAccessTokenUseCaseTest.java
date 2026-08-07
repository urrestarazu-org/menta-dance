package com.menta.bff.application.usecase;

import com.menta.bff.application.dto.TokenPairResponse;
import com.menta.bff.application.port.out.AuthApiClient;
import com.menta.bff.application.port.out.SessionTokenRepository;
import com.menta.bff.domain.model.SessionTokens;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("GetValidAccessTokenUseCase")
class GetValidAccessTokenUseCaseTest {

    @Mock
    private AuthApiClient authApiClient;

    @Mock
    private SessionTokenRepository sessionTokenRepository;

    private GetValidAccessTokenUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new GetValidAccessTokenUseCaseImpl(authApiClient, sessionTokenRepository);
    }

    @Test
    @DisplayName("should return access token when not expired")
    void shouldReturnAccessTokenWhenNotExpired() {
        // Given
        SessionTokens tokens = new SessionTokens(
                "valid_access_token",
                "refresh_token",
                Instant.now().plusSeconds(900) // Expires in 15 minutes
        );
        when(sessionTokenRepository.load()).thenReturn(Optional.of(tokens));

        // When
        String result = useCase.execute();

        // Then
        assertThat(result).isEqualTo("valid_access_token");
        verify(authApiClient, never()).refresh(anyString());
    }

    @Test
    @DisplayName("should refresh token and return new access token when expired")
    void shouldRefreshTokenAndReturnNewAccessTokenWhenExpired() {
        // Given - Expired access token
        SessionTokens expiredTokens = new SessionTokens(
                "expired_access_token",
                "refresh_token_123",
                Instant.now().minusSeconds(60) // Expired 1 minute ago
        );
        when(sessionTokenRepository.load()).thenReturn(Optional.of(expiredTokens));

        // Mock refresh response
        TokenPairResponse refreshResponse = new TokenPairResponse(
                "new_access_token",
                "new_refresh_token",
                900L
        );
        when(authApiClient.refresh("refresh_token_123")).thenReturn(refreshResponse);

        // When
        String result = useCase.execute();

        // Then
        assertThat(result).isEqualTo("new_access_token");

        // Verify tokens updated in session
        ArgumentCaptor<SessionTokens> tokensCaptor = ArgumentCaptor.forClass(SessionTokens.class);
        verify(sessionTokenRepository).store(tokensCaptor.capture());

        SessionTokens updatedTokens = tokensCaptor.getValue();
        assertThat(updatedTokens.accessToken()).isEqualTo("new_access_token");
        assertThat(updatedTokens.refreshToken()).isEqualTo("new_refresh_token");
        assertThat(updatedTokens.expiresAt()).isAfter(Instant.now());
    }

    @Test
    @DisplayName("should throw SessionNotFoundException when no session exists")
    void shouldThrowSessionNotFoundExceptionWhenNoSessionExists() {
        // Given
        when(sessionTokenRepository.load()).thenReturn(Optional.empty());

        // When / Then
        assertThatThrownBy(() -> useCase.execute())
                .isInstanceOf(GetValidAccessTokenUseCase.SessionNotFoundException.class)
                .hasMessageContaining("No active session");
    }

    @Test
    @DisplayName("should throw AuthenticationException when refresh fails with 401")
    void shouldThrowAuthenticationExceptionWhenRefreshFails() {
        // Given - Expired access token
        SessionTokens expiredTokens = new SessionTokens(
                "expired_access_token",
                "invalid_refresh_token",
                Instant.now().minusSeconds(60)
        );
        when(sessionTokenRepository.load()).thenReturn(Optional.of(expiredTokens));
        when(authApiClient.refresh("invalid_refresh_token"))
                .thenThrow(new AuthApiClient.AuthenticationException("Invalid refresh token"));

        // When / Then
        assertThatThrownBy(() -> useCase.execute())
                .isInstanceOf(AuthApiClient.AuthenticationException.class)
                .hasMessageContaining("Invalid refresh token");
    }

    @Test
    @DisplayName("should throw RefreshTokenRevokedException when token family revoked")
    void shouldThrowRefreshTokenRevokedExceptionWhenTokenFamilyRevoked() {
        // Given - Expired access token
        SessionTokens expiredTokens = new SessionTokens(
                "expired_access_token",
                "revoked_refresh_token",
                Instant.now().minusSeconds(60)
        );
        when(sessionTokenRepository.load()).thenReturn(Optional.of(expiredTokens));
        when(authApiClient.refresh("revoked_refresh_token"))
                .thenThrow(new AuthApiClient.RefreshTokenRevokedException("Token family revoked"));

        // When / Then
        assertThatThrownBy(() -> useCase.execute())
                .isInstanceOf(AuthApiClient.RefreshTokenRevokedException.class)
                .hasMessageContaining("Token family revoked");

        // Verify session cleared (fail-closed for security)
        verify(sessionTokenRepository).clear();
    }

    @Test
    @DisplayName("should handle token expiring at exact current time (boundary condition)")
    void shouldHandleTokenExpiringAtExactCurrentTime() {
        // Given - Token expires NOW (boundary condition)
        Instant now = Instant.now();
        SessionTokens tokensExpiringNow = new SessionTokens(
                "access_token",
                "refresh_token",
                now
        );
        when(sessionTokenRepository.load()).thenReturn(Optional.of(tokensExpiringNow));

        TokenPairResponse refreshResponse = new TokenPairResponse(
                "new_access_token",
                "new_refresh_token",
                900L
        );
        when(authApiClient.refresh("refresh_token")).thenReturn(refreshResponse);

        // When
        String result = useCase.execute();

        // Then - Should refresh (isAfter boundary)
        assertThat(result).isEqualTo("new_access_token");
        verify(authApiClient).refresh("refresh_token");
    }

    @Test
    @DisplayName("should NOT refresh when token expires in 1 second (still valid)")
    void shouldNotRefreshWhenTokenExpiresInOneSecond() {
        // Given - Token expires in 1 second (still valid)
        SessionTokens tokensExpiringSoon = new SessionTokens(
                "access_token",
                "refresh_token",
                Instant.now().plusSeconds(1)
        );
        when(sessionTokenRepository.load()).thenReturn(Optional.of(tokensExpiringSoon));

        // When
        String result = useCase.execute();

        // Then - Should NOT refresh
        assertThat(result).isEqualTo("access_token");
        verify(authApiClient, never()).refresh(anyString());
    }
}
