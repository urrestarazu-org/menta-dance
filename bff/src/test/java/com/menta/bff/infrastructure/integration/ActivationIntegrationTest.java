package com.menta.bff.infrastructure.integration;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.ThrowableProxyUtil;
import ch.qos.logback.core.read.ListAppender;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.http.Fault;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

/**
 * Account activation through the rendered pages. The field carrying the token is
 * located in the HTML, never hard-coded, so a template/controller mismatch breaks
 * the build (the class of bug behind #490). The token is a credential: these tests
 * also pin where it may and may not appear.
 */
@DisplayName("Activation Integration Tests")
class ActivationIntegrationTest extends BaseIntegrationTest {

    private static final String TOKEN = "activation-token-abc";
    private static final String VALIDATE_URL = "/api/v1/auth/activate/" + TOKEN;
    private static final String ACTIVATE_URL = "/api/v1/auth/activate";
    private static final Pattern INPUT = Pattern.compile("<input[^>]*>");
    private static final Pattern NAME = Pattern.compile("name=\"([^\"]+)\"");
    private static final String LOGIN_REDIRECT = "http://localhost/login";

    private static void stubValidation(int status) {
        WIRE_MOCK_SERVER.stubFor(WireMock.get(urlEqualTo(VALIDATE_URL)).willReturn(aResponse().withStatus(status)));
    }

    private static void stubActivation(int status) {
        WIRE_MOCK_SERVER.stubFor(WireMock.post(urlEqualTo(ACTIVATE_URL)).willReturn(aResponse().withStatus(status)));
    }

    private String activationPage() throws Exception {
        return mockMvc.perform(get("/activar/" + TOKEN))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(header().string("Referrer-Policy", "no-referrer"))
                .andReturn().getResponse().getContentAsString();
    }

    /** Name of the form field whose value is the token, read from the rendered HTML. */
    private static String tokenFieldName(String html) {
        Matcher input = INPUT.matcher(html);
        while (input.find()) {
            if (input.group().contains("value=\"" + TOKEN + "\"")) {
                Matcher name = NAME.matcher(input.group());
                assertThat(name.find()).isTrue();
                return name.group(1);
            }
        }
        throw new AssertionError("The rendered page has no field carrying the token");
    }

    @Test
    @DisplayName("a valid link renders the Activar cuenta button, carrying the token only in a hidden field")
    void validLinkRendersTheButton() throws Exception {
        stubValidation(204);

        String html = activationPage();

        assertThat(html).contains("Activar cuenta").contains("name=\"_csrf\"").contains("action=\"/activar\"");
        assertThat(html.replace("value=\"" + TOKEN + "\"", "")).doesNotContain(TOKEN);
        WIRE_MOCK_SERVER.verify(getRequestedFor(urlEqualTo(VALIDATE_URL)));
    }

    @Test
    @DisplayName("an invalid, expired or used link shows the Spanish message, no button and no echo of the token")
    void invalidLinkShowsTheInvalidState() throws Exception {
        stubValidation(400);

        String body = mockMvc.perform(get("/activar/" + TOKEN))
                .andExpect(status().isBadRequest())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(header().string("Referrer-Policy", "no-referrer"))
                .andReturn().getResponse().getContentAsString();

        assertThat(body).contains("inválido").doesNotContain("<form").doesNotContain(TOKEN)
                .doesNotContain("reenviar");
    }

    @Test
    @DisplayName("submitting the rendered form activates the account and redirects without the token in the URL")
    void submittingTheRenderedFormActivatesTheAccount() throws Exception {
        stubValidation(204);
        stubActivation(204);
        String html = activationPage();

        mockMvc.perform(post("/activar").param(tokenFieldName(html), TOKEN).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/cuenta-activada"));

        WIRE_MOCK_SERVER.verify(postRequestedFor(urlEqualTo(ACTIVATE_URL))
                .withRequestBody(matchingJsonPath("$.token", equalTo(TOKEN))));
    }

    @Test
    @DisplayName("an API rate limit or outage re-renders the button page with a Spanish message")
    void apiFailuresReRenderTheButtonPage() throws Exception {
        stubValidation(204);
        String field = tokenFieldName(activationPage());

        stubActivation(429);
        String limited = mockMvc.perform(post("/activar").param(field, TOKEN).with(csrf()))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andReturn().getResponse().getContentAsString();
        assertThat(limited).contains("demasiados intentos").contains("Activar cuenta");

        stubActivation(503);
        String unavailable = mockMvc.perform(post("/activar").param(field, TOKEN).with(csrf()))
                .andExpect(status().isServiceUnavailable())
                .andReturn().getResponse().getContentAsString();
        assertThat(unavailable).contains("No pudimos completar la operación").contains("Activar cuenta");
        assertThat(tokenFieldName(unavailable)).isEqualTo(field);
    }

    @Test
    @DisplayName("posting an invalid token or none shows the invalid state")
    void invalidSubmissionShowsTheInvalidState() throws Exception {
        stubActivation(400);

        String body = mockMvc.perform(post("/activar").param("token", TOKEN).with(csrf()))
                .andExpect(status().isBadRequest())
                .andReturn().getResponse().getContentAsString();
        assertThat(body).contains("inválido").doesNotContain("<form").doesNotContain(TOKEN);

        WIRE_MOCK_SERVER.resetRequests();
        mockMvc.perform(post("/activar").with(csrf())).andExpect(status().isBadRequest());
        assertThat(WIRE_MOCK_SERVER.findAll(postRequestedFor(urlEqualTo(ACTIVATE_URL)))).isEmpty();
    }

    @Test
    @DisplayName("posting without a CSRF token is rejected")
    void postWithoutCsrfIsRejected() throws Exception {
        mockMvc.perform(post("/activar").param("token", TOKEN)).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("the success screen is public and offers the login")
    void successScreenIsPublic() throws Exception {
        String body = mockMvc.perform(get("/cuenta-activada"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(body).contains("Tu cuenta fue activada").contains("href=\"/login\"");
    }

    @Test
    @DisplayName("only the exact activation routes and verbs are public: nothing around them opens up")
    void onlyTheActivationRoutesArePublic() throws Exception {
        mockMvc.perform(get("/activar/a/b")).andExpect(redirectedUrl(LOGIN_REDIRECT));
        mockMvc.perform(get("/activar")).andExpect(redirectedUrl(LOGIN_REDIRECT));
        mockMvc.perform(get("/activar-cuenta")).andExpect(redirectedUrl(LOGIN_REDIRECT));
        mockMvc.perform(post("/activar/" + TOKEN).with(csrf())).andExpect(redirectedUrl(LOGIN_REDIRECT));
        mockMvc.perform(put("/activar").with(csrf())).andExpect(redirectedUrl(LOGIN_REDIRECT));
        mockMvc.perform(post("/cuenta-activada").with(csrf())).andExpect(redirectedUrl(LOGIN_REDIRECT));
        mockMvc.perform(get("/cuenta-activada/x")).andExpect(redirectedUrl(LOGIN_REDIRECT));
    }

    @Test
    @DisplayName("the token never reaches the application logs, whatever the API answers")
    void tokenIsNeverLogged() throws Exception {
        Logger root = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        root.addAppender(appender);
        try {
            stubValidation(204);
            mockMvc.perform(get("/activar/" + TOKEN));
            stubValidation(400);
            mockMvc.perform(get("/activar/" + TOKEN));
            stubValidation(503);
            mockMvc.perform(get("/activar/" + TOKEN));
            WIRE_MOCK_SERVER.stubFor(WireMock.get(urlEqualTo(VALIDATE_URL))
                    .willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)));
            mockMvc.perform(get("/activar/" + TOKEN));
            stubActivation(429);
            mockMvc.perform(post("/activar").param("token", TOKEN).with(csrf()));
            stubActivation(204);
            mockMvc.perform(post("/activar").param("token", TOKEN).with(csrf()));
        } finally {
            root.detachAppender(appender);
        }

        assertThat(appender.list).isNotEmpty();
        assertThat(appender.list).noneMatch(event -> (event.getFormattedMessage() + (event.getThrowableProxy() == null
                ? "" : ThrowableProxyUtil.asString(event.getThrowableProxy()))).contains(TOKEN));
    }
}
