package com.menta.bff.infrastructure.integration;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.github.tomakehurst.wiremock.client.WireMock;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Where each role lands after POST /login (#354): staff on the management home, everyone else on the
 * dashboard, and the remembered "Suscribirme" entry point (#361) still wins.
 */
@DisplayName("Landing after login Integration Tests")
class ManagementLandingIntegrationTest extends BaseIntegrationTest {

    private static void stubLogin(String role) {
        WIRE_MOCK_SERVER.stubFor(WireMock.post(urlEqualTo("/api/v1/auth/login")).willReturn(aResponse()
                .withStatus(200).withHeader("Content-Type", "application/json")
                .withHeader("X-Refresh-Token", "refresh-token-abc")
                .withBody("{\"access_token\": \"%s\", \"token_type\": \"Bearer\", \"expires_in\": 3600}"
                        .formatted(TestAccessTokens.withRole(role)))));
    }

    private MvcResult login(String role, Cookie anonymousSession) throws Exception {
        stubLogin(role);
        var request = post("/login").param("email", "user@example.com").param("password", "Password1")
                .with(csrf());
        if (anonymousSession != null) {
            request.cookie(anonymousSession);
        }
        return mockMvc.perform(request).andExpect(status().is3xxRedirection()).andReturn();
    }

    @ParameterizedTest(name = "{0} lands on {1}")
    @CsvSource({
        "ADMIN, /gestion",
        "INSTRUCTOR, /gestion",
        "RECEPTIONIST, /gestion",
        "STUDENT, /dashboard"
    })
    @DisplayName("a direct login lands on the home of the role")
    void directLoginLandsByRole(String role, String expected) throws Exception {
        assertThat(login(role, null).getResponse().getRedirectedUrl()).isEqualTo(expected);
    }

    @Test
    @DisplayName("the landing page of a staff member opens right after the login")
    void staffLandingOpens() throws Exception {
        Cookie session = login("INSTRUCTOR", null).getResponse().getCookie("SESSION");

        mockMvc.perform(get("/gestion").cookie(session)).andExpect(status().isOk());
    }

    @ParameterizedTest(name = "{0} keeps the remembered Suscribirme entry")
    @CsvSource({"STUDENT", "ADMIN"})
    @DisplayName("the remembered /plans/suscribirme entry point wins over the role landing")
    void savedRequestStillWins(String role) throws Exception {
        MvcResult entry = mockMvc.perform(get("/plans/suscribirme")).andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login")).andReturn();
        Cookie anonymousSession = entry.getResponse().getCookie("SESSION");
        assertThat(anonymousSession).isNotNull();

        String target = login(role, anonymousSession).getResponse().getRedirectedUrl();

        assertThat(target).startsWith("http://localhost/plans/suscribirme")
                .doesNotContain("/gestion").doesNotContain("/dashboard");
    }

    @Test
    @DisplayName("another protected page is not remembered: staff still land on /gestion")
    void otherPagesAreNotRemembered() throws Exception {
        MvcResult entry = mockMvc.perform(get("/gestion/billing")).andExpect(status().is3xxRedirection())
                .andReturn();
        Cookie anonymousSession = entry.getResponse().getCookie("SESSION");

        assertThat(login("ADMIN", anonymousSession).getResponse().getRedirectedUrl()).isEqualTo("/gestion");
    }
}
