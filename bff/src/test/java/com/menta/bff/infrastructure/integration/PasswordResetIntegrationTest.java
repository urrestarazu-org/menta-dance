package com.menta.bff.infrastructure.integration;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.menta.bff.infrastructure.integration.HtmlSections.mainOf;
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
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Password recovery through the rendered pages. Field names are read from the HTML,
 * never hard-coded, so a template/controller mismatch breaks the build (the class of
 * bug behind #490). Token, email and password are credentials: these tests also pin
 * where they may and may not appear.
 */
@DisplayName("Password Reset Integration Tests")
class PasswordResetIntegrationTest extends BaseIntegrationTest {

    private static final String FORGOT_URL = "/api/v1/auth/forgot-password";
    private static final String RESET_URL = "/api/v1/auth/reset-password";
    private static final String REQUEST_PAGE = "/recuperar-contrasena";
    private static final String SENT_PAGE = "/recuperar-contrasena/enviado";
    private static final String RESET_PAGE = "/restablecer-contrasena";
    private static final String TOKEN = "reset-token-abc";
    private static final String EMAIL = "user@example.com";
    private static final String PASSWORD = "Password1";
    private static final Pattern INPUT = Pattern.compile("<input[^>]*>");
    private static final Pattern NAME = Pattern.compile("name=\"([^\"]+)\"");
    private static final Pattern HREF = Pattern.compile("<a[^>]*href=\"([^\"#][^\"]*)\"");
    private static final String LOGIN_REDIRECT = "http://localhost/login";

    private static void stub(String url, int status, String body) {
        WIRE_MOCK_SERVER.stubFor(WireMock.post(urlEqualTo(url)).willReturn(aResponse().withStatus(status)
                .withBody(body)));
    }

    private static String page(ResultActions result) throws Exception {
        return result.andReturn().getResponse().getContentAsString();
    }

    private static List<String> inputs(String html, String attribute) {
        List<String> found = new ArrayList<>();
        Matcher input = INPUT.matcher(html);
        while (input.find()) {
            if (input.group().contains(attribute)) {
                Matcher name = NAME.matcher(input.group());
                assertThat(name.find()).isTrue();
                found.add(name.group(1));
            }
        }
        return found;
    }

    private String resetPage() throws Exception {
        return page(mockMvc.perform(get(RESET_PAGE + "/" + TOKEN))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(header().string("Referrer-Policy", "no-referrer")));
    }

    private ResultActions submitReset(String html, String password, String confirmation) throws Exception {
        List<String> passwords = inputs(html, "type=\"password\"");
        assertThat(passwords).hasSize(2);
        return mockMvc.perform(post(RESET_PAGE)
                .param(inputs(html, "value=\"" + TOKEN + "\"").get(0), TOKEN)
                .param(passwords.get(0), password).param(passwords.get(1), confirmation).with(csrf()));
    }

    private ResultActions submitRequest(String email) throws Exception {
        String html = page(mockMvc.perform(get(REQUEST_PAGE)).andExpect(status().isOk()));
        assertThat(html).contains("name=\"_csrf\"");
        return mockMvc.perform(post(REQUEST_PAGE).param(inputs(html, "type=\"email\"").get(0), email).with(csrf()));
    }

    @Test
    @DisplayName("login offers the recovery link, and the post-reset notice only after a reset")
    void loginLinksToRecoveryAndShowsTheNotice() throws Exception {
        assertThat(page(mockMvc.perform(get("/login")))).contains("href=\"" + REQUEST_PAGE + "\"")
                .doesNotContain("contraseña fue actualizada");
        assertThat(page(mockMvc.perform(get("/login").param("reset", "")))).contains("contraseña fue actualizada");
    }

    @Test
    @DisplayName("requesting a reset redirects to a confirmation without the email, the same for any account")
    void requestRedirectsToTheSameConfirmation() throws Exception {
        stub(FORGOT_URL, 202, "");

        submitRequest(EMAIL).andExpect(status().is3xxRedirection()).andExpect(redirectedUrl(SENT_PAGE));
        submitRequest("other@example.com").andExpect(redirectedUrl(SENT_PAGE));

        WIRE_MOCK_SERVER.verify(postRequestedFor(urlEqualTo(FORGOT_URL))
                .withRequestBody(matchingJsonPath("$.email", equalTo(EMAIL))));
        String body = page(mockMvc.perform(get(SENT_PAGE)).andExpect(status().isOk()));
        assertThat(body).contains("Si existe una cuenta").doesNotContain(EMAIL);
    }

    @Test
    @DisplayName("an invalid email shows a Spanish error and never reaches the API")
    void invalidEmailNeverReachesTheApi() throws Exception {
        String body = page(submitRequest("no-es-un-email").andExpect(status().isOk()));

        assertThat(body).contains("El formato del email es inválido");
        assertThat(WIRE_MOCK_SERVER.findAll(postRequestedFor(urlEqualTo(FORGOT_URL)))).isEmpty();
    }

    @Test
    @DisplayName("request failures: 400 and 429 (with the wait) in Spanish, 503 and transport errors never a 500")
    void requestFailures() throws Exception {
        stub(FORGOT_URL, 400, "{\"code\":\"INVALID_REQUEST\"}");
        assertThat(page(submitRequest(EMAIL).andExpect(status().isBadRequest())))
                .contains("No pudimos procesar").doesNotContain("INVALID_REQUEST");

        WIRE_MOCK_SERVER.stubFor(WireMock.post(urlEqualTo(FORGOT_URL)).willReturn(aResponse().withStatus(429)
                .withHeader("Retry-After", "120")));
        assertThat(page(submitRequest(EMAIL).andExpect(status().isTooManyRequests())))
                .contains("demasiados intentos").contains("2 minutos");

        stub(FORGOT_URL, 503, "");
        submitRequest(EMAIL).andExpect(status().isServiceUnavailable());
        WIRE_MOCK_SERVER.stubFor(WireMock.post(urlEqualTo(FORGOT_URL))
                .willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)));
        submitRequest(EMAIL).andExpect(status().isServiceUnavailable());
    }

    @Test
    @DisplayName("the reset page carries the token only in a hidden field and never in a cache or Referer")
    void resetPageCarriesTheTokenOnlyInAHiddenField() throws Exception {
        String html = resetPage();

        assertThat(html).contains("name=\"_csrf\"").contains("action=\"/restablecer-contrasena\"")
                .contains("Al menos 8 caracteres");
        assertThat(inputs(html, "type=\"hidden\"")).containsAll(inputs(html, "value=\"" + TOKEN + "\""));
        assertThat(html.replace("value=\"" + TOKEN + "\"", "")).doesNotContain(TOKEN);
    }

    @Test
    @DisplayName("submitting the rendered form resets the password and redirects to the login with a notice")
    void submittingTheRenderedFormResets() throws Exception {
        stub(RESET_URL, 200, "");

        submitReset(resetPage(), PASSWORD, PASSWORD)
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/login?reset"));

        WIRE_MOCK_SERVER.verify(postRequestedFor(urlEqualTo(RESET_URL))
                .withRequestBody(matchingJsonPath("$.token", equalTo(TOKEN)))
                .withRequestBody(matchingJsonPath("$.newPassword", equalTo(PASSWORD))));
    }

    @Test
    @DisplayName("a weak or unconfirmed password is rejected next to the field without calling the API")
    void localPasswordRulesNeverReachTheApi() throws Exception {
        String html = resetPage();

        String weak = page(submitReset(html, "corta", "corta").andExpect(status().isOk()));
        assertThat(weak).contains("al menos 8 caracteres").contains("al menos una mayúscula")
                .contains("al menos un número").doesNotContain("value=\"corta\"");
        String mismatch = page(submitReset(html, PASSWORD, "Password2").andExpect(status().isOk()));
        assertThat(mismatch).contains("Las contraseñas no coinciden").doesNotContain(PASSWORD);
        assertThat(inputs(mismatch, "value=\"" + TOKEN + "\"")).hasSize(1);
        assertThat(WIRE_MOCK_SERVER.findAll(postRequestedFor(urlEqualTo(RESET_URL)))).isEmpty();
    }

    @Test
    @DisplayName("an API password rejection shows a Spanish error next to the field and keeps the token")
    void apiPasswordRejectionIsShownNextToTheField() throws Exception {
        String html = resetPage();

        stub(RESET_URL, 400, "{\"code\":\"WEAK_PASSWORD\"}");
        assertThat(page(submitReset(html, PASSWORD, PASSWORD).andExpect(status().isOk())))
                .contains("no cumple los requisitos").doesNotContain("WEAK_PASSWORD");
        stub(RESET_URL, 400, "{\"code\":\"SAME_PASSWORD\"}");
        String same = page(submitReset(html, PASSWORD, PASSWORD).andExpect(status().isOk()));
        assertThat(same).contains("debe ser diferente a la actual").doesNotContain(PASSWORD);
        assertThat(inputs(same, "value=\"" + TOKEN + "\"")).hasSize(1);
    }

    @Test
    @DisplayName("an unknown, expired or used token shows the Spanish message with a link to ask for a new one")
    void unusableTokenOffersANewRequest() throws Exception {
        for (int status : new int[] {404, 410}) {
            stub(RESET_URL, status, "{\"code\":\"PASSWORD_RESET_TOKEN_EXPIRED\"}");
            String body = page(submitReset(resetPage(), PASSWORD, PASSWORD)
                    .andExpect(status().isBadRequest())
                    .andExpect(header().string("Cache-Control", "no-store"))
                    .andExpect(header().string("Referrer-Policy", "no-referrer")));
            assertThat(body).contains("inválido, venció o ya fue utilizado").doesNotContain("<form")
                    .doesNotContain(TOKEN).contains("href=\"" + REQUEST_PAGE + "\"");
        }
    }

    @Test
    @DisplayName("reset failures: 400 and 429 in Spanish keeping the form, 503 and transport errors never a 500")
    void resetFailuresKeepTheForm() throws Exception {
        String html = resetPage();

        stub(RESET_URL, 400, "{\"code\":\"INVALID_REQUEST\"}");
        assertThat(page(submitReset(html, PASSWORD, PASSWORD).andExpect(status().isBadRequest())))
                .contains("No pudimos procesar");
        stub(RESET_URL, 429, "");
        String limited = page(submitReset(html, PASSWORD, PASSWORD).andExpect(status().isTooManyRequests()));
        assertThat(limited).contains("demasiados intentos");
        assertThat(inputs(limited, "value=\"" + TOKEN + "\"")).hasSize(1);
        stub(RESET_URL, 503, "");
        submitReset(html, PASSWORD, PASSWORD).andExpect(status().isServiceUnavailable());
        WIRE_MOCK_SERVER.stubFor(WireMock.post(urlEqualTo(RESET_URL))
                .willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)));
        submitReset(html, PASSWORD, PASSWORD).andExpect(status().isServiceUnavailable());
    }

    @Test
    @DisplayName("posting without a CSRF token is rejected")
    void postWithoutCsrfIsRejected() throws Exception {
        mockMvc.perform(post(REQUEST_PAGE).param("email", EMAIL)).andExpect(status().isForbidden());
        mockMvc.perform(post(RESET_PAGE).param("token", TOKEN)).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("only the exact recovery routes and verbs are public: nothing around them opens up")
    void onlyTheRecoveryRoutesArePublic() throws Exception {
        mockMvc.perform(get(RESET_PAGE)).andExpect(redirectedUrl(LOGIN_REDIRECT));
        mockMvc.perform(get(RESET_PAGE + "/a/b")).andExpect(redirectedUrl(LOGIN_REDIRECT));
        mockMvc.perform(post(RESET_PAGE + "/" + TOKEN).with(csrf())).andExpect(redirectedUrl(LOGIN_REDIRECT));
        mockMvc.perform(put(RESET_PAGE).with(csrf())).andExpect(redirectedUrl(LOGIN_REDIRECT));
        mockMvc.perform(get(REQUEST_PAGE + "/otra-cosa")).andExpect(redirectedUrl(LOGIN_REDIRECT));
        mockMvc.perform(put(REQUEST_PAGE).with(csrf())).andExpect(redirectedUrl(LOGIN_REDIRECT));
        mockMvc.perform(post(SENT_PAGE).with(csrf())).andExpect(redirectedUrl(LOGIN_REDIRECT));
        mockMvc.perform(get(SENT_PAGE + "/x")).andExpect(redirectedUrl(LOGIN_REDIRECT));
    }

    @Test
    @DisplayName("no link of the recovery pages is dead")
    void recoveryLinksAreAlive() throws Exception {
        stub(RESET_URL, 410, "");
        List<String> pages = List.of(page(mockMvc.perform(get("/login"))),
                page(mockMvc.perform(get(REQUEST_PAGE))), page(mockMvc.perform(get(SENT_PAGE))),
                page(submitReset(resetPage(), PASSWORD, PASSWORD)), resetPage());

        for (String html : pages) {
            Matcher links = HREF.matcher(mainOf(html));
            while (links.find()) {
                int httpStatus = mockMvc.perform(get(links.group(1))).andReturn().getResponse().getStatus();
                assertThat(httpStatus).withFailMessage("Dead link %s -> %s", links.group(1), httpStatus)
                        .isLessThan(400);
            }
        }
    }

    @Test
    @DisplayName("neither the token, the email nor the password ever reach the application logs")
    void secretsAreNeverLogged() throws Exception {
        Logger root = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        root.addAppender(appender);
        try {
            String html = resetPage();
            for (int status : new int[] {200, 400, 404, 410, 429, 503}) {
                stub(FORGOT_URL, status, "{\"code\":\"WEAK_PASSWORD\"}");
                stub(RESET_URL, status, "{\"code\":\"WEAK_PASSWORD\"}");
                submitRequest(EMAIL);
                submitReset(html, PASSWORD, PASSWORD);
            }
            WIRE_MOCK_SERVER.stubFor(WireMock.post(urlEqualTo(FORGOT_URL))
                    .willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)));
            WIRE_MOCK_SERVER.stubFor(WireMock.post(urlEqualTo(RESET_URL))
                    .willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)));
            submitRequest(EMAIL);
            submitReset(html, PASSWORD, PASSWORD);
        } finally {
            root.detachAppender(appender);
        }

        assertThat(appender.list).isNotEmpty();
        for (String secret : List.of(TOKEN, EMAIL, PASSWORD)) {
            assertThat(appender.list).noneMatch(event -> (event.getFormattedMessage()
                    + (event.getThrowableProxy() == null ? "" : ThrowableProxyUtil.asString(event.getThrowableProxy())))
                    .contains(secret));
        }
    }
}
