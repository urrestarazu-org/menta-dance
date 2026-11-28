package com.menta.bff.infrastructure.integration;

import static com.github.tomakehurst.wiremock.client.WireMock.anyRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.putRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static com.menta.bff.infrastructure.integration.HtmlSections.mainOf;
import static com.menta.bff.infrastructure.integration.VideoApiFixtures.API;
import static com.menta.bff.infrastructure.integration.VideoApiFixtures.SECRET;
import static com.menta.bff.infrastructure.integration.VideoApiFixtures.VIDEO_ID;
import static com.menta.bff.infrastructure.integration.VideoApiFixtures.problem;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * Renaming a video (#842): the form, the exact call to the Virtual API, Post/Redirect/Get with a one-time notice, and
 * how every refusal is worded from its {@code code}. Each case logs in for real against the stubbed APIs.
 */
@DisplayName("Management video rename Integration Tests")
class ManagementVideoRenameIntegrationTest extends BaseIntegrationTest {

    private static final String LIST = "/gestion/videos";
    private static final String URL = LIST + "/" + VIDEO_ID + "/renombrar";
    private static final String VIDEO_API = API + "/" + VIDEO_ID;

    private Cookie logIn(String role) throws Exception {
        return VideoApiFixtures.logIn(mockMvc, WIRE_MOCK_SERVER, role);
    }

    private static String render(MockHttpServletResponse response) throws Exception {
        assertThat(response.getHeader("Cache-Control")).contains("no-store");
        assertThat(response.getContentAsString()).doesNotContain("/api/v1").doesNotContain(SECRET);
        return mainOf(response.getContentAsString());
    }

    private MockHttpServletResponse submit(Cookie session, String title) throws Exception {
        return mockMvc.perform(post(URL).cookie(session).with(csrf()).param("title", title)).andReturn()
                .getResponse();
    }

    // ---- the form ----

    @Test
    @DisplayName("the form is prefilled with the title of the link, escaped, and posts with CSRF; no API call")
    void form() throws Exception {
        MockHttpServletResponse response = mockMvc.perform(get(URL).param("titulo", "Abrazo \"<b>\"")
                .cookie(logIn("INSTRUCTOR"))).andReturn().getResponse();
        String main = render(response);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(main).contains("<h1")
                .containsPattern("<label[^>]*for=\"title\"[^>]*>\\s*Título del video\\s*</label>")
                .containsPattern("<input[^>]*id=\"title\"[^>]*name=\"title\""
                        + "[^>]*value=\"Abrazo &quot;&lt;b&gt;&quot;\"")
                .containsPattern("<form[^>]*action=\"" + URL + "\"[^>]*method=\"post\"")
                .containsPattern("<input type=\"hidden\" name=\"_csrf\"")
                .containsPattern("<button[^>]*type=\"submit\"[^>]*>\\s*Guardar\\s*</button>")
                .containsPattern("<a[^>]*href=\"/gestion/videos\">Cancelar</a>")
                .doesNotContain("<b>");
        WIRE_MOCK_SERVER.verify(0, anyRequestedFor(urlPathMatching(API + ".*")));
    }

    @Test
    @DisplayName("without a title in the link the field is empty")
    void formWithoutTitle() throws Exception {
        String main = render(mockMvc.perform(get(URL).cookie(logIn("ADMIN"))).andReturn().getResponse());

        assertThat(main).containsPattern("<input[^>]*id=\"title\"[^>]*>")
                .doesNotContainPattern("<input[^>]*id=\"title\"[^>]*value=\"[^\"]+\"");
    }

    @Test
    @DisplayName("a title in the link longer than 255 characters is cut, so the form never starts invalid")
    void longTitleInTheLinkIsCut() throws Exception {
        String main = render(mockMvc.perform(get(URL).param("titulo", "a".repeat(400)).cookie(logIn("ADMIN")))
                .andReturn().getResponse());

        assertThat(main).contains("value=\"" + "a".repeat(255) + "\"").doesNotContain("a".repeat(256));
    }

    // ---- renaming ----

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"ADMIN", "INSTRUCTOR"})
    @DisplayName("renaming makes the exact call with the user's Bearer and the trimmed title, then PRG with a notice")
    void rename(String role) throws Exception {
        VideoApiFixtures.stub(WIRE_MOCK_SERVER, "PUT", VIDEO_API, 200, "{}");
        Cookie session = logIn(role);

        mockMvc.perform(post(URL).cookie(session).with(csrf()).param("title", "  Nuevo título  "))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl(LIST))
                .andExpect(flash().attribute("videoSaved", "Video renombrado."));

        WIRE_MOCK_SERVER.verify(1, putRequestedFor(urlEqualTo(VIDEO_API))
                .withHeader("Authorization", equalTo("Bearer " + TestAccessTokens.withRole(role)))
                .withRequestBody(equalToJson("{\"title\":\"Nuevo título\"}", false, false)));
        WIRE_MOCK_SERVER.verify(1, anyRequestedFor(urlPathMatching(API + ".*")));
    }

    static Stream<String> invalidTitles() {
        return Stream.of("", "   ", "a".repeat(256));
    }

    @ParameterizedTest(name = "[{0}]")
    @MethodSource("invalidTitles")
    @DisplayName("a blank or too long title shows the error next to the field, with the summary, and no API call")
    void invalidTitle(String title) throws Exception {
        MockHttpServletResponse response = submit(logIn("ADMIN"), title);
        String main = render(response);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(main).containsPattern("<p class=\"field-error\">(Ingresá el título\\.|El título puede tener hasta "
                + "255 caracteres\\.)</p>").contains("error-summary").contains("aria-invalid=\"true\"");
        WIRE_MOCK_SERVER.verify(0, anyRequestedFor(urlPathMatching(API + ".*")));
    }

    static Stream<Arguments> refusals() {
        return Stream.of(
                Arguments.of(403, "VIDEO_NOT_OWNED", 403, "No podés gestionar este video."),
                Arguments.of(403, "ACCESS_DENIED", 403, "No podés gestionar los videos."),
                Arguments.of(404, "VIDEO_NOT_FOUND", 404, "No encontramos el video."),
                Arguments.of(400, "INVALID_REQUEST", 400, "Revisá los datos ingresados."),
                Arguments.of(503, "VIDEO_LIBRARY_UNAVAILABLE", 503,
                        "La biblioteca de videos no está disponible en este momento. "
                                + "Intentá de nuevo en unos minutos."),
                Arguments.of(409, "VIDEO_IN_USE", 503, "No pudimos guardar el video. Intentá de nuevo."),
                Arguments.of(500, "OTHER", 503, "No pudimos guardar el video. Intentá de nuevo."),
                Arguments.of(401, "OTHER", 503, "No pudimos guardar el video. Intentá de nuevo."));
    }

    @ParameterizedTest(name = "API {0} {1} -> page {2}")
    @MethodSource("refusals")
    @DisplayName("every refusal is worded from its code, with the status of the other screens and never the body")
    void refused(int apiStatus, String code, int pageStatus, String message) throws Exception {
        VideoApiFixtures.stub(WIRE_MOCK_SERVER, "PUT", VIDEO_API, apiStatus, problem(apiStatus, code));

        MockHttpServletResponse response = submit(logIn("INSTRUCTOR"), "Nuevo");
        String main = render(response);

        assertThat(response.getStatus()).isEqualTo(pageStatus);
        assertThat(main).contains(message).doesNotContain(code);
        if (pageStatus == 400 || pageStatus == 503 && message.startsWith("No pudimos")) {
            assertThat(main).containsPattern("role=\"alert\"").contains("name=\"title\"").contains("value=\"Nuevo\"");
        } else {
            assertThat(main).contains("Volver a los videos").doesNotContain("<form");
        }
    }

    @Test
    @DisplayName("a library that does not answer shows the notice page, never the API's detail")
    void apiDown() throws Exception {
        VideoApiFixtures.stub(WIRE_MOCK_SERVER, "PUT", VIDEO_API, 500, "{\"detail\":\"" + SECRET + "\"}");

        MockHttpServletResponse response = submit(logIn("ADMIN"), "Nuevo");

        assertThat(response.getStatus()).isEqualTo(503);
        assertThat(render(response)).contains("No pudimos guardar el video. Intentá de nuevo.");
    }

    @Test
    @DisplayName("an id that is not a simple identifier is a 404 on both methods, without calling the API")
    void invalidId() throws Exception {
        Cookie session = logIn("ADMIN");
        String url = LIST + "/a.b/renombrar";

        MockHttpServletResponse opening = mockMvc.perform(get(url).cookie(session)).andReturn().getResponse();
        MockHttpServletResponse saving = mockMvc.perform(post(url).cookie(session).with(csrf())
                .param("title", "Nuevo")).andReturn().getResponse();

        assertThat(opening.getStatus()).isEqualTo(404);
        assertThat(render(opening)).contains("No encontramos el video.");
        assertThat(saving.getStatus()).isEqualTo(404);
        WIRE_MOCK_SERVER.verify(0, anyRequestedFor(urlPathMatching(API + ".*")));
    }

    // ---- access ----

    @ParameterizedTest(name = "{0} is refused")
    @ValueSource(strings = {"RECEPTIONIST", "STUDENT"})
    @DisplayName("every other role gets a 403 on both methods, and the API is never asked")
    void otherRolesAreRefused(String role) throws Exception {
        Cookie session = logIn(role);

        mockMvc.perform(get(URL).cookie(session)).andExpect(status().isForbidden());
        mockMvc.perform(post(URL).cookie(session).with(csrf()).param("title", "Nuevo"))
                .andExpect(status().isForbidden());

        WIRE_MOCK_SERVER.verify(0, anyRequestedFor(urlPathMatching(API + ".*")));
    }

    @Test
    @DisplayName("an anonymous visitor goes to the login page; a rename without CSRF is refused")
    void anonymousAndCsrf() throws Exception {
        Cookie session = logIn("ADMIN");

        mockMvc.perform(get(URL)).andExpect(redirectedUrlPattern("**/login"));
        mockMvc.perform(post(URL).with(csrf()).param("title", "Nuevo")).andExpect(redirectedUrlPattern("**/login"));
        mockMvc.perform(post(URL).cookie(session).param("title", "Nuevo")).andExpect(status().isForbidden());

        WIRE_MOCK_SERVER.verify(0, anyRequestedFor(urlPathMatching(API + ".*")));
    }
}
