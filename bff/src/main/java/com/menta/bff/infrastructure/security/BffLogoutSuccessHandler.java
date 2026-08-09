package com.menta.bff.infrastructure.security;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.logout.LogoutSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Custom logout success handler that redirects to login page after logout.
 * <p>
 * This handler is invoked AFTER:
 * - BffLogoutHandler revokes refresh token (if present)
 * - Spring Security invalidates session
 * </p>
 * <p>
 * Its only responsibility is to redirect the user to /login?logout
 * to display a "logout successful" message.
 * </p>
 * <p>
 * Flow:
 * 1. Spring Security triggers logout (POST /logout with CSRF token)
 * 2. BffLogoutHandler revokes refresh token in Auth API
 * 3. Spring Security invalidates session
 * 4. This handler redirects to /login?logout
 * </p>
 * <p>
 * Part of Clean Architecture infrastructure layer.
 * </p>
 */
@Component
public class BffLogoutSuccessHandler implements LogoutSuccessHandler {

    @Override
    public void onLogoutSuccess(
        HttpServletRequest request,
        HttpServletResponse response,
        Authentication authentication
    ) throws IOException, ServletException {

        // Redirect to login page with logout message
        // The refresh token revocation was already handled by BffLogoutHandler
        response.sendRedirect("/login?logout");
    }
}
