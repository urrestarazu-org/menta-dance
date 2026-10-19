package com.menta.bff.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.menta.bff.application.port.out.AuthApiClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("AccountActivationUseCase")
class AccountActivationUseCaseTest {

    private static final String TOKEN = "activation-token-abc";

    @Mock
    private AuthApiClient authApiClient;

    private AccountActivationUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new AccountActivationUseCaseImpl(authApiClient);
    }

    @Test
    @DisplayName("a token the API accepts is valid")
    void acceptedTokenIsValid() {
        assertThat(useCase.isTokenValid(TOKEN)).isTrue();

        verify(authApiClient).validateActivationToken(TOKEN);
    }

    @Test
    @DisplayName("a token the API rejects is not valid")
    void rejectedTokenIsNotValid() {
        doThrow(new AuthApiClient.InvalidActivationTokenException("no")).when(authApiClient)
                .validateActivationToken(TOKEN);

        assertThat(useCase.isTokenValid(TOKEN)).isFalse();
    }

    @Test
    @DisplayName("validation lets availability failures reach the caller")
    void validationPropagatesAvailabilityFailures() {
        doThrow(new AuthApiClient.ServiceUnavailableException("down")).when(authApiClient)
                .validateActivationToken(TOKEN);

        assertThatThrownBy(() -> useCase.isTokenValid(TOKEN))
                .isInstanceOf(AuthApiClient.ServiceUnavailableException.class);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "a/b", "..", "a b", "tok?en=1", "tok.en"})
    @DisplayName("a malformed token is invalid and never reaches the API")
    void malformedTokenNeverReachesTheApi(String malformed) {
        assertThat(useCase.isTokenValid(malformed)).isFalse();
        assertThatThrownBy(() -> useCase.activate(malformed))
                .isInstanceOf(AuthApiClient.InvalidActivationTokenException.class);

        verifyNoInteractions(authApiClient);
    }

    @Test
    @DisplayName("an over-long token is invalid and never reaches the API")
    void overLongTokenNeverReachesTheApi() {
        assertThat(useCase.isTokenValid("a".repeat(257))).isFalse();

        verifyNoInteractions(authApiClient);
    }

    @Test
    @DisplayName("activate submits the token to the Auth API")
    void activateSubmitsTheToken() {
        useCase.activate(TOKEN);

        verify(authApiClient).activate(TOKEN);
    }

    @Test
    @DisplayName("activate lets API failures reach the caller")
    void activatePropagatesFailures() {
        doThrow(new AuthApiClient.RateLimitedException("limit", null)).when(authApiClient).activate(TOKEN);

        assertThatThrownBy(() -> useCase.activate(TOKEN)).isInstanceOf(AuthApiClient.RateLimitedException.class);
    }
}
