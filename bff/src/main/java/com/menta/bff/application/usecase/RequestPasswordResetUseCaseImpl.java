package com.menta.bff.application.usecase;

import com.menta.bff.application.dto.RequestPasswordResetCommand;
import com.menta.bff.application.port.out.AuthApiClient;
import java.util.Objects;

/**
 * Implementation of {@link RequestPasswordResetUseCase}.
 */
public class RequestPasswordResetUseCaseImpl implements RequestPasswordResetUseCase {

    private final AuthApiClient authApiClient;

    /**
     * Constructor for dependency injection.
     *
     * @param authApiClient HTTP client for Auth API
     */
    public RequestPasswordResetUseCaseImpl(AuthApiClient authApiClient) {
        this.authApiClient = Objects.requireNonNull(authApiClient, "authApiClient cannot be null");
    }

    @Override
    public void execute(RequestPasswordResetCommand command) {
        Objects.requireNonNull(command, "command cannot be null");
        authApiClient.requestPasswordReset(command);
    }
}
