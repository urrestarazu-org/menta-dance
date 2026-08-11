package com.menta.bff.application.usecase;

import com.menta.bff.application.port.out.AuthApiClient;
import com.menta.bff.application.port.out.SessionTokenRepository;
import com.menta.bff.domain.model.SessionTokens;

import java.util.Objects;
import java.util.Optional;

/**
 * Implementation of {@link LogoutUseCase}.
 * <p>
 * Implements fail-open logout: always clears local session,
 * even if Auth API revocation fails.
 * </p>
 */
public class LogoutUseCaseImpl implements LogoutUseCase {

    private final AuthApiClient authApiClient;
    private final SessionTokenRepository sessionTokenRepository;

    /**
     * Constructor for dependency injection.
     *
     * @param authApiClient HTTP client for Auth API
     * @param sessionTokenRepository Session storage for tokens
     */
    public LogoutUseCaseImpl(
            AuthApiClient authApiClient,
            SessionTokenRepository sessionTokenRepository
    ) {
        this.authApiClient = Objects.requireNonNull(authApiClient, "authApiClient cannot be null");
        this.sessionTokenRepository = Objects.requireNonNull(sessionTokenRepository, "sessionTokenRepository cannot be null");
    }

    @Override
    public void execute() {
        // Load current session tokens (if any)
        Optional<SessionTokens> maybeTokens = sessionTokenRepository.load();

        // If session exists, attempt to revoke refresh token in Auth API
        if (maybeTokens.isPresent()) {
            String refreshToken = maybeTokens.get().refreshToken();

            try {
                // Best-effort revocation
                authApiClient.logout(refreshToken);
            } catch (Exception e) {
                // Fail-open: Continue to clear local session even if revocation fails
                // This prevents user lockout if Auth API is temporarily unavailable
                // Security note: Access tokens expire in 15 min, refresh tokens rotate,
                // so this is safe - user's session is cleared and they can't make new requests
            }
        }

        // Always clear local session, even if:
        // - Session was empty
        // - Revocation failed
        // - Revocation threw exception
        sessionTokenRepository.clear();
    }
}
