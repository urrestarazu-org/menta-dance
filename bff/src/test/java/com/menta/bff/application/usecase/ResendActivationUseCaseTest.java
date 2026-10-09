package com.menta.bff.application.usecase;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import com.menta.bff.application.dto.ResendActivationCommand;
import com.menta.bff.application.port.out.AuthApiClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("ResendActivationUseCase")
class ResendActivationUseCaseTest {

    private static final ResendActivationCommand COMMAND =
            new ResendActivationCommand("user@example.com", "203.0.113.9");

    @Mock
    private AuthApiClient authApiClient;

    private ResendActivationUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new ResendActivationUseCaseImpl(authApiClient);
    }

    @Test
    @DisplayName("asks the Auth API to resend the activation email")
    void asksTheApi() {
        useCase.execute(COMMAND);

        verify(authApiClient).resendActivation(COMMAND);
    }

    @Test
    @DisplayName("lets API failures reach the caller")
    void propagatesFailures() {
        doThrow(new AuthApiClient.ServiceUnavailableException("down")).when(authApiClient)
                .resendActivation(COMMAND);

        assertThatThrownBy(() -> useCase.execute(COMMAND))
                .isInstanceOf(AuthApiClient.ServiceUnavailableException.class);
    }

    @Test
    @DisplayName("rejects a null command")
    void rejectsNullCommand() {
        assertThatThrownBy(() -> useCase.execute(null)).isInstanceOf(NullPointerException.class);
    }
}
