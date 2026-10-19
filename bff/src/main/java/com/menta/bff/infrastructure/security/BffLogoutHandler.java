package com.menta.bff.infrastructure.security;

import com.menta.bff.application.usecase.LogoutOutcome;
import com.menta.bff.application.usecase.LogoutUseCase;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.logout.LogoutHandler;
import org.springframework.stereotype.Component;

/**
 * Custom logout handler that revokes refresh token in Auth API BEFORE
 * Spring Security invalidates the session.
 *
 * <p>This handler is invoked BEFORE session invalidation, ensuring the
 * refresh token is still available in the session when we attempt to revoke it.
 * </p>
 *
 * <p>Flow:
 * 1. Spring Security triggers logout (POST /logout with CSRF token)
 * 2. This handler executes FIRST
 * 3. The use case gets a valid access token from the session (refreshing it if expired)
 * 4. The use case revokes the refresh token in Auth API (Bearer access token + X-Refresh-Token)
 * 5. Spring Security invalidates session (automatic)
 * 6. BffLogoutSuccessHandler redirects to /login?logout
 * </p>
 *
 * <p>Fail-open: If Auth API revocation fails, we still allow logout to proceed.
 * The local session will be invalidated and the refresh token will eventually
 * expire in Auth API (7 days TTL). The log line reflects what really happened:
 * revocation is only reported when the use case confirms it.
 * </p>
 *
 * <p>Part of Clean Architecture infrastructure layer.
 * </p>
 */
@Component
public class BffLogoutHandler implements LogoutHandler {

    private static final Logger log = LoggerFactory.getLogger(BffLogoutHandler.class);

    private final LogoutUseCase logoutUseCase;

    /**
     * Constructor for dependency injection.
     *
     * @param logoutUseCase Use case for revoking refresh token
     */
    public BffLogoutHandler(LogoutUseCase logoutUseCase) {
        this.logoutUseCase = Objects.requireNonNull(logoutUseCase, "logoutUseCase cannot be null");
    }

    @Override
    public void logout(
            HttpServletRequest request,
            HttpServletResponse response,
            Authentication authentication
    ) {
        // This method is called BEFORE Spring Security invalidates the session
        // So the tokens are still available in session storage

        try {
            // LogoutUseCase will:
            // 1. Obtain a valid access token (refreshing it and rotating the refresh token if expired)
            // 2. Call Auth API POST /api/v1/auth/logout with Bearer access token + X-Refresh-Token header
            // 3. Clear session tokens (optional, session will be invalidated anyway)
            LogoutOutcome outcome = logoutUseCase.execute();
            logOutcome(outcome);

        } catch (Exception e) {
            // Fail-open: Log error but don't throw exception
            // This allows logout to proceed even if Auth API is unavailable
            // The local session will still be invalidated by Spring Security
            log.warn("Logout failed unexpectedly, refresh token not revoked (fail-open): {}",
                    e.getClass().getSimpleName());
        }

        // NOTE: We do NOT redirect here - that's done by LogoutSuccessHandler
        // This handler only handles the revocation logic
    }

    private static void logOutcome(LogoutOutcome outcome) {
        if (outcome == LogoutOutcome.REVOKED) {
            log.info("User logged out successfully - refresh token revoked");
        } else if (outcome == LogoutOutcome.REVOCATION_FAILED) {
            log.warn("User logged out locally, but refresh token revocation failed; local session cleared anyway");
        } else {
            log.debug("Logout requested without an active session - nothing to revoke");
        }
    }
}
