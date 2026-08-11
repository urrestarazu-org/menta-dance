package com.menta.bff.application.usecase;

import com.menta.bff.application.dto.LoginCommand;
import com.menta.bff.application.dto.TokenPairResponse;
import com.menta.bff.application.port.out.AuthApiClient;
import com.menta.bff.application.port.out.SessionTokenRepository;
import com.menta.bff.domain.model.SessionTokens;

import java.time.Instant;
import java.util.Objects;

/**
 * Implementation of {@link LoginUseCase}.
 * <p>
 * Coordinates Auth API calls and session token storage.
 * This class contains business logic but no infrastructure details
 * (no HTTP, no Redis, no Spring annotations except @Component in infrastructure layer).
 * </p>
 */
public class LoginUseCaseImpl implements LoginUseCase {

    private final AuthApiClient authApiClient;
    private final SessionTokenRepository sessionTokenRepository;

    /**
     * Constructor for dependency injection.
     *
     * @param authApiClient HTTP client for Auth API
     * @param sessionTokenRepository Session storage for tokens
     */
    public LoginUseCaseImpl(
            AuthApiClient authApiClient,
            SessionTokenRepository sessionTokenRepository
    ) {
        this.authApiClient = Objects.requireNonNull(authApiClient, "authApiClient cannot be null");
        this.sessionTokenRepository = Objects.requireNonNull(sessionTokenRepository, "sessionTokenRepository cannot be null");
    }

    @Override
    public void execute(LoginCommand command) {
        Objects.requireNonNull(command, "command cannot be null");

        // Call Auth API to exchange credentials for tokens
        // Throws AuthenticationException if credentials invalid
        // Throws ServiceUnavailableException if Auth API unreachable
        TokenPairResponse response = authApiClient.login(command);

        // Calculate absolute expiration time
        Instant expiresAt = Instant.now().plusSeconds(response.expiresIn());

        // Create domain model from API response
        SessionTokens tokens = new SessionTokens(
                response.accessToken(),
                response.refreshToken(),
                expiresAt
        );

        // Store in server-side session (Redis)
        // Browser will receive opaque SESSION cookie, never the tokens
        sessionTokenRepository.store(tokens);
    }
}
