package com.menta.bff.application.usecase;

import com.menta.bff.application.dto.LoginCommand;
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

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("LoginUseCase")
class LoginUseCaseTest {

    private static final String CLIENT_ADDRESS = "203.0.113.9";

    @Mock
    private AuthApiClient authApiClient;

    @Mock
    private SessionTokenRepository sessionTokenRepository;

    private LoginUseCase loginUseCase;

    @BeforeEach
    void setUp() {
        loginUseCase = new LoginUseCaseImpl(authApiClient, sessionTokenRepository);
    }

    @Test
    @DisplayName("should store tokens in session when credentials are valid")
    void shouldStoreTokensInSessionWhenCredentialsValid() {
        // Given
        String email = "user@example.com";
        String password = "ValidPass123!";
        LoginCommand command = new LoginCommand(email, password, CLIENT_ADDRESS);

        String accessToken = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...";
        String refreshToken = "refresh_abc123";
        long expiresIn = 900L; // 15 minutes

        TokenPairResponse apiResponse = new TokenPairResponse(
                accessToken,
                refreshToken,
                expiresIn
        );

        when(authApiClient.login(command)).thenReturn(apiResponse);

        // When
        loginUseCase.execute(command);

        // Then - Verify tokens stored in session
        ArgumentCaptor<SessionTokens> tokensCaptor = ArgumentCaptor.forClass(SessionTokens.class);
        verify(sessionTokenRepository).store(tokensCaptor.capture());

        SessionTokens storedTokens = tokensCaptor.getValue();
        assertThat(storedTokens.accessToken()).isEqualTo(accessToken);
        assertThat(storedTokens.refreshToken()).isEqualTo(refreshToken);
        assertThat(storedTokens.expiresAt()).isAfter(Instant.now());
        assertThat(storedTokens.expiresAt()).isBefore(Instant.now().plus(Duration.ofSeconds(expiresIn + 10)));
    }

    @Test
    @DisplayName("should throw AuthenticationException when credentials are invalid")
    void shouldThrowAuthenticationExceptionWhenCredentialsInvalid() {
        // Given
        LoginCommand command = new LoginCommand("user@example.com", "WrongPassword", CLIENT_ADDRESS);

        when(authApiClient.login(command))
                .thenThrow(new AuthApiClient.AuthenticationException("Invalid credentials"));

        // When / Then
        assertThatThrownBy(() -> loginUseCase.execute(command))
                .isInstanceOf(AuthApiClient.AuthenticationException.class)
                .hasMessageContaining("Invalid credentials");
    }

    @Test
    @DisplayName("should throw ServiceUnavailableException when Auth API is unavailable")
    void shouldThrowServiceUnavailableExceptionWhenAuthApiUnavailable() {
        // Given
        LoginCommand command = new LoginCommand("user@example.com", "ValidPass123!", CLIENT_ADDRESS);

        when(authApiClient.login(command))
                .thenThrow(new AuthApiClient.ServiceUnavailableException("Auth API unreachable"));

        // When / Then
        assertThatThrownBy(() -> loginUseCase.execute(command))
                .isInstanceOf(AuthApiClient.ServiceUnavailableException.class)
                .hasMessageContaining("Auth API unreachable");
    }

    @Test
    @DisplayName("should throw NullPointerException when LoginCommand is null")
    void shouldThrowNullPointerExceptionWhenCommandIsNull() {
        // When / Then
        assertThatThrownBy(() -> loginUseCase.execute(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("should calculate expiresAt based on expiresIn from API response")
    void shouldCalculateExpiresAtBasedOnExpiresIn() {
        // Given
        LoginCommand command = new LoginCommand("user@example.com", "ValidPass123!", CLIENT_ADDRESS);
        long expiresIn = 900L;
        Instant beforeCall = Instant.now();

        TokenPairResponse apiResponse = new TokenPairResponse(
                "access_token",
                "refresh_token",
                expiresIn
        );

        when(authApiClient.login(command)).thenReturn(apiResponse);

        // When
        loginUseCase.execute(command);

        // Then
        ArgumentCaptor<SessionTokens> tokensCaptor = ArgumentCaptor.forClass(SessionTokens.class);
        verify(sessionTokenRepository).store(tokensCaptor.capture());

        SessionTokens storedTokens = tokensCaptor.getValue();
        Instant afterCall = Instant.now();

        // ExpiresAt should be approximately now + expiresIn seconds
        Instant expectedExpiresAt = beforeCall.plusSeconds(expiresIn);
        assertThat(storedTokens.expiresAt())
                .isAfterOrEqualTo(expectedExpiresAt)
                .isBefore(afterCall.plusSeconds(expiresIn + 5)); // Allow 5 sec tolerance
    }
}
