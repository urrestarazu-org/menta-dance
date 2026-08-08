package com.menta.bff.infrastructure.security;

import com.menta.bff.application.usecase.LogoutUseCase;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.logout.LogoutSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Custom logout handler that revokes refresh token in Auth API before
 * invalidating local session.
 *
 * Flow:
 * 1. Spring Security triggers logout (POST /logout with CSRF token)
 * 2. Call LogoutUseCase to revoke refresh token in Auth API
 * 3. Spring Security invalidates session (automatic)
 * 4. Redirect to /login?logout
 *
 * Fail-open: If Auth API revocation fails, we still invalidate local session
 * to prevent user lockout. The refresh token will eventually expire in Auth API.
 */
@Component
public class BffLogoutSuccessHandler implements LogoutSuccessHandler {

    private static final Logger log = LoggerFactory.getLogger(BffLogoutSuccessHandler.class);

    private final LogoutUseCase logoutUseCase;

    public BffLogoutSuccessHandler(LogoutUseCase logoutUseCase) {
        this.logoutUseCase = logoutUseCase;
    }

    @Override
    public void onLogoutSuccess(
        HttpServletRequest request,
        HttpServletResponse response,
        Authentication authentication
    ) throws IOException, ServletException {

        // Call LogoutUseCase to revoke refresh token
        // This is fail-open: if it fails, we still invalidate local session
        try {
            logoutUseCase.execute();
            log.info("User logged out successfully");
        } catch (Exception e) {
            // Log error but don't fail the logout
            // Local session will be invalidated anyway by Spring Security
            log.warn("Failed to revoke refresh token in Auth API (fail-open): {}", e.getMessage());
        }

        // Redirect to login page with logout message
        response.sendRedirect("/login?logout");
    }
}
