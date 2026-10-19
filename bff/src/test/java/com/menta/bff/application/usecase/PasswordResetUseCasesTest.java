package com.menta.bff.application.usecase;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.menta.bff.application.dto.RequestPasswordResetCommand;
import com.menta.bff.application.port.out.AuthApiClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("Password recovery use cases")
class PasswordResetUseCasesTest {

    private static final RequestPasswordResetCommand COMMAND =
            new RequestPasswordResetCommand("user@example.com", "203.0.113.9");

    @Mock
    private AuthApiClient authApiClient;

    @Test
    @DisplayName("requesting a reset asks the Auth API and rejects a null command")
    void requestDelegates() {
        RequestPasswordResetUseCase useCase = new RequestPasswordResetUseCaseImpl(authApiClient);

        useCase.execute(COMMAND);

        verify(authApiClient).requestPasswordReset(COMMAND);
        assertThatThrownBy(() -> useCase.execute(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("the command refuses a blank email")
    void commandRefusesBlankEmail() {
        assertThatThrownBy(() -> new RequestPasswordResetCommand(" ", null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("resetting forwards a well-formed token and the new password")
    void resetDelegates() {
        new ResetPasswordUseCaseImpl(authApiClient).execute("reset-token-abc", "Password1");

        verify(authApiClient).resetPassword("reset-token-abc", "Password1");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"has space", "../etc", "a/b", "tok?en"})
    @DisplayName("resetting rejects a malformed token without calling the API")
    void resetRejectsMalformedTokens(String token) {
        ResetPasswordUseCase useCase = new ResetPasswordUseCaseImpl(authApiClient);

        assertThatThrownBy(() -> useCase.execute(token, "Password1"))
                .isInstanceOf(AuthApiClient.InvalidResetTokenException.class);
        verifyNoInteractions(authApiClient);
    }
}
