package com.menta.bff.infrastructure.integration;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.notMatching;
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
import org.springframework.test.web.servlet.ResultActions;

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
    private static final String PASSWORD = "Secreta123";
    private static final Pattern PASSWORD_INPUT = Pattern.compile("<input[^>]*type=\"password\"[^>]*>");

    private static void stubValidation(int status) {
        WIRE_MOCK_SERVER.stubFor(WireMock.get(urlEqualTo(VALIDATE_URL)).willReturn(aResponse().withStatus(status)));
    }

    private static void stubActivation(int status) {
        WIRE_MOCK_SERVER.stubFor(WireMock.post(urlEqualTo(ACTIVATE_URL)).willReturn(aResponse().withStatus(status)));
    }

    private static void stubPasswordRequired() {
        WIRE_MOCK_SERVER.stubFor(WireMock.get(urlEqualTo(VALIDATE_URL)).willReturn(aResponse().withStatus(200)
                .withHeader("Content-Type", "application/json").withBody("{\"passwordRequired\":true}")));
    }

    private static void stubActivationProblem(String code) {
        WIRE_MOCK_SERVER.stubFor(WireMock.post(urlEqualTo(ACTIVATE_URL)).willReturn(aResponse().withStatus(400)
                .withHeader("Content-Type", "application/problem+json")
                .withBody("{\"status\":400,\"code\":\"" + code + "\",\"detail\":\"Detalle crudo de la API\"}")));
    }

    private ResultActions submitPassword(String password, String confirmation)
            throws Exception {
        return mockMvc.perform(post("/activar").param("token", TOKEN)
                .param("password", password).param("passwordConfirmation", confirmation).with(csrf()));
    }

    private static String inputTag(String html, String id) {
        Matcher input = Pattern.compile("<input[^>]*\\bid=\"" + id + "\"[^>]*>").matcher(html);
        assertThat(input.find()).as("an <input id=\"%s\"> in the page", id).isTrue();
        return input.group();
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
                .contains("href=\"/reenviar-activacion\"");
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

    @Test
    @DisplayName("a link of an account that needs a password asks for it and for its repetition, accessibly")
    void passwordRequiredLinkRendersTheAccessiblePasswordForm() throws Exception {
        stubPasswordRequired();

        String html = activationPage();

        assertThat(inputTag(html, "password")).contains("type=\"password\"").contains("name=\"password\"")
                .contains("autocomplete=\"new-password\"").contains("required")
                .contains("aria-describedby=\"password-help\"").doesNotContain("aria-invalid")
                .doesNotContainPattern("value=\"[^\"]");
        assertThat(inputTag(html, "passwordConfirmation")).contains("type=\"password\"")
                .contains("autocomplete=\"new-password\"").contains("required").doesNotContainPattern("value=\"[^\"]");
        assertThat(html).containsPattern("<label[^>]*for=\"password\"[^>]*>Contraseña</label>")
                .containsPattern("<label[^>]*for=\"passwordConfirmation\"[^>]*>Repetí la contraseña</label>")
                .containsPattern("<div[^>]*id=\"password-help\"[^>]*>\\s*<p[^>]*>Al menos 8 caracteres, "
                        + "una mayúscula y un número.</p>")
                .contains("name=\"_csrf\"").contains("action=\"/activar\"").contains("Activar cuenta");
        assertThat(PASSWORD_INPUT.matcher(html).results().count()).isEqualTo(2);
        assertThat(html.replace("value=\"" + TOKEN + "\"", "")).doesNotContain(TOKEN);
    }

    @Test
    @DisplayName("a link of a regular account renders exactly the page it always did: no password fields")
    void regularLinkHasNoPasswordFields() throws Exception {
        stubValidation(204);

        String html = activationPage();

        assertThat(html).doesNotContain("type=\"password\"").doesNotContain("data-error-summary");
        assertThat(html).contains("Confirmá la activación de tu cuenta");
    }

    @Test
    @DisplayName("submitting the chosen password sends it to the API in the body and redirects without it in the URL")
    void submittingTheChosenPasswordActivatesTheAccount() throws Exception {
        stubPasswordRequired();
        stubActivation(204);
        String html = activationPage();

        mockMvc.perform(post("/activar").param(tokenFieldName(html), TOKEN)
                        .param("password", PASSWORD).param("passwordConfirmation", PASSWORD).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/cuenta-activada"));

        WIRE_MOCK_SERVER.verify(postRequestedFor(urlEqualTo(ACTIVATE_URL))
                .withRequestBody(matchingJsonPath("$.token", equalTo(TOKEN)))
                .withRequestBody(matchingJsonPath("$.password", equalTo(PASSWORD))));
    }

    @Test
    @DisplayName("a regular activation never sends a password")
    void regularActivationSendsNoPassword() throws Exception {
        stubActivation(204);

        mockMvc.perform(post("/activar").param("token", TOKEN).with(csrf()))
                .andExpect(redirectedUrl("/cuenta-activada"));

        WIRE_MOCK_SERVER.verify(postRequestedFor(urlEqualTo(ACTIVATE_URL))
                .withRequestBody(notMatching(".*password.*")));
    }

    @Test
    @DisplayName("a weak or unconfirmed password shows an error summary and field errors without calling the API")
    void localPasswordErrorsAreAccessibleAndNeverEchoPasswords() throws Exception {
        String weak = page(submitPassword("corta", "corta").andExpect(status().isOk()));
        String mismatch = page(submitPassword(PASSWORD, "Otra12345").andExpect(status().isOk()));

        assertThat(weak).contains("data-error-summary").contains("Hay 3 errores en el formulario")
                .contains("al menos 8 caracteres").contains("al menos una mayúscula").contains("al menos un número")
                .contains("href=\"#password\"").doesNotContain("value=\"corta\"");
        assertThat(inputTag(weak, "password")).contains("aria-invalid=\"true\"")
                .contains("aria-describedby=\"password-help\"").doesNotContainPattern("value=\"[^\"]");
        assertThat(mismatch).contains("Las contraseñas no coinciden").contains("href=\"#passwordConfirmation\"")
                .doesNotContain(PASSWORD).doesNotContain("Otra12345");
        assertThat(inputTag(mismatch, "passwordConfirmation")).contains("aria-invalid=\"true\"")
                .contains("aria-describedby=\"passwordConfirmation-help\"").doesNotContainPattern("value=\"[^\"]");
        assertThat(tokenFieldName(mismatch)).isEqualTo("token");
        assertDescribedByTargetsExist(weak);
        assertDescribedByTargetsExist(mismatch);
        assertThat(WIRE_MOCK_SERVER.findAll(postRequestedFor(urlEqualTo(ACTIVATE_URL)))).isEmpty();
    }

    @Test
    @DisplayName("a password the API refuses as weak or missing re-renders the form in Spanish, not the API body")
    void apiPasswordVerdictsAreShownInSpanishAndKeepTheLink() throws Exception {
        stubActivationProblem("WEAK_PASSWORD");
        String weak = page(submitPassword(PASSWORD, PASSWORD).andExpect(status().isOk()));
        stubActivationProblem("PASSWORD_REQUIRED");
        String required = page(mockMvc.perform(post("/activar").param("token", TOKEN).with(csrf()))
                .andExpect(status().isOk()));

        assertThat(weak).contains("no cumple los requisitos").contains("data-error-summary")
                .doesNotContain("WEAK_PASSWORD").doesNotContain("Detalle crudo").doesNotContain(PASSWORD);
        assertThat(required).contains("Elegí una contraseña para activar tu cuenta")
                .contains("type=\"password\"").doesNotContain("PASSWORD_REQUIRED").doesNotContain("Detalle crudo");
        for (String html : new String[] {weak, required}) {
            assertThat(tokenFieldName(html)).isEqualTo("token");
            assertThat(inputTag(html, "password")).contains("aria-invalid=\"true\"")
                    .doesNotContainPattern("value=\"[^\"]");
            assertThat(inputTag(html, "passwordConfirmation")).doesNotContainPattern("value=\"[^\"]");
            assertThat(html).contains("name=\"_csrf\"");
            assertDescribedByTargetsExist(html);
        }
    }

    @Test
    @DisplayName("a password submitted with a token the API rejects shows the invalid state without the form")
    void invalidTokenWithPasswordShowsTheInvalidState() throws Exception {
        stubActivationProblem("ACTIVATION_TOKEN_INVALID");

        String body = page(submitPassword(PASSWORD, PASSWORD).andExpect(status().isBadRequest()));

        assertThat(body).contains("inválido").doesNotContain("<form").doesNotContain(TOKEN).doesNotContain(PASSWORD);
    }

    @Test
    @DisplayName("an API rate limit or outage keeps the password form with empty password fields")
    void apiFailuresKeepThePasswordForm() throws Exception {
        stubActivation(429);
        String limited = page(submitPassword(PASSWORD, PASSWORD).andExpect(status().isTooManyRequests()));
        stubActivation(503);
        String unavailable = page(submitPassword(PASSWORD, PASSWORD).andExpect(status().isServiceUnavailable()));

        assertThat(limited).contains("demasiados intentos");
        assertThat(unavailable).contains("No pudimos completar la operación");
        for (String html : new String[] {limited, unavailable}) {
            assertThat(PASSWORD_INPUT.matcher(html).results().count()).isEqualTo(2);
            assertThat(html).doesNotContain(PASSWORD);
            assertThat(tokenFieldName(html)).isEqualTo("token");
        }
    }

    @Test
    @DisplayName("posting a password without a CSRF token is rejected")
    void passwordPostWithoutCsrfIsRejected() throws Exception {
        mockMvc.perform(post("/activar").param("token", TOKEN).param("password", PASSWORD)
                        .param("passwordConfirmation", PASSWORD))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("neither the token nor the password reach the application logs on the password flow")
    void passwordIsNeverLogged() throws Exception {
        Logger root = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        root.addAppender(appender);
        try {
            stubPasswordRequired();
            mockMvc.perform(get("/activar/" + TOKEN));
            submitPassword("xyzzy", "xyzzy");
            stubActivationProblem("WEAK_PASSWORD");
            submitPassword(PASSWORD, PASSWORD);
            stubActivationProblem("PASSWORD_REQUIRED");
            submitPassword(PASSWORD, PASSWORD);
            stubActivation(429);
            submitPassword(PASSWORD, PASSWORD);
            stubActivation(503);
            submitPassword(PASSWORD, PASSWORD);
            WIRE_MOCK_SERVER.stubFor(WireMock.post(urlEqualTo(ACTIVATE_URL))
                    .willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)));
            submitPassword(PASSWORD, PASSWORD);
            stubActivation(204);
            submitPassword(PASSWORD, PASSWORD);
        } finally {
            root.detachAppender(appender);
        }

        assertThat(appender.list).isNotEmpty();
        for (String secret : new String[] {TOKEN, PASSWORD, "xyzzy"}) {
            assertThat(appender.list).noneMatch(event -> (event.getFormattedMessage()
                    + (event.getThrowableProxy() == null ? "" : ThrowableProxyUtil.asString(event.getThrowableProxy())))
                    .contains(secret));
        }
    }

    private static String page(ResultActions result) throws Exception {
        return result.andReturn().getResponse().getContentAsString();
    }

    private static void assertDescribedByTargetsExist(String html) {
        Matcher references = Pattern.compile("aria-describedby=\"([^\"]+)\"").matcher(html);
        int checked = 0;
        while (references.find()) {
            checked++;
            assertThat(html).as("target of %s", references.group()).contains("id=\"" + references.group(1) + "\"");
        }
        assertThat(checked).isPositive();
    }
}
