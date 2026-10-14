package com.menta.bff.infrastructure.integration;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.github.tomakehurst.wiremock.client.WireMock;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Login return of the anonymous "Suscribirme" action (#361, spec {@code bff-plans-view}): only the
 * subscribe entry point is remembered across the login, every other login still lands on the
 * dashboard and no caller-supplied target is ever followed.
 */
@DisplayName("Plans login return integration tests")
class PlansLoginReturnIntegrationTest extends BaseIntegrationTest {

    private static final String ENTRY = "/plans/suscribirme";

    @BeforeEach
    void stubLogin() {
        WIRE_MOCK_SERVER.stubFor(WireMock.post(urlEqualTo("/api/v1/auth/login")).willReturn(aResponse()
                .withStatus(200).withHeader("Content-Type", "application/json")
                .withHeader("X-Refresh-Token", "refresh-token-abc")
                .withBody("{\"access_token\": \"%s\", \"token_type\": \"Bearer\", \"expires_in\": 3600}"
                        .formatted(TestAccessTokens.withRole("STUDENT")))));
    }

    private MvcResult login(Cookie anonymousSession) throws Exception {
        var request = post("/login").param("email", "user@example.com").param("password", "Password1")
                .with(csrf());
        if (anonymousSession != null) {
            request.cookie(anonymousSession);
        }
        return mockMvc.perform(request).andExpect(status().is3xxRedirection()).andReturn();
    }

    @Test
    @DisplayName("anonymous Suscribirme goes to login and, once logged in, ends on /plans")
    void anonymousReturnsToPlansAfterLogin() throws Exception {
        MvcResult entry = mockMvc.perform(get(ENTRY)).andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login")).andReturn();
        Cookie anonymousSession = entry.getResponse().getCookie("SESSION");
        assertThat(anonymousSession).isNotNull();

        MvcResult login = login(anonymousSession);

        String target = login.getResponse().getRedirectedUrl();
        assertThat(target).startsWith("http://localhost" + ENTRY).doesNotContain("/dashboard");
        Cookie session = login.getResponse().getCookie("SESSION");
        assertThat(session).isNotNull();
        mockMvc.perform(get(target.replace("http://localhost", "")).cookie(session))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/plans"));
    }

    @Test
    @DisplayName("a login with no saved request still lands on /dashboard")
    void directLoginLandsOnDashboard() throws Exception {
        assertThat(login(null).getResponse().getRedirectedUrl()).isEqualTo("/dashboard");
    }

    @Test
    @DisplayName("another protected page is not remembered: login lands on /dashboard")
    void otherProtectedPageIsNotRemembered() throws Exception {
        MvcResult entry = mockMvc.perform(get("/compras/retorno")).andExpect(status().is3xxRedirection())
                .andReturn();
        Cookie anonymousSession = entry.getResponse().getCookie("SESSION");

        assertThat(login(anonymousSession).getResponse().getRedirectedUrl()).isEqualTo("/dashboard");
    }

    @Test
    @DisplayName("a caller-supplied target is never followed: the return stays on this site's entry path")
    void noOpenRedirect() throws Exception {
        MvcResult entry = mockMvc.perform(get(ENTRY).param("next", "https://evil.example/steal"))
                .andExpect(status().is3xxRedirection()).andReturn();
        Cookie anonymousSession = entry.getResponse().getCookie("SESSION");

        String target = login(anonymousSession).getResponse().getRedirectedUrl();

        assertThat(target).startsWith("http://localhost" + ENTRY + "?");
        assertThat(java.net.URI.create(target).getHost()).isEqualTo("localhost");
    }

    @Test
    @DisplayName("an authenticated user opening the entry path is sent to /plans")
    void authenticatedEntryRedirectsToPlans() throws Exception {
        Cookie session = login(null).getResponse().getCookie("SESSION");

        mockMvc.perform(get(ENTRY).cookie(session))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/plans"));
    }
}
