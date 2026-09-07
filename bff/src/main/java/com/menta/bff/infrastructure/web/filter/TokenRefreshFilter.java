package com.menta.bff.infrastructure.web.filter;

import com.menta.bff.application.port.out.AuthApiClient;
import com.menta.bff.application.usecase.GetValidAccessTokenUseCase;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Objects;

/**
 * Filter for transparent access token refresh.
 * <p>
 * Runs BEFORE business logic on every authenticated request:
 * 1. Checks if request is authenticated
 * 2. Loads session tokens via {@link GetValidAccessTokenUseCase}
 * 3. If access token expired, refreshes it transparently
 * 4. Sets access token as request attribute for downstream use
 * 5. Continues filter chain
 * </p>
 * <p>
 * Fail-closed behavior:
 * - Refresh failures (401, 423) → clear session + redirect to login
 * - Auth API unavailable (503) → return 503 error
 * </p>
 * <p>
 * Anonymous requests are skipped by two independent defenses: filter ordering
 * (this filter runs before {@code AnonymousAuthenticationFilter}, so the
 * security context authentication is still {@code null}) and an explicit
 * {@link AnonymousAuthenticationToken} check in {@link #doFilterInternal} —
 * see that method's comment for why both exist (#170).
 * </p>
 * <p>
 * Part of Clean Architecture infrastructure layer.
 * </p>
 */
public class TokenRefreshFilter extends OncePerRequestFilter {

    /**
     * Request attribute key for storing the valid access token.
     * <p>
     * Controllers can read this attribute to get the current access token
     * for making authenticated API calls.
     * </p>
     */
    public static final String ACCESS_TOKEN_ATTRIBUTE = "com.menta.bff.ACCESS_TOKEN";

    private final GetValidAccessTokenUseCase getValidAccessTokenUseCase;

    /**
     * Constructor for dependency injection.
     *
     * @param getValidAccessTokenUseCase Use case for getting valid access token with transparent refresh
     */
    public TokenRefreshFilter(GetValidAccessTokenUseCase getValidAccessTokenUseCase) {
        this.getValidAccessTokenUseCase = Objects.requireNonNull(
                getValidAccessTokenUseCase,
                "getValidAccessTokenUseCase cannot be null"
        );
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        // Skip filter for login/logout endpoints (Spring Security handles these)
        String requestUri = request.getRequestURI();
        if (requestUri.equals("/login") || requestUri.equals("/logout")) {
            filterChain.doFilter(request, response);
            return;
        }

        // Check if request is authenticated.
        //
        // Two independent defenses skip anonymous traffic here, per #170's
        // security hardening (this change is the first to route anonymous
        // visitors through this filter on a real page):
        //   1. Ordering (load-bearing today): BffSecurityConfig registers this
        //      filter via addFilterBefore(..., UsernamePasswordAuthenticationFilter.class),
        //      ahead of AnonymousAuthenticationFilter, so `authentication` is
        //      still null here for an anonymous request — caught by the first
        //      clause below.
        //   2. Explicit type check (defense in depth, not load-bearing while
        //      #1 holds): a real AnonymousAuthenticationToken returns true
        //      from isAuthenticated(), so it would slip past a bare
        //      `!authentication.isAuthenticated()` guard. If the filter is
        //      ever reordered after AnonymousAuthenticationFilter, this
        //      second clause is what keeps anonymous visitors browsing
        //      instead of being bounced to /login?sessionExpired=true.
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            // Unauthenticated or anonymous request - skip filter
            filterChain.doFilter(request, response);
            return;
        }

        try {
            // Load valid access token (with transparent refresh if needed)
            String accessToken = getValidAccessTokenUseCase.execute();

            // Set access token as request attribute for downstream use
            request.setAttribute(ACCESS_TOKEN_ATTRIBUTE, accessToken);

            // Continue filter chain
            filterChain.doFilter(request, response);

        } catch (GetValidAccessTokenUseCase.SessionNotFoundException e) {
            // No active session - clear authentication and redirect to login
            handleSessionError(response);

        } catch (AuthApiClient.AuthenticationException e) {
            // Refresh failed with 401 (invalid refresh token) - clear session and redirect
            handleSessionError(response);

        } catch (AuthApiClient.RefreshTokenRevokedException e) {
            // Token family revoked (423 - security breach detected) - clear session and redirect
            handleSessionError(response);

        } catch (AuthApiClient.ServiceUnavailableException e) {
            // Auth API unavailable (503) - propagate error to client
            response.sendError(
                    HttpServletResponse.SC_SERVICE_UNAVAILABLE,
                    "Service temporarily unavailable - please try again later"
            );
            // Do NOT call filterChain - request processing stops here
        }
    }

    /**
     * Handles session errors by clearing authentication and redirecting to login.
     *
     * @param response HTTP response for sending redirect
     * @throws IOException if redirect fails
     */
    private void handleSessionError(HttpServletResponse response) throws IOException {
        // Clear Spring Security authentication
        SecurityContextHolder.getContext().setAuthentication(null);

        // Redirect to login with session expired parameter
        response.sendRedirect("/login?sessionExpired=true");
        // Do NOT call filterChain - request processing stops here
    }
}
