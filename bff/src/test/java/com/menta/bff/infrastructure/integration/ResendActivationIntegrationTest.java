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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.http.Fault;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Resending the activation email through the rendered {@code /reenviar-activacion}
 * page. The field name is derived from the HTML, never hard-coded, so a
 * template/controller mismatch breaks the build (the class of bug behind #490).
 */
@DisplayName("Resend Activation Integration Tests")
class ResendActivationIntegrationTest extends BaseIntegrationTest {

    private static final String RESEND_URL = "/api/v1/auth/resend-activation";
    private static final String PAGE = "/reenviar-activacion";
    private static final String EMAIL = "user@example.com";
    private static final Pattern EMAIL_FIELD = Pattern.compile("<input[^>]*type=\"email\"[^>]*name=\"([^\"]+)\"");
    private static final Pattern HREF = Pattern.compile("<a[^>]*href=\"([^\"]+)\"");
    private static final String LOGIN_REDIRECT = "http://localhost/login";

    private String formPage() throws Exception {
        return mockMvc.perform(get(PAGE))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    private static String emailField(String html) {
        Matcher matcher = EMAIL_FIELD.matcher(html);
        assertThat(matcher.find()).withFailMessage("The rendered page has no email input").isTrue();
        return matcher.group(1);
    }

    private static void stubResend(int status) {
        WIRE_MOCK_SERVER.stubFor(WireMock.post(urlEqualTo(RESEND_URL)).willReturn(aResponse().withStatus(status)));
    }

    @Test
    @DisplayName("the anonymous form carries the CSRF token")
    void formHasCsrf() throws Exception {
        assertThat(formPage()).contains("name=\"_csrf\"");
    }

    @Test
    @DisplayName("submitting the rendered form resends and redirects to a pending screen that carries no email")
    void submittingTheRenderedFormRedirectsToPending() throws Exception {
        stubResend(202);
        String html = formPage();

        mockMvc.perform(post(PAGE).param(emailField(html), EMAIL).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/registro/pendiente"));

        WIRE_MOCK_SERVER.verify(postRequestedFor(urlEqualTo(RESEND_URL))
                .withRequestBody(matchingJsonPath("$.email", equalTo(EMAIL))));
    }

    @Test
    @DisplayName("an invalid email shows a Spanish error next to the form and never reaches the API")
    void invalidEmailNeverReachesTheApi() throws Exception {
        String body = mockMvc.perform(post(PAGE).param("email", "no-es-un-email").with(csrf()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(body).contains("El formato del email es inválido");
        assertThat(WIRE_MOCK_SERVER.findAll(postRequestedFor(urlEqualTo(RESEND_URL)))).isEmpty();
    }

    @Test
    @DisplayName("an API 400 shows a Spanish message without API internals")
    void api400ShowsSpanishMessage() throws Exception {
        WIRE_MOCK_SERVER.stubFor(WireMock.post(urlEqualTo(RESEND_URL)).willReturn(aResponse().withStatus(400)
                .withBody("{\"code\":\"INVALID_ACTIVATION_REQUEST\"}")));

        String body = mockMvc.perform(post(PAGE).param("email", EMAIL).with(csrf()))
                .andExpect(status().isBadRequest())
                .andReturn().getResponse().getContentAsString();

        assertThat(body).contains("No pudimos procesar los datos").doesNotContain("INVALID_ACTIVATION_REQUEST");
    }

    @Test
    @DisplayName("a rate-limited resend tells how long to wait, without API internals")
    void rateLimitedShowsTheWait() throws Exception {
        WIRE_MOCK_SERVER.stubFor(WireMock.post(urlEqualTo(RESEND_URL))
                .willReturn(aResponse().withStatus(429).withHeader("Retry-After", "120")
                        .withBody("{\"code\":\"ACTIVATION_RATE_LIMITED\"}")));

        String body = mockMvc.perform(post(PAGE).param("email", EMAIL).with(csrf()))
                .andExpect(status().isTooManyRequests())
                .andReturn().getResponse().getContentAsString();

        assertThat(body).contains("demasiados intentos").contains("2 minutos")
                .doesNotContain("ACTIVATION_RATE_LIMITED");
    }

    @Test
    @DisplayName("a degraded or unreachable Auth API is a 503 page, never a 500")
    void unavailableIsA503() throws Exception {
        stubResend(503);
        mockMvc.perform(post(PAGE).param("email", EMAIL).with(csrf())).andExpect(status().isServiceUnavailable());

        WIRE_MOCK_SERVER.stubFor(WireMock.post(urlEqualTo(RESEND_URL))
                .willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)));
        mockMvc.perform(post(PAGE).param("email", EMAIL).with(csrf())).andExpect(status().isServiceUnavailable());
    }

    @Test
    @DisplayName("posting without a CSRF token is rejected")
    void postWithoutCsrfIsRejected() throws Exception {
        mockMvc.perform(post(PAGE).param("email", EMAIL)).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("only GET and POST on the exact path are public")
    void onlyTheExactRoutesArePublic() throws Exception {
        mockMvc.perform(get(PAGE + "/otra-cosa")).andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl(LOGIN_REDIRECT));
        mockMvc.perform(put(PAGE).with(csrf())).andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl(LOGIN_REDIRECT));
    }

    @Test
    @DisplayName("every onboarding page links to the resend form, and no onboarding link is dead")
    void onboardingLinksAreAliveAndReachResend() throws Exception {
        WIRE_MOCK_SERVER.stubFor(WireMock.get(urlEqualTo("/api/v1/auth/activate/expired-token"))
                .willReturn(aResponse().withStatus(400)));
        List<String> pages = List.of(
                mockMvc.perform(get("/registro")).andReturn().getResponse().getContentAsString(),
                mockMvc.perform(get("/registro/pendiente")).andReturn().getResponse().getContentAsString(),
                mockMvc.perform(get("/activar/expired-token")).andReturn().getResponse().getContentAsString(),
                formPage());

        for (String html : pages) {
            Matcher links = HREF.matcher(html);
            int count = 0;
            while (links.find()) {
                count++;
                int httpStatus = mockMvc.perform(get(links.group(1))).andReturn().getResponse().getStatus();
                assertThat(httpStatus).withFailMessage("Dead link %s -> %s", links.group(1), httpStatus)
                        .isLessThan(400);
            }
            assertThat(count).isPositive();
        }
        assertThat(pages.subList(0, 3)).allSatisfy(html -> assertThat(html).contains("href=\"" + PAGE + "\""));
    }
}
