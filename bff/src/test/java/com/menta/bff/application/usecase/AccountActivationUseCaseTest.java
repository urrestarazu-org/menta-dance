package com.menta.bff.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.menta.bff.application.dto.ActivationRequirement;
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
    private static final String PASSWORD = "Secreta123";

    @Mock
    private AuthApiClient authApiClient;

    private AccountActivationUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new AccountActivationUseCaseImpl(authApiClient);
    }

    @Test
    @DisplayName("a token the API accepts without a password check needs nothing else")
    void acceptedTokenNeedsNoPassword() {
        when(authApiClient.validateActivationToken(TOKEN)).thenReturn(ActivationRequirement.NONE);

        assertThat(useCase.checkToken(TOKEN)).isEqualTo(ActivationRequirement.NONE);
    }

    @Test
    @DisplayName("a token of an account created by an admin reports that a password is required")
    void adminAccountTokenRequiresAPassword() {
        when(authApiClient.validateActivationToken(TOKEN)).thenReturn(ActivationRequirement.PASSWORD);

        assertThat(useCase.checkToken(TOKEN)).isEqualTo(ActivationRequirement.PASSWORD);
    }

    @Test
    @DisplayName("validation lets a rejected token and availability failures reach the caller")
    void validationPropagatesFailures() {
        doThrow(new AuthApiClient.InvalidActivationTokenException("no")).when(authApiClient)
                .validateActivationToken(TOKEN);
        assertThatThrownBy(() -> useCase.checkToken(TOKEN))
                .isInstanceOf(AuthApiClient.InvalidActivationTokenException.class);

        doThrow(new AuthApiClient.ServiceUnavailableException("down")).when(authApiClient)
                .validateActivationToken(TOKEN);
        assertThatThrownBy(() -> useCase.checkToken(TOKEN))
                .isInstanceOf(AuthApiClient.ServiceUnavailableException.class);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "a/b", "..", "a b", "tok?en=1", "tok.en"})
    @DisplayName("a malformed token is invalid and never reaches the API")
    void malformedTokenNeverReachesTheApi(String malformed) {
        assertThatThrownBy(() -> useCase.checkToken(malformed))
                .isInstanceOf(AuthApiClient.InvalidActivationTokenException.class);
        assertThatThrownBy(() -> useCase.activate(malformed))
                .isInstanceOf(AuthApiClient.InvalidActivationTokenException.class);
        assertThatThrownBy(() -> useCase.activate(malformed, PASSWORD))
                .isInstanceOf(AuthApiClient.InvalidActivationTokenException.class);

        verifyNoInteractions(authApiClient);
    }

    @Test
    @DisplayName("an over-long token is invalid and never reaches the API")
    void overLongTokenNeverReachesTheApi() {
        assertThatThrownBy(() -> useCase.checkToken("a".repeat(257)))
                .isInstanceOf(AuthApiClient.InvalidActivationTokenException.class);

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

    @Test
    @DisplayName("activate with a password submits both to the Auth API")
    void activateSubmitsTheChosenPassword() {
        useCase.activate(TOKEN, PASSWORD);

        verify(authApiClient).activate(TOKEN, PASSWORD);
    }

    @Test
    @DisplayName("activate with a password lets the API verdicts reach the caller")
    void activateWithPasswordPropagatesVerdicts() {
        doThrow(new AuthApiClient.PasswordRejectedException(AuthApiClient.PasswordRejectedException.Reason.WEAK))
                .when(authApiClient).activate(TOKEN, PASSWORD);

        assertThatThrownBy(() -> useCase.activate(TOKEN, PASSWORD))
                .isInstanceOf(AuthApiClient.PasswordRejectedException.class);
    }

    @Test
    @DisplayName("activate without a password lets the API ask for one")
    void activateWithoutPasswordPropagatesPasswordRequired() {
        doThrow(new AuthApiClient.PasswordRequiredException()).when(authApiClient).activate(TOKEN);

        assertThatThrownBy(() -> useCase.activate(TOKEN))
                .isInstanceOf(AuthApiClient.PasswordRequiredException.class);
    }

    @Test
    @DisplayName("activate with a null password is a programming error and never reaches the API")
    void activateRejectsANullPassword() {
        assertThatThrownBy(() -> useCase.activate(TOKEN, null)).isInstanceOf(NullPointerException.class);

        verifyNoInteractions(authApiClient);
    }
}
