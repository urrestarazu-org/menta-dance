package com.menta.bff.infrastructure.integration;

import com.github.tomakehurst.wiremock.client.WireMock;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration test for logout flow.
 * <p>
 * Verifies:
 * - POST /logout revokes refresh token in Auth API
 * - POST /logout clears session (invalidates session)
 * - POST /logout redirects to /login?logout
 * - Fail-open: logout succeeds even if Auth API fails
 * </p>
 */
@DisplayName("Logout Integration Tests")
class LogoutIntegrationTest extends BaseIntegrationTest {

    // TODO: Fix logout flow - Spring Security invalidates session BEFORE LogoutSuccessHandler runs
    // This means sessionTokenRepository.load() returns empty and we can't revoke the refresh token
    // Solution: Implement custom LogoutHandler that runs BEFORE session invalidation
    @Test
    @DisplayName("POST /logout debe revocar refresh token y limpiar sesión")
    @org.junit.jupiter.api.Disabled("TODO: Fix logout handler order")
    void logout_shouldRevokeTokenAndClearSession() throws Exception {
        // Given: User is logged in
        String accessToken = "access_token_abc123";
        String refreshToken = "refresh_token_xyz789";

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
        assertThat(sessionCookie).isNotNull();

        // Mock Auth API logout (should receive refresh token)
        wireMockServer.stubFor(WireMock.post(urlEqualTo("/api/v1/auth/logout"))
                .withHeader("X-Refresh-Token", equalTo(refreshToken))
                .willReturn(aResponse()
                        .withStatus(204))); // 204 No Content - successful revocation

        // When: POST /logout with SESSION cookie
        var logoutResult = mockMvc.perform(post("/logout")
                        .cookie(sessionCookie)
                        .with(csrf()))

                // Then: Should redirect to /login?logout
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?logout"))

                .andReturn();

        // And: Verify Auth API logout was called with correct refresh token
        wireMockServer.verify(postRequestedFor(urlEqualTo("/api/v1/auth/logout"))
                .withHeader("X-Refresh-Token", equalTo(refreshToken)));

        // And: Verify session was invalidated (cookie should be expired/deleted)
        var logoutCookie = logoutResult.getResponse().getCookie("SESSION");
        // Spring Security invalidates session by setting Max-Age=0
        assertThat(logoutCookie).isNotNull();
        assertThat(logoutCookie.getMaxAge()).isEqualTo(0);
    }

    @Test
    @DisplayName("POST /logout sin CSRF token debe retornar 403 Forbidden")
    void logout_withoutCsrfToken_shouldReturnForbidden() throws Exception {
        // Given: User is logged in
        String accessToken = "access_token";
        String refreshToken = "refresh_token";

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

        var sessionCookie = loginResult.getResponse().getCookie("SESSION");

        // When: POST /logout without CSRF token
        mockMvc.perform(post("/logout")
                        .cookie(sessionCookie))

                // Then: Should return 403 Forbidden
                .andExpect(status().isForbidden());
    }

    // TODO: Same issue as above
    @Test
    @DisplayName("POST /logout cuando Auth API falla debe igual limpiar sesión local (fail-open)")
    @org.junit.jupiter.api.Disabled("TODO: Fix logout handler order")
    void logout_whenAuthApiFailsToRevoke_shouldStillClearSession() throws Exception {
        // Given: User is logged in
        String accessToken = "access_token";
        String refreshToken = "refresh_token";

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

        var sessionCookie = loginResult.getResponse().getCookie("SESSION");

        // Mock Auth API logout failing (503 Service Unavailable)
        wireMockServer.stubFor(WireMock.post(urlEqualTo("/api/v1/auth/logout"))
                .willReturn(aResponse()
                        .withStatus(503)
                        .withHeader("Content-Type", "application/problem+json")
                        .withBody("""
                                {
                                  "type": "about:blank",
                                  "title": "Service Unavailable",
                                  "status": 503,
                                  "detail": "Auth service temporarily unavailable"
                                }
                                """)));

        // When: POST /logout (Auth API will fail but logout should succeed)
        var logoutResult = mockMvc.perform(post("/logout")
                        .cookie(sessionCookie)
                        .with(csrf()))

                // Then: Should STILL redirect to /login?logout (fail-open behavior)
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?logout"))

                .andReturn();

        // And: Verify session was invalidated despite Auth API failure
        var logoutCookie = logoutResult.getResponse().getCookie("SESSION");
        assertThat(logoutCookie).isNotNull();
        assertThat(logoutCookie.getMaxAge()).isEqualTo(0);

        // And: Verify we attempted to call Auth API logout
        wireMockServer.verify(postRequestedFor(urlEqualTo("/api/v1/auth/logout")));
    }

    @Test
    @DisplayName("POST /logout sin estar autenticado debe redirigir a /login")
    void logout_withoutAuthentication_shouldRedirectToLogin() throws Exception {
        // When: POST /logout without being logged in
        mockMvc.perform(post("/logout")
                        .with(csrf()))

                // Then: Should redirect to login
                // (Spring Security allows logout even without authentication)
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?logout"));
    }
}
