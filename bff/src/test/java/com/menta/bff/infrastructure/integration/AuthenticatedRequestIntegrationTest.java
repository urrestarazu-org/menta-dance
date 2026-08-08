package com.menta.bff.infrastructure.integration;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.menta.bff.domain.model.SessionTokens;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;

import java.time.Instant;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
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
        wireMockServer.stubFor(WireMock.post(urlEqualTo("/api/v1/auth/login"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withHeader("X-Refresh-Token", refreshToken)
                        .withBody("""
                                {
                                  "accessToken": "%s",
                                  "expiresIn": 3600
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

    // TODO: Fix this test - MockHttpSession manipulation doesn't work with Spring Session + Redis
    // Need to use SessionTokenRepository directly to manipulate session state
    @Test
    @DisplayName("GET /dashboard con access token expirado debe hacer refresh transparente")
    @org.junit.jupiter.api.Disabled("TODO: Fix session manipulation approach")
    void authenticatedRequest_withExpiredAccessToken_shouldRefreshTransparently() throws Exception {
        // Given: User logged in with expired access token
        String oldAccessToken = "expired_access_token";
        String newAccessToken = "new_access_token_after_refresh";
        String refreshToken = "refresh_token_abc123";

        // Step 1: Login (mock successful login)
        wireMockServer.stubFor(WireMock.post(urlEqualTo("/api/v1/auth/login"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withHeader("X-Refresh-Token", refreshToken)
                        .withBody("""
                                {
                                  "accessToken": "%s",
                                  "expiresIn": 3600
                                }
                                """.formatted(oldAccessToken))));

        var loginResult = mockMvc.perform(post("/login")
                        .param("username", "user@example.com")
                        .param("password", "password123")
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andReturn();

        var sessionCookie = loginResult.getResponse().getCookie("SESSION");

        // Step 2: Manually expire the access token in session
        // (In real scenario, we'd wait for expiration; here we simulate it)
        MockHttpSession session = (MockHttpSession) loginResult.getRequest().getSession();
        SessionTokens expiredTokens = new SessionTokens(
                oldAccessToken,
                refreshToken,
                Instant.now().minusSeconds(60) // Expired 60 seconds ago
        );
        session.setAttribute("AUTH_TOKENS", expiredTokens);

        // Step 3: Mock Auth API refresh endpoint
        wireMockServer.stubFor(WireMock.post(urlEqualTo("/api/v1/auth/refresh"))
                .withHeader("X-Refresh-Token", equalTo(refreshToken))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withHeader("X-Refresh-Token", refreshToken)
                        .withBody("""
                                {
                                  "accessToken": "%s",
                                  "expiresIn": 3600
                                }
                                """.formatted(newAccessToken))));

        // When: GET /dashboard (should trigger transparent refresh)
        mockMvc.perform(get("/dashboard")
                        .session(session))

                // Then: Should return 200 OK (refresh was transparent)
                .andExpect(status().isOk())
                .andExpect(view().name("dashboard"));

        // And: Verify Auth API refresh was called
        wireMockServer.verify(postRequestedFor(urlEqualTo("/api/v1/auth/refresh"))
                .withHeader("X-Refresh-Token", equalTo(refreshToken)));
    }

    // TODO: Fix this test - same issue as above
    @Test
    @DisplayName("GET /dashboard cuando refresh token fue revocado debe redirigir a /login")
    @org.junit.jupiter.api.Disabled("TODO: Fix session manipulation approach")
    void authenticatedRequest_withRevokedRefreshToken_shouldRedirectToLogin() throws Exception {
        // Given: User logged in
        String accessToken = "expired_access_token";
        String refreshToken = "revoked_refresh_token";

        wireMockServer.stubFor(WireMock.post(urlEqualTo("/api/v1/auth/login"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withHeader("X-Refresh-Token", refreshToken)
                        .withBody("""
                                {
                                  "accessToken": "%s",
                                  "expiresIn": 3600
                                }
                                """.formatted(accessToken))));

        var loginResult = mockMvc.perform(post("/login")
                        .param("username", "user@example.com")
                        .param("password", "password123")
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andReturn();

        MockHttpSession session = (MockHttpSession) loginResult.getRequest().getSession();

        // Expire access token to force refresh
        SessionTokens expiredTokens = new SessionTokens(
                accessToken,
                refreshToken,
                Instant.now().minusSeconds(60)
        );
        session.setAttribute("AUTH_TOKENS", expiredTokens);

        // Mock Auth API refresh returning 423 (token family revoked)
        wireMockServer.stubFor(WireMock.post(urlEqualTo("/api/v1/auth/refresh"))
                .willReturn(aResponse()
                        .withStatus(423) // Locked - token family revoked
                        .withHeader("Content-Type", "application/problem+json")
                        .withBody("""
                                {
                                  "type": "about:blank",
                                  "title": "Locked",
                                  "status": 423,
                                  "detail": "Token family revoked - security breach detected"
                                }
                                """)));

        // When: GET /dashboard (should detect revoked token and log out)
        mockMvc.perform(get("/dashboard")
                        .session(session))

                // Then: Should redirect to /login with session expired
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?sessionExpired=true"));
    }
}
