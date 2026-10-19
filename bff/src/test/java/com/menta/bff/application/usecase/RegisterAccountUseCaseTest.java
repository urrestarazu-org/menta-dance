package com.menta.bff.application.usecase;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import com.menta.bff.application.dto.RegisterCommand;
import com.menta.bff.application.port.out.AuthApiClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("RegisterAccountUseCase")
class RegisterAccountUseCaseTest {

    private static final RegisterCommand COMMAND =
            RegisterCommand.student("ana@example.com", "Secreta123", "203.0.113.9");

    @Mock
    private AuthApiClient authApiClient;

    private RegisterAccountUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new RegisterAccountUseCaseImpl(authApiClient);
    }

    @Test
    @DisplayName("submits the registration to the Auth API")
    void submitsTheRegistration() {
        useCase.execute(COMMAND);

        verify(authApiClient).register(COMMAND);
    }

    @Test
    @DisplayName("lets API failures reach the caller")
    void propagatesFailures() {
        doThrow(new AuthApiClient.RateLimitedException("limit", null)).when(authApiClient).register(COMMAND);

        assertThatThrownBy(() -> useCase.execute(COMMAND)).isInstanceOf(AuthApiClient.RateLimitedException.class);
    }

    @Test
    @DisplayName("rejects a null command")
    void rejectsNullCommand() {
        assertThatThrownBy(() -> useCase.execute(null)).isInstanceOf(NullPointerException.class);
    }
}
