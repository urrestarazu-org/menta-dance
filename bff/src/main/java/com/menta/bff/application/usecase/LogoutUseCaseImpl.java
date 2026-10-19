package com.menta.bff.application.usecase;

import com.menta.bff.application.port.out.AuthApiClient;
import com.menta.bff.application.port.out.SessionTokenRepository;
import com.menta.bff.domain.model.SessionTokens;
import java.util.Objects;

/**
 * Implementation of {@link LogoutUseCase}.
 *
 * <p>Implements fail-open logout: always clears local session,
 * even if Auth API revocation fails.
 * </p>
 *
 * <p>The Auth API logout endpoint is authenticated, so revocation needs a valid access
 * token. {@link GetValidAccessTokenUseCase} provides one (refreshing it when expired,
 * which also rotates the stored refresh token), and the session is reloaded afterwards
 * so the refresh token that gets revoked is the current one, not a stale copy.
 * </p>
 */
public class LogoutUseCaseImpl implements LogoutUseCase {

    private final AuthApiClient authApiClient;
    private final SessionTokenRepository sessionTokenRepository;
    private final GetValidAccessTokenUseCase getValidAccessTokenUseCase;

    /**
     * Constructor for dependency injection.
     *
     * @param authApiClient HTTP client for Auth API
     * @param sessionTokenRepository Session storage for tokens
     * @param getValidAccessTokenUseCase Provides a non-expired access token, refreshing when needed
     */
    public LogoutUseCaseImpl(
            AuthApiClient authApiClient,
            SessionTokenRepository sessionTokenRepository,
            GetValidAccessTokenUseCase getValidAccessTokenUseCase
    ) {
        this.authApiClient = Objects.requireNonNull(authApiClient, "authApiClient cannot be null");
        this.sessionTokenRepository =
            Objects.requireNonNull(sessionTokenRepository, "sessionTokenRepository cannot be null");
        this.getValidAccessTokenUseCase =
            Objects.requireNonNull(getValidAccessTokenUseCase, "getValidAccessTokenUseCase cannot be null");
    }

    @Override
    public LogoutOutcome execute() {
        // Always clear local session when done, even if the session was empty or revocation failed
        // (including when it threw). Fail-open: this prevents user lockout if the Auth API is down.
        // Security note: access tokens expire in 15 min and the refresh token is not usable
        // without the cleared session, so the user can't make new requests.
        try {
            return revokeIfSessionExists();
        } finally {
            sessionTokenRepository.clear();
        }
    }

    private LogoutOutcome revokeIfSessionExists() {
        try {
            if (sessionTokenRepository.load().isEmpty()) {
                return LogoutOutcome.NO_SESSION;
            }

            // May refresh and rotate the stored refresh token, so load the session AFTER this call
            String accessToken = getValidAccessTokenUseCase.execute();
            SessionTokens current = sessionTokenRepository.load()
                    .orElseThrow(() -> new GetValidAccessTokenUseCase.SessionNotFoundException(
                            "Session disappeared before revocation"));

            authApiClient.logout(accessToken, current.refreshToken());
            return LogoutOutcome.REVOKED;
        } catch (RuntimeException e) {
            // Fail-open: the caller reports the outcome; the local session is cleared regardless.
            // The exception is dropped on purpose: it may carry upstream detail and must not leak tokens.
            return LogoutOutcome.REVOCATION_FAILED;
        }
    }
}
