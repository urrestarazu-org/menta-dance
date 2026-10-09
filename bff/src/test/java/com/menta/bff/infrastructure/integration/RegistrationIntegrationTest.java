package com.menta.bff.infrastructure.integration;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.github.tomakehurst.wiremock.client.WireMock;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Registration through the rendered {@code /registro} page. Field names are
 * derived from the HTML, never hard-coded, so a template/controller mismatch
 * breaks the build (the class of bug behind #490).
 */
@DisplayName("Registration Integration Tests")
class RegistrationIntegrationTest extends BaseIntegrationTest {

    private static final String REGISTER_URL = "/api/v1/auth/register";
    private static final Pattern EMAIL_FIELD = Pattern.compile("<input[^>]*type=\"email\"[^>]*name=\"([^\"]+)\"");
    private static final Pattern PASSWORD_FIELD =
            Pattern.compile("<input[^>]*type=\"password\"[^>]*name=\"([^\"]+)\"");

    private String registerPage() throws Exception {
        return mockMvc.perform(get("/registro"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    private static String fieldName(Pattern pattern, String html) {
        Matcher matcher = pattern.matcher(html);
        assertThat(matcher.find()).withFailMessage("The rendered page has no input matching %s", pattern).isTrue();
        return matcher.group(1);
    }

    @Test
    @DisplayName("the anonymous form carries the CSRF token and exposes no role field")
    void formHasCsrfAndNoRoleField() throws Exception {
        String html = registerPage();

        assertThat(html).contains("name=\"_csrf\"");
        assertThat(html.toLowerCase()).doesNotContain("role").doesNotContain("rol\"");
    }

    @Test
    @DisplayName("submitting the rendered form registers a STUDENT and redirects to the pending screen")
    void submittingTheRenderedFormRegistersAStudent() throws Exception {
        WIRE_MOCK_SERVER.stubFor(WireMock.post(urlEqualTo(REGISTER_URL)).willReturn(aResponse().withStatus(202)));
        String html = registerPage();

        mockMvc.perform(post("/registro")
                        .param(fieldName(EMAIL_FIELD, html), "ana@example.com")
                        .param(fieldName(PASSWORD_FIELD, html), "Secreta123")
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/registro/pendiente"));

        WIRE_MOCK_SERVER.verify(postRequestedFor(urlEqualTo(REGISTER_URL))
                .withRequestBody(matchingJsonPath("$.email", equalTo("ana@example.com")))
                .withRequestBody(matchingJsonPath("$.role", equalTo("STUDENT"))));
    }

    @Test
    @DisplayName("invalid input shows Spanish errors next to the fields and never reaches the API")
    void invalidInputShowsSpanishFieldErrors() throws Exception {
        String body = mockMvc.perform(post("/registro")
                        .param("email", "no-es-un-email")
                        .param("password", "corta")
                        .with(csrf()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(body).contains("El formato del email es inválido")
                .contains("al menos 8 caracteres")
                .contains("una mayúscula")
                .contains("un número");
        assertThat(WIRE_MOCK_SERVER.findAll(postRequestedFor(urlEqualTo(REGISTER_URL)))).isEmpty();
    }

    @Test
    @DisplayName("a rate-limited registration shows the Spanish 429 message without API internals")
    void rateLimitedRegistrationShowsSpanishMessage() throws Exception {
        WIRE_MOCK_SERVER.stubFor(WireMock.post(urlEqualTo(REGISTER_URL))
                .willReturn(aResponse().withStatus(429).withHeader("Retry-After", "60")
                        .withBody("{\"code\":\"ACTIVATION_RATE_LIMITED\"}")));

        String body = mockMvc.perform(post("/registro")
                        .param("email", "ana@example.com")
                        .param("password", "Secreta123")
                        .with(csrf()))
                .andExpect(status().isTooManyRequests())
                .andReturn().getResponse().getContentAsString();

        assertThat(body).contains("demasiados intentos").doesNotContain("ACTIVATION_RATE_LIMITED");
    }

    @Test
    @DisplayName("the pending screen is public and never reveals whether the email already existed")
    void pendingScreenIsNeutral() throws Exception {
        String body = mockMvc.perform(get("/registro/pendiente"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(body).contains("Revisá tu correo").doesNotContain("ya está registrado").doesNotContain("@");
    }

    @Test
    @DisplayName("posting without a CSRF token is rejected")
    void postWithoutCsrfIsRejected() throws Exception {
        mockMvc.perform(post("/registro").param("email", "ana@example.com").param("password", "Secreta123"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("only the registration routes are public: nothing beneath them opens up")
    void onlyRegistrationRoutesArePublic() throws Exception {
        mockMvc.perform(get("/registro/otra-cosa")).andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("http://localhost/login"));
        mockMvc.perform(post("/registro/pendiente").with(csrf())).andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("http://localhost/login"));
    }
}
