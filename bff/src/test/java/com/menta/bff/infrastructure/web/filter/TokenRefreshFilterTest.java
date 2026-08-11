package com.menta.bff.infrastructure.web.filter;

import com.menta.bff.application.usecase.GetValidAccessTokenUseCase;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.IOException;

import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link TokenRefreshFilter}.
 * <p>
 * Tests transparent token refresh behavior for authenticated requests.
 * </p>
 */
@ExtendWith(MockitoExtension.class)
class TokenRefreshFilterTest {

    @Mock
    private GetValidAccessTokenUseCase getValidAccessTokenUseCase;

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Mock
    private FilterChain filterChain;

    @Mock
    private SecurityContext securityContext;

    private TokenRefreshFilter filter;

    @BeforeEach
    void setUp() {
        filter = new TokenRefreshFilter(getValidAccessTokenUseCase);
        SecurityContextHolder.setContext(securityContext);

        // Mock request URI (default to /dashboard, can be overridden in specific tests)
        when(request.getRequestURI()).thenReturn("/dashboard");
    }

    @Test
    @DisplayName("Should load tokens and set access token attribute for authenticated request")
    void shouldLoadTokensForAuthenticatedRequest() throws ServletException, IOException {
        // Given: authenticated request
        Authentication authentication = mock(Authentication.class);
        when(authentication.isAuthenticated()).thenReturn(true);
        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(getValidAccessTokenUseCase.execute()).thenReturn("valid-access-token");

        // When
        filter.doFilterInternal(request, response, filterChain);

        // Then: use case called and attribute set
        verify(getValidAccessTokenUseCase).execute();
        verify(request).setAttribute(TokenRefreshFilter.ACCESS_TOKEN_ATTRIBUTE, "valid-access-token");
        verify(filterChain).doFilter(request, response);
    }

    @Test
    @DisplayName("Should skip filter for unauthenticated request")
    void shouldSkipFilterForUnauthenticatedRequest() throws ServletException, IOException {
        // Given: unauthenticated request
        when(securityContext.getAuthentication()).thenReturn(null);

        // When
        filter.doFilterInternal(request, response, filterChain);

        // Then: use case NOT called
        verifyNoInteractions(getValidAccessTokenUseCase);
        verify(filterChain).doFilter(request, response);
    }

    @Test
    @DisplayName("Should skip filter for anonymous authentication")
    void shouldSkipFilterForAnonymousAuthentication() throws ServletException, IOException {
        // Given: anonymous authentication
        Authentication authentication = mock(Authentication.class);
        when(authentication.isAuthenticated()).thenReturn(false);
        when(securityContext.getAuthentication()).thenReturn(authentication);

        // When
        filter.doFilterInternal(request, response, filterChain);

        // Then: use case NOT called
        verifyNoInteractions(getValidAccessTokenUseCase);
        verify(filterChain).doFilter(request, response);
    }

    @Test
    @DisplayName("Should clear session and redirect to login when refresh fails with 401")
    void shouldClearSessionAndRedirectOnAuthenticationException() throws ServletException, IOException {
        // Given: authenticated request with expired token that fails refresh
        Authentication authentication = mock(Authentication.class);
        when(authentication.isAuthenticated()).thenReturn(true);
        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(getValidAccessTokenUseCase.execute())
                .thenThrow(new com.menta.bff.application.port.out.AuthApiClient.AuthenticationException("Invalid refresh token"));

        // When
        filter.doFilterInternal(request, response, filterChain);

        // Then: session cleared and redirected to login
        verify(securityContext).setAuthentication(null);
        verify(response).sendRedirect("/login?sessionExpired=true");
        verifyNoInteractions(filterChain); // Filter chain NOT called
    }

    @Test
    @DisplayName("Should clear session and redirect to login when token family revoked (423)")
    void shouldClearSessionAndRedirectOnTokenRevoked() throws ServletException, IOException {
        // Given: refresh returns 423 (token family revoked - security breach)
        Authentication authentication = mock(Authentication.class);
        when(authentication.isAuthenticated()).thenReturn(true);
        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(getValidAccessTokenUseCase.execute())
                .thenThrow(new com.menta.bff.application.port.out.AuthApiClient.RefreshTokenRevokedException("Token family revoked"));

        // When
        filter.doFilterInternal(request, response, filterChain);

        // Then: session cleared and redirected to login
        verify(securityContext).setAuthentication(null);
        verify(response).sendRedirect("/login?sessionExpired=true");
        verifyNoInteractions(filterChain);
    }

    @Test
    @DisplayName("Should propagate error when Auth API unavailable (503)")
    void shouldPropagateServiceUnavailableException() throws ServletException, IOException {
        // Given: Auth API unavailable
        Authentication authentication = mock(Authentication.class);
        when(authentication.isAuthenticated()).thenReturn(true);
        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(getValidAccessTokenUseCase.execute())
                .thenThrow(new com.menta.bff.application.port.out.AuthApiClient.ServiceUnavailableException("Auth API unavailable"));

        // When
        filter.doFilterInternal(request, response, filterChain);

        // Then: error propagated (response status set, filter chain NOT called)
        verify(response).sendError(503, "Service temporarily unavailable - please try again later");
        verifyNoInteractions(filterChain);
    }

    @Test
    @DisplayName("Should handle session not found gracefully (redirect to login)")
    void shouldHandleSessionNotFoundException() throws ServletException, IOException {
        // Given: no active session
        Authentication authentication = mock(Authentication.class);
        when(authentication.isAuthenticated()).thenReturn(true);
        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(getValidAccessTokenUseCase.execute())
                .thenThrow(new GetValidAccessTokenUseCase.SessionNotFoundException("No active session"));

        // When
        filter.doFilterInternal(request, response, filterChain);

        // Then: redirected to login
        verify(securityContext).setAuthentication(null);
        verify(response).sendRedirect("/login?sessionExpired=true");
        verifyNoInteractions(filterChain);
    }
}
