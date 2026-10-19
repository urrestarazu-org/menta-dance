package com.menta.bff.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.menta.bff.application.port.out.AuthApiClient;
import com.menta.bff.application.port.out.SessionTokenRepository;
import com.menta.bff.domain.model.SessionTokens;
import java.time.Instant;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("LogoutUseCase")
class LogoutUseCaseTest {

    private static final String ACCESS_TOKEN = "access_token_abc";
    private static final String REFRESH_TOKEN = "refresh_token_123";
    private static final String NEW_ACCESS_TOKEN = "new_access_token_def";
    private static final String NEW_REFRESH_TOKEN = "new_refresh_token_456";

    @Mock
    private AuthApiClient authApiClient;

    @Mock
    private SessionTokenRepository sessionTokenRepository;

    @Mock
    private GetValidAccessTokenUseCase getValidAccessTokenUseCase;

    private LogoutUseCase logoutUseCase;

    @BeforeEach
    void setUp() {
        logoutUseCase = new LogoutUseCaseImpl(authApiClient, sessionTokenRepository, getValidAccessTokenUseCase);
    }

    private static SessionTokens validSession() {
        return new SessionTokens(ACCESS_TOKEN, REFRESH_TOKEN, Instant.now().plusSeconds(900));
    }

    static Stream<RuntimeException> refreshFailures() {
        return Stream.of(
                new AuthApiClient.AuthenticationException("Invalid refresh token"),
                new AuthApiClient.RefreshTokenRevokedException("Token family revoked"),
                new AuthApiClient.ServiceUnavailableException("Auth API unreachable"),
                new GetValidAccessTokenUseCase.SessionNotFoundException("No active session"));
    }

    @Test
    @DisplayName("revoca con el access token vigente y el refresh token guardado, y limpia la sesión")
    void revokesWithCurrentAccessTokenAndStoredRefreshTokenThenClearsSession() {
        // Given
        when(sessionTokenRepository.load()).thenReturn(Optional.of(validSession()));
        when(getValidAccessTokenUseCase.execute()).thenReturn(ACCESS_TOKEN);

        // When
        LogoutOutcome outcome = logoutUseCase.execute();

        // Then - revoke THEN clear
        assertThat(outcome).isEqualTo(LogoutOutcome.REVOKED);
        InOrder inOrder = inOrder(getValidAccessTokenUseCase, authApiClient, sessionTokenRepository);
        inOrder.verify(getValidAccessTokenUseCase).execute();
        inOrder.verify(authApiClient).logout(ACCESS_TOKEN, REFRESH_TOKEN);
        inOrder.verify(sessionTokenRepository).clear();
    }

    @Test
    @DisplayName("con el access token vencido refresca primero y revoca con el access y refresh token rotados")
    void refreshesFirstAndRevokesWithRotatedTokensWhenAccessTokenExpired() {
        // Given - the first load sees the expired pair, the reload sees what the refresh stored
        SessionTokens expired = new SessionTokens(ACCESS_TOKEN, REFRESH_TOKEN, Instant.now().minusSeconds(60));
        SessionTokens rotated =
                new SessionTokens(NEW_ACCESS_TOKEN, NEW_REFRESH_TOKEN, Instant.now().plusSeconds(900));
        when(sessionTokenRepository.load()).thenReturn(Optional.of(expired)).thenReturn(Optional.of(rotated));
        when(getValidAccessTokenUseCase.execute()).thenReturn(NEW_ACCESS_TOKEN);

        // When
        LogoutOutcome outcome = logoutUseCase.execute();

        // Then
        assertThat(outcome).isEqualTo(LogoutOutcome.REVOKED);
        verify(authApiClient).logout(NEW_ACCESS_TOKEN, NEW_REFRESH_TOKEN);
        verify(authApiClient, never()).logout(ACCESS_TOKEN, REFRESH_TOKEN);
        verify(sessionTokenRepository).clear();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("refreshFailures")
    @DisplayName("si no se puede obtener un access token válido no llama al logout y limpia la sesión")
    void doesNotCallLogoutAndStillClearsSessionWhenNoValidAccessToken(RuntimeException failure) {
        // Given
        when(sessionTokenRepository.load()).thenReturn(Optional.of(validSession()));
        when(getValidAccessTokenUseCase.execute()).thenThrow(failure);

        // When
        LogoutOutcome outcome = logoutUseCase.execute();

        // Then
        assertThat(outcome).isEqualTo(LogoutOutcome.REVOCATION_FAILED);
        verify(authApiClient, never()).logout(anyString(), anyString());
        verify(sessionTokenRepository).clear();
    }

    @Test
    @DisplayName("si la API rechaza el logout informa REVOCATION_FAILED y limpia la sesión igual (fail-open)")
    void reportsRevocationFailedAndClearsSessionWhenLogoutThrows() {
        // Given
        when(sessionTokenRepository.load()).thenReturn(Optional.of(validSession()));
        when(getValidAccessTokenUseCase.execute()).thenReturn(ACCESS_TOKEN);
        doThrow(new AuthApiClient.AuthenticationException("Unauthorized"))
                .when(authApiClient).logout(ACCESS_TOKEN, REFRESH_TOKEN);

        // When
        LogoutOutcome outcome = logoutUseCase.execute();

        // Then
        assertThat(outcome).isEqualTo(LogoutOutcome.REVOCATION_FAILED);
        verify(sessionTokenRepository).clear();
    }

    @Test
    @DisplayName("si la API no está disponible informa REVOCATION_FAILED y limpia la sesión igual (fail-open)")
    void reportsRevocationFailedWhenAuthApiUnavailable() {
        // Given
        when(sessionTokenRepository.load()).thenReturn(Optional.of(validSession()));
        when(getValidAccessTokenUseCase.execute()).thenReturn(ACCESS_TOKEN);
        doThrow(new AuthApiClient.ServiceUnavailableException("Auth API unreachable"))
                .when(authApiClient).logout(ACCESS_TOKEN, REFRESH_TOKEN);

        // When
        LogoutOutcome outcome = logoutUseCase.execute();

        // Then
        assertThat(outcome).isEqualTo(LogoutOutcome.REVOCATION_FAILED);
        verify(sessionTokenRepository).clear();
    }

    @Test
    @DisplayName("si la sesión desaparece al recargarla informa REVOCATION_FAILED y no llama al logout")
    void reportsRevocationFailedWhenSessionVanishesOnReload() {
        // Given
        when(sessionTokenRepository.load()).thenReturn(Optional.of(validSession())).thenReturn(Optional.empty());
        when(getValidAccessTokenUseCase.execute()).thenReturn(ACCESS_TOKEN);

        // When
        LogoutOutcome outcome = logoutUseCase.execute();

        // Then
        assertThat(outcome).isEqualTo(LogoutOutcome.REVOCATION_FAILED);
        verify(authApiClient, never()).logout(anyString(), anyString());
        verify(sessionTokenRepository).clear();
    }

    @Test
    @DisplayName("sin sesión informa NO_SESSION, no llama a nada y limpia de forma segura")
    void reportsNoSessionAndOnlyClearsWhenThereIsNoSession() {
        // Given
        when(sessionTokenRepository.load()).thenReturn(Optional.empty());

        // When
        LogoutOutcome outcome = logoutUseCase.execute();

        // Then
        assertThat(outcome).isEqualTo(LogoutOutcome.NO_SESSION);
        verifyNoInteractions(authApiClient, getValidAccessTokenUseCase);
        verify(sessionTokenRepository).clear();
    }

    @Test
    @DisplayName("es idempotente: llamarlo varias veces sin sesión es seguro")
    void isIdempotent() {
        // Given
        when(sessionTokenRepository.load()).thenReturn(Optional.empty());

        // When
        logoutUseCase.execute();
        logoutUseCase.execute();
        logoutUseCase.execute();

        // Then
        verify(sessionTokenRepository, times(3)).clear();
        verifyNoInteractions(authApiClient, getValidAccessTokenUseCase);
    }
}
