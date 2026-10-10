package com.menta.bff.application.usecase;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;

import com.menta.bff.application.dto.RequestPasswordResetCommand;
import com.menta.bff.application.port.out.AuthApiClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("Request password reset use case")
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
}
