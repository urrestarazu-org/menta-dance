package com.menta.bff.infrastructure.integration;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.menta.bff.infrastructure.integration.HtmlSections.mainOf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.github.tomakehurst.wiremock.client.WireMock;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Error summary of the server-rendered forms (#529, finding 23, WCAG 3.3.1 and 3.3.3). After an invalid submit the
 * page reloads with a summary of the field errors whose links jump to each invalid input, and a small static script
 * moves focus to it. The summary is a plain focusable group, never a live region: the page reload is announced by
 * the browser and the project requires each message to be announced once.
 */
@DisplayName("Error summary Integration Tests")
class ErrorSummaryIntegrationTest extends BaseIntegrationTest {

    private static final Pattern SUMMARY = Pattern.compile(
            "<div[^>]*\\bdata-error-summary\\b[^>]*>.*?</div>", Pattern.DOTALL);
    private static final Pattern SUMMARY_LINK = Pattern.compile("<a[^>]*\\bhref=\"#([^\"]+)\"[^>]*>([^<]*)</a>");
    private static final Pattern SUMMARY_TITLE = Pattern.compile("<h2[^>]*>([^<]*)</h2>");
    private static final Pattern SCRIPT_SRC = Pattern.compile(
            "<script[^>]*\\bsrc=\"(/js/error-summary-[0-9a-f]{32}\\.js)\"[^>]*\\bdefer\\b[^>]*>");
    private static final Pattern HEADING = Pattern.compile("<h([1-6])[\\s>]");
    private static final String REGISTER_API = "/api/v1/auth/register";

    /** Invalid submissions of every form that renders field errors: label, request, errors, link targets in order. */
    static Stream<Arguments> invalidSubmissions() {
        Supplier<MockHttpServletRequestBuilder> register = () -> post("/registro")
                .param("firstName", "").param("lastName", "Prueba").param("email", "no-es-un-email")
                .param("phone", "+54 9 11 0000-0000").param("password", "corta")
                .param("passwordConfirmation", "corta");
        Supplier<MockHttpServletRequestBuilder> registerMismatch = () -> post("/registro")
                .param("firstName", "Ana").param("lastName", "Prueba").param("email", "ana@example.com")
                .param("phone", "+54 9 11 0000-0000").param("password", "Secreta123")
                .param("passwordConfirmation", "Otra12345");
        Supplier<MockHttpServletRequestBuilder> requestReset = () -> post("/recuperar-contrasena")
                .param("email", "no-es-un-email");
        Supplier<MockHttpServletRequestBuilder> resend = () -> post("/reenviar-activacion")
                .param("email", "no-es-un-email");
        Supplier<MockHttpServletRequestBuilder> reset = () -> post("/restablecer-contrasena")
                .param("token", "reset-token-abc").param("password", "corta").param("passwordConfirmation", "corta");
        Supplier<MockHttpServletRequestBuilder> resetMismatch = () -> post("/restablecer-contrasena")
                .param("token", "reset-token-abc").param("password", "Secreta123")
                .param("passwordConfirmation", "Otra12345");
        return Stream.of(
                Arguments.of("registro", register, 5,
                        List.of("firstName", "email", "password", "password", "password")),
                Arguments.of("registro con confirmación distinta", registerMismatch, 1,
                        List.of("passwordConfirmation")),
                Arguments.of("recuperar contraseña", requestReset, 1, List.of("email")),
                Arguments.of("reenviar activación", resend, 1, List.of("email")),
                Arguments.of("restablecer contraseña", reset, 3, List.of("password", "password", "password")),
                Arguments.of("restablecer con confirmación distinta", resetMismatch, 1,
                        List.of("passwordConfirmation")));
    }

    @ParameterizedTest(name = "{0}: el resumen lista {2} error(es) en el orden del formulario")
    @MethodSource("invalidSubmissions")
    @DisplayName("an invalid submit renders a summary with one link per error, in form order, to existing inputs")
    void invalidSubmitRendersTheSummary(String label, Supplier<MockHttpServletRequestBuilder> request,
            int errors, List<String> targets) throws Exception {
        String html = submit(request);

        String summary = summaryOf(html);
        assertThat(summary).contains("tabindex=\"-1\"").contains("role=\"group\"")
                .containsPattern("aria-labelledby=\"error-summary-title\"")
                .doesNotContain("role=\"alert\"").doesNotContain("aria-live");
        assertThat(titleOf(summary)).isEqualTo(title(errors));
        assertThat(summary).contains("id=\"error-summary-title\"");
        List<String> hrefs = new ArrayList<>();
        Matcher link = SUMMARY_LINK.matcher(summary);
        while (link.find()) {
            hrefs.add(link.group(1));
            assertThat(link.group(2)).as("message of the link to #%s", link.group(1)).isNotBlank();
            assertThat(html).as("the input #%s that the summary links to", link.group(1))
                    .containsPattern("<input[^>]*\\bid=\"" + link.group(1) + "\"");
        }
        assertThat(hrefs).containsExactlyElementsOf(targets);
    }

    @ParameterizedTest(name = "{0}: ningún campo pide autofocus, el foco es del resumen")
    @MethodSource("invalidSubmissions")
    @DisplayName("with a summary no input asks for autofocus, so the browser cannot take the focus from the script")
    void summaryOwnsTheFocus(String label, Supplier<MockHttpServletRequestBuilder> request,
            int errors, List<String> targets) throws Exception {
        assertThat(submit(request)).doesNotContain("autofocus");
    }

    @ParameterizedTest(name = "{0} sin enviar conserva el autofocus del primer campo")
    @ValueSource(strings = {
        "/registro", "/recuperar-contrasena", "/reenviar-activacion", "/restablecer-contrasena/reset-token-abc"
    })
    @DisplayName("a pristine form keeps autofocus on its first field")
    void pristineFormKeepsItsAutofocus(String path) throws Exception {
        String html = mockMvc.perform(get(path)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(html.split("autofocus=", -1)).as("one autofocus attribute").hasSize(2);
    }

    @Test
    @DisplayName("the summary repeats the message each field shows, and uses the singular for one error")
    void summaryRepeatsTheFieldMessages() throws Exception {
        String html = submit(() -> post("/registro")
                .param("firstName", "Ana").param("lastName", "Prueba").param("email", "no-es-un-email")
                .param("phone", "+54 9 11 0000-0000").param("password", "Secreta123")
                .param("passwordConfirmation", "Secreta123"));

        String summary = summaryOf(html);
        assertThat(titleOf(summary)).isEqualTo("Hay 1 error en el formulario");
        assertThat(summary).contains(">El formato del email es inválido</a>");
        assertThat(html).containsPattern("<p[^>]*class=\"field-error\"[^>]*>El formato del email es inválido</p>");
    }

    @Test
    @DisplayName("the summary comes after the page title: one h1 and no skipped heading level")
    void summaryKeepsTheHeadingOrder() throws Exception {
        String html = submit(() -> post("/registro").param("email", "no-es-un-email"));

        assertThat(html.split("<h1", -1)).as("single h1").hasSize(2);
        assertThat(html.indexOf("<h1")).isLessThan(html.indexOf("data-error-summary"));
        List<Integer> levels = new ArrayList<>();
        Matcher heading = HEADING.matcher(html);
        while (heading.find()) {
            levels.add(Integer.parseInt(heading.group(1)));
        }
        assertThat(levels.get(0)).as("first heading").isEqualTo(1);
        for (int i = 1; i < levels.size(); i++) {
            assertThat(levels.get(i) - levels.get(i - 1)).as("heading jump at %d in %s", i, levels)
                    .isLessThanOrEqualTo(1);
        }
    }

    @ParameterizedTest(name = "{0} sin errores no muestra resumen ni script")
    @ValueSource(strings = {
        "/registro", "/login", "/recuperar-contrasena", "/reenviar-activacion",
        "/restablecer-contrasena/reset-token-abc"
    })
    @DisplayName("a page that has not been submitted renders no summary and loads no script")
    void validGetRendersNoSummary(String path) throws Exception {
        String html = mockMvc.perform(get(path)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(html).doesNotContain("data-error-summary").doesNotContain("error-summary")
                .doesNotContain("<script");
    }

    @Test
    @DisplayName("a failure that is not a field error keeps its single role=alert and renders no summary")
    void formErrorAlertStaysTheOnlyAnnouncement() throws Exception {
        WIRE_MOCK_SERVER.stubFor(WireMock.post(urlEqualTo(REGISTER_API)).willReturn(aResponse().withStatus(503)));

        String html = mockMvc.perform(post("/registro")
                        .param("firstName", "Ana").param("lastName", "Prueba").param("email", "ana@example.com")
                        .param("phone", "+54 9 11 0000-0000").param("password", "Secreta123")
                        .param("passwordConfirmation", "Secreta123").with(csrf()))
                .andReturn().getResponse().getContentAsString();

        assertThat(html.split("role=\"alert\"", -1)).as("one role=alert").hasSize(2);
        assertThat(html).doesNotContain("data-error-summary").doesNotContain("<script");
    }

    @Test
    @DisplayName("the field errors with a summary add no live region: the page has no role=alert")
    void summaryIsNotALiveRegion() throws Exception {
        String html = submit(() -> post("/registro").param("email", "no-es-un-email"));

        assertThat(html).doesNotContain("role=\"alert\"").doesNotContain("aria-live").doesNotContain("role=\"status\"");
    }

    @Test
    @DisplayName("the focus script is one content-hashed external file, loaded deferred only where a summary exists")
    void focusScriptIsAnExternalHashedFile() throws Exception {
        String html = submit(() -> post("/registro").param("email", "no-es-un-email"));

        assertThat(html).doesNotContainPattern("<script(?![^>]*\\bsrc=)");
        Matcher script = SCRIPT_SRC.matcher(mainOf(html));
        assertThat(script.find()).as("a deferred /js/error-summary-<hash>.js script in the page").isTrue();

        String body = mockMvc.perform(get(script.group(1)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(body).contains("data-error-summary").contains("focus");
    }

    @Test
    @DisplayName("the script is public at its plain path, GET-only, and opens no other route")
    void scriptPermitDoesNotWidenAccess() throws Exception {
        String type = mockMvc.perform(get("/js/error-summary.js"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentType();

        assertThat(type).contains("javascript");
        mockMvc.perform(post("/js/error-summary.js")).andExpect(status().is4xxClientError());
        mockMvc.perform(get("/dashboard")).andExpect(status().is3xxRedirection());
    }

    private String submit(Supplier<MockHttpServletRequestBuilder> request) throws Exception {
        return mockMvc.perform(request.get().with(csrf()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    private static String summaryOf(String html) {
        Matcher summary = SUMMARY.matcher(html);
        assertThat(summary.find()).as("an error summary in the page").isTrue();
        return summary.group();
    }

    private static String titleOf(String summary) {
        Matcher title = SUMMARY_TITLE.matcher(summary);
        assertThat(title.find()).as("an h2 title in the summary").isTrue();
        return title.group(1).trim();
    }

    private static String title(int errors) {
        return errors == 1 ? "Hay 1 error en el formulario" : "Hay " + errors + " errores en el formulario";
    }
}
