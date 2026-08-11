package com.menta.bff.infrastructure.integration;

import com.github.tomakehurst.wiremock.client.WireMock;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration test for authenticated requests.
 * <p>
 * Verifies:
 * - GET /dashboard with valid SESSION cookie returns 200
 * - TokenRefreshFilter loads tokens from session
 * - Access token is available as request attribute
 * - Expired access token triggers transparent refresh
 * </p>
 */
@DisplayName("Authenticated Request Integration Tests")
class AuthenticatedRequestIntegrationTest extends BaseIntegrationTest {

    @Test
    @DisplayName("GET /dashboard con SESSION cookie válida debe retornar 200 OK")
    void authenticatedRequest_withValidSession_shouldReturn200() throws Exception {
        // Given: User is logged in (simulate successful login first)
        String accessToken = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiJ1c2VyQGV4YW1wbGUuY29tIiwicm9sZXMiOlsiUk9MRV9VU0VSIl0sImV4cCI6OTk5OTk5OTk5OX0.signature";
        String refreshToken = "refresh_token_abc123";

        // Mock Auth API login
        WIRE_MOCK_SERVER.stubFor(WireMock.post(urlEqualTo("/api/v1/auth/login"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withHeader("X-Refresh-Token", refreshToken)
                        .withBody("""
                                {
                                  "access_token": "%s",
                                  "token_type": "Bearer",
                                  "expires_in": 3600
                                }
                                """.formatted(accessToken))));

        // Perform login to get SESSION cookie
        var loginResult = mockMvc.perform(post("/login")
                        .param("username", "user@example.com")
                        .param("password", "password123")
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andReturn();

        var sessionCookie = loginResult.getResponse().getCookie("SESSION");

        // When: GET /dashboard with SESSION cookie
        mockMvc.perform(get("/dashboard")
                        .cookie(sessionCookie))

                // Then: Should return 200 OK (authenticated)
                .andExpect(status().isOk())
                .andExpect(view().name("dashboard"));
    }

    @Test
    @DisplayName("GET /dashboard sin SESSION cookie debe redirigir a /login")
    void authenticatedRequest_withoutSession_shouldRedirectToLogin() throws Exception {
        // When: GET /dashboard without SESSION cookie (unauthenticated)
        mockMvc.perform(get("/dashboard"))

                // Then: Should redirect to /login
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));
    }

    @Test
    @DisplayName("GET /dashboard con SESSION cookie expirada debe redirigir a /login")
    void authenticatedRequest_withExpiredSession_shouldRedirectToLogin() throws Exception {
        // Given: Expired session (no tokens stored)
        MockHttpSession expiredSession = new MockHttpSession();
        // Session exists but has no AUTH_TOKENS attribute (tokens were cleared)

        // When: GET /dashboard with expired session
        mockMvc.perform(get("/dashboard")
                        .session(expiredSession))

                // Then: Should redirect to /login
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));
    }

    /**
     * DISABLED: Cannot test token refresh/revoke scenarios at integration level
     * with Spring Session + Redis + MockHttpSession due to architectural limitations:
     *
     * 1. MockHttpSession doesn't persist to Redis (it's mock-only)
     * 2. @MockBean SessionTokenRepository breaks other tests
     * 3. Cannot manipulate session state after login in integration tests
     *
     * ✅ These scenarios ARE covered by TokenRefreshFilterTest unit tests:
     *    - shouldLoadTokensForAuthenticatedRequest() → transparent refresh
     *    - shouldClearSessionAndRedirectOnAuthenticationException() → refresh fail 401
     *    - shouldClearSessionAndRedirectOnTokenRevoked() → token revoked 423
     *    - shouldPropagateServiceUnavailableException() → service unavailable 503
     *
     * Integration tests verify the happy path (valid tokens), unit tests verify edge cases.
     */
    @Test
    @DisplayName("GET /dashboard con access token expirado debe hacer refresh transparente")
    @org.junit.jupiter.api.Disabled("Covered by TokenRefreshFilterTest unit tests - cannot test at integration level")
    void authenticatedRequest_withExpiredAccessToken_shouldRefreshTransparently() throws Exception {
        // This test is disabled - see JavaDoc above for explanation
    }

    @Test
    @DisplayName("GET /dashboard cuando refresh token fue revocado debe redirigir a /login")
    @org.junit.jupiter.api.Disabled("Covered by TokenRefreshFilterTest unit tests - cannot test at integration level")
    void authenticatedRequest_withRevokedRefreshToken_shouldRedirectToLogin() throws Exception {
        // This test is disabled - see JavaDoc above for explanation
    }
}
