package com.menta.bff.application.usecase;

import com.menta.bff.application.dto.TokenPairResponse;
import com.menta.bff.application.port.out.AuthApiClient;
import com.menta.bff.application.port.out.SessionTokenRepository;
import com.menta.bff.domain.model.SessionTokens;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * Implementation of {@link GetValidAccessTokenUseCase}.
 * <p>
 * Handles transparent token refresh logic.
 * </p>
 */
public class GetValidAccessTokenUseCaseImpl implements GetValidAccessTokenUseCase {

    private final AuthApiClient authApiClient;
    private final SessionTokenRepository sessionTokenRepository;

    /**
     * Constructor for dependency injection.
     *
     * @param authApiClient HTTP client for Auth API
     * @param sessionTokenRepository Session storage for tokens
     */
    public GetValidAccessTokenUseCaseImpl(
            AuthApiClient authApiClient,
            SessionTokenRepository sessionTokenRepository
    ) {
        this.authApiClient = Objects.requireNonNull(authApiClient, "authApiClient cannot be null");
        this.sessionTokenRepository = Objects.requireNonNull(sessionTokenRepository, "sessionTokenRepository cannot be null");
    }

    @Override
    public String execute() {
        // Load tokens from session
        Optional<SessionTokens> maybeTokens = sessionTokenRepository.load();

        if (maybeTokens.isEmpty()) {
            throw new SessionNotFoundException("No active session found - user must login");
        }

        SessionTokens tokens = maybeTokens.get();

        // Check if access token is expired
        Instant now = Instant.now();
        if (!tokens.isExpired(now)) {
            // Token still valid, return it
            return tokens.accessToken();
        }

        // Token expired - refresh transparently
        try {
            TokenPairResponse refreshResponse = authApiClient.refresh(tokens.refreshToken());

            // Calculate new expiration time
            Instant newExpiresAt = now.plusSeconds(refreshResponse.expiresIn());

            // Create updated tokens
            SessionTokens newTokens = new SessionTokens(
                    refreshResponse.accessToken(),
                    refreshResponse.refreshToken(),
                    newExpiresAt
            );

            // Store updated tokens in session
            sessionTokenRepository.store(newTokens);

            // Return new access token
            return newTokens.accessToken();

        } catch (AuthApiClient.RefreshTokenRevokedException e) {
            // Security breach detected - clear session and fail closed
            sessionTokenRepository.clear();
            throw e;
        }
        // Let other exceptions (AuthenticationException, ServiceUnavailableException) propagate
    }
}
