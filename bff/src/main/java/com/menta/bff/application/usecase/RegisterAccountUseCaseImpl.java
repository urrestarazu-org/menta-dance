package com.menta.bff.application.usecase;

import com.menta.bff.application.dto.RegisterCommand;
import com.menta.bff.application.port.out.AuthApiClient;
import java.util.Objects;

/**
 * Implementation of {@link RegisterAccountUseCase}.
 */
public class RegisterAccountUseCaseImpl implements RegisterAccountUseCase {

    private final AuthApiClient authApiClient;

    /**
     * Constructor for dependency injection.
     *
     * @param authApiClient HTTP client for Auth API
     */
    public RegisterAccountUseCaseImpl(AuthApiClient authApiClient) {
        this.authApiClient = Objects.requireNonNull(authApiClient, "authApiClient cannot be null");
    }

    @Override
    public void execute(RegisterCommand command) {
        Objects.requireNonNull(command, "command cannot be null");
        authApiClient.register(command);
    }
}
