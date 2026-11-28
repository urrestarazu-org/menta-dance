package com.menta.bff.infrastructure.integration;

import static com.github.tomakehurst.wiremock.client.WireMock.anyRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.deleteRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static com.menta.bff.infrastructure.integration.HtmlSections.mainOf;
import static com.menta.bff.infrastructure.integration.VideoApiFixtures.API;
import static com.menta.bff.infrastructure.integration.VideoApiFixtures.LESSON_EDIT;
import static com.menta.bff.infrastructure.integration.VideoApiFixtures.SECRET;
import static com.menta.bff.infrastructure.integration.VideoApiFixtures.VIDEO_ID;
import static com.menta.bff.infrastructure.integration.VideoApiFixtures.inUse;
import static com.menta.bff.infrastructure.integration.VideoApiFixtures.lesson;
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
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * Deleting a video (#842), administrator only: the confirmation page, the exact call, Post/Redirect/Get with a
 * one-time notice, the lessons that use a video that cannot be deleted (with links) and how every refusal is worded.
 * An instructor gets a 403 from the security rules, before any controller or API call.
 */
@DisplayName("Management video delete Integration Tests")
class ManagementVideoDeleteIntegrationTest extends BaseIntegrationTest {

    private static final String LIST = "/gestion/videos";
    private static final String URL = LIST + "/" + VIDEO_ID + "/borrar";
    private static final String VIDEO_API = API + "/" + VIDEO_ID;
    private static final String GENERIC = "No pudimos completar la acción. Intentá de nuevo.";

    private Cookie logIn(String role) throws Exception {
        return VideoApiFixtures.logIn(mockMvc, WIRE_MOCK_SERVER, role);
    }

    private static String render(MockHttpServletResponse response) throws Exception {
        assertThat(response.getHeader("Cache-Control")).contains("no-store");
        assertThat(response.getContentAsString()).doesNotContain("/api/v1").doesNotContain(SECRET);
        return mainOf(response.getContentAsString());
    }

    private MockHttpServletResponse confirm(Cookie session) throws Exception {
        return mockMvc.perform(post(URL).cookie(session).with(csrf()).param("titulo", "Mi <video>")).andReturn()
                .getResponse();
    }

    // ---- the confirmation page ----

    @Test
    @DisplayName("the page asks, escapes the title, says what happens and posts with CSRF; no API call")
    void confirmationPage() throws Exception {
        MockHttpServletResponse response = mockMvc.perform(get(URL).param("titulo", "Mi <video>")
                .cookie(logIn("ADMIN"))).andReturn().getResponse();
        String main = render(response);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(main).contains("<strong>Mi &lt;video&gt;</strong>")
                .contains("Se borra de la biblioteca. Esta acción no se puede deshacer.")
                .doesNotContain("<video>")
                .containsPattern("<form[^>]*action=\"" + URL + "\"[^>]*method=\"post\"")
                .containsPattern("<input type=\"hidden\" name=\"_csrf\"")
                .containsPattern("<input[^>]*type=\"hidden\"[^>]*name=\"titulo\"[^>]*value=\"Mi &lt;video&gt;\"")
                .containsPattern("<button[^>]*type=\"submit\"[^>]*btn-danger[^>]*>\\s*Sí, borrar el video\\s*</button>")
                .containsPattern("<a[^>]*href=\"/gestion/videos\">Volver</a>");
        WIRE_MOCK_SERVER.verify(0, anyRequestedFor(urlPathMatching(API + ".*")));
    }

    @Test
    @DisplayName("without a title in the link the page says 'este video'")
    void confirmationWithoutTitle() throws Exception {
        String main = render(mockMvc.perform(get(URL).cookie(logIn("ADMIN"))).andReturn().getResponse());

        assertThat(main).contains("este video").doesNotContain("«");
    }

    // ---- deleting ----

    @Test
    @DisplayName("confirming makes the exact call with the administrator's Bearer, then PRG with a one-time notice")
    void deleteSucceeds() throws Exception {
        VideoApiFixtures.stub(WIRE_MOCK_SERVER, "DELETE", VIDEO_API, 204, "");

        mockMvc.perform(post(URL).cookie(logIn("ADMIN")).with(csrf()))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl(LIST))
                .andExpect(flash().attribute("videoSaved", "Video borrado."));

        WIRE_MOCK_SERVER.verify(1, deleteRequestedFor(urlEqualTo(VIDEO_API))
                .withHeader("Authorization", equalTo("Bearer " + TestAccessTokens.withRole("ADMIN"))));
        WIRE_MOCK_SERVER.verify(1, anyRequestedFor(urlPathMatching(API + ".*")));
    }

    @Test
    @DisplayName("a video in use shows which lessons use it, with links to their edit screens, and no confirm button")
    void inUseShowsTheLessons() throws Exception {
        VideoApiFixtures.stub(WIRE_MOCK_SERVER, "DELETE", VIDEO_API, 409,
                inUse(List.of(lesson(), lesson("<b>Vals</b>", "Giros", "El giro"))));

        MockHttpServletResponse response = confirm(logIn("ADMIN"));
        String main = render(response);

        assertThat(response.getStatus()).isEqualTo(409);
        assertThat(main).contains("No se puede borrar el video porque está en uso en estas lecciones.")
                .contains("Cambiá el video de esas lecciones antes de borrarlo.")
                .containsPattern("<a[^>]*href=\"" + LESSON_EDIT + "\"[^>]*>\\s*Tango desde cero\\s*›\\s*Primeros pasos"
                        + "\\s*›\\s*El abrazo\\s*</a>")
                .contains("&lt;b&gt;Vals&lt;/b&gt;").doesNotContain("<b>Vals")
                .containsPattern("role=\"alert\"").doesNotContain("VIDEO_IN_USE")
                .doesNotContain("Sí, borrar el video")
                .containsPattern("<a[^>]*href=\"/gestion/videos\">Volver a los videos</a>");
    }

    @Test
    @DisplayName("a video in use without lessons in the answer still says it is in use")
    void inUseWithoutLessons() throws Exception {
        VideoApiFixtures.stub(WIRE_MOCK_SERVER, "DELETE", VIDEO_API, 409, problem(409, "VIDEO_IN_USE"));

        MockHttpServletResponse response = confirm(logIn("ADMIN"));

        assertThat(response.getStatus()).isEqualTo(409);
        assertThat(render(response)).contains("No se puede borrar el video porque está en uso en una lección.");
    }

    static Stream<Arguments> refusals() {
        return Stream.of(
                Arguments.of(403, "ACCESS_DENIED", 403, "No podés gestionar los videos."),
                Arguments.of(403, "VIDEO_NOT_OWNED", 403, "No podés gestionar este video."),
                Arguments.of(404, "VIDEO_NOT_FOUND", 404, "No encontramos el video."),
                Arguments.of(400, "INVALID_REQUEST", 503, GENERIC),
                Arguments.of(503, "VIDEO_LIBRARY_UNAVAILABLE", 503,
                        "La biblioteca de videos no está disponible en este momento. "
                                + "Intentá de nuevo en unos minutos."),
                Arguments.of(500, "OTHER", 503, GENERIC),
                Arguments.of(401, "OTHER", 503, GENERIC));
    }

    @ParameterizedTest(name = "API {0} {1} -> page {2}")
    @MethodSource("refusals")
    @DisplayName("every other refusal is worded from its code, never the body")
    void refused(int apiStatus, String code, int pageStatus, String message) throws Exception {
        VideoApiFixtures.stub(WIRE_MOCK_SERVER, "DELETE", VIDEO_API, apiStatus, problem(apiStatus, code));

        MockHttpServletResponse response = confirm(logIn("ADMIN"));
        String main = render(response);

        assertThat(response.getStatus()).isEqualTo(pageStatus);
        assertThat(main).contains(message).doesNotContain(code);
    }

    @Test
    @DisplayName("an id that is not a simple identifier is a 404 on both methods, without calling the API")
    void invalidId() throws Exception {
        Cookie session = logIn("ADMIN");
        String url = LIST + "/a.b/borrar";

        mockMvc.perform(get(url).cookie(session)).andExpect(status().isNotFound());
        mockMvc.perform(post(url).cookie(session).with(csrf())).andExpect(status().isNotFound());

        WIRE_MOCK_SERVER.verify(0, anyRequestedFor(urlPathMatching(API + ".*")));
    }

    // ---- access ----

    @ParameterizedTest(name = "{0} is refused")
    @ValueSource(strings = {"INSTRUCTOR", "RECEPTIONIST", "STUDENT"})
    @DisplayName("everyone but the administrator gets a 403 on both methods, and the API is never asked")
    void otherRolesAreRefused(String role) throws Exception {
        Cookie session = logIn(role);

        mockMvc.perform(get(URL).cookie(session)).andExpect(status().isForbidden());
        mockMvc.perform(post(URL).cookie(session).with(csrf())).andExpect(status().isForbidden());

        WIRE_MOCK_SERVER.verify(0, anyRequestedFor(urlPathMatching(API + ".*")));
    }

    @Test
    @DisplayName("an anonymous visitor goes to the login page; a delete without CSRF is refused")
    void anonymousAndCsrf() throws Exception {
        Cookie session = logIn("ADMIN");

        mockMvc.perform(get(URL)).andExpect(redirectedUrlPattern("**/login"));
        mockMvc.perform(post(URL).with(csrf())).andExpect(redirectedUrlPattern("**/login"));
        mockMvc.perform(post(URL).cookie(session)).andExpect(status().isForbidden());

        WIRE_MOCK_SERVER.verify(0, anyRequestedFor(urlPathMatching(API + ".*")));
    }
}
