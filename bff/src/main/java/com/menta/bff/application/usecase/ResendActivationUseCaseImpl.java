package com.menta.bff.application.usecase;

import com.menta.bff.application.dto.ResendActivationCommand;
import com.menta.bff.application.port.out.AuthApiClient;
import java.util.Objects;

/**
 * Implementation of {@link ResendActivationUseCase}.
 */
public class ResendActivationUseCaseImpl implements ResendActivationUseCase {

    private final AuthApiClient authApiClient;

    /**
     * Constructor for dependency injection.
     *
     * @param authApiClient HTTP client for Auth API
     */
    public ResendActivationUseCaseImpl(AuthApiClient authApiClient) {
        this.authApiClient = Objects.requireNonNull(authApiClient, "authApiClient cannot be null");
    }

    @Override
    public void execute(ResendActivationCommand command) {
        Objects.requireNonNull(command, "command cannot be null");
        authApiClient.resendActivation(command);
    }
}
