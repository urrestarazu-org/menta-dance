package com.menta.bff.infrastructure.integration;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.anyRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.deleteRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.putRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static com.menta.bff.infrastructure.integration.HtmlSections.mainOf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.github.tomakehurst.wiremock.client.WireMock;
import jakarta.servlet.http.Cookie;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Publishing, unpublishing and deleting a virtual course (#367): the confirmation pages, what each confirmation
 * sends to the Virtual API (the exact call), how every refusal is worded from its {@code code} and that only the
 * reason of {@code COURSE_NOT_PUBLISHABLE} reaches the browser. Each case logs in for real against the stubbed APIs.
 */
@DisplayName("Management virtual course status Integration Tests")
class ManagementVirtualCourseStatusIntegrationTest extends BaseIntegrationTest {

    private static final String LIST = "/gestion/cursos-virtuales";
    private static final String ID = "5b6f7c8e-1d2a-4e3b-9c4d-0a1b2c3d4e5f";
    private static final String API = "/api/v1/admin/virtual/courses";
    private static final String COURSE_API = API + "/" + ID;
    private static final String SECRET = "secret-detail";
    private static final String TITLE = "<script>boom()</script>";
    private static final String ESCAPED_TITLE = "&lt;script&gt;boom()&lt;/script&gt;";
    private static final String GENERIC = "No pudimos completar la acción. Intentá de nuevo.";
    private static final String NOT_PUBLISHABLE = "El curso todavía no cumple las condiciones para publicarse.";

    /** The three actions: page, API call, what the confirmation says and the course state it expects. */
    private enum Route {
        PUBLISH("publicar", "PUT", "/publish", "DRAFT", "Va a quedar visible en el catálogo para los alumnos.",
                "Sí, publicar", "Curso publicado."),
        UNPUBLISH("despublicar", "PUT", "/unpublish", "PUBLISHED",
                "Va a dejar de verse en el catálogo y vuelve a borrador.", "Sí, despublicar", "Curso despublicado."),
        DELETE("borrar", "DELETE", "", "DRAFT",
                "Se borran también sus módulos y lecciones. Esta acción no se puede deshacer.",
                "Sí, borrar el curso", "Curso borrado.");

        final String page;
        final String method;
        final String apiSuffix;
        final String expectedStatus;
        final String effect;
        final String button;
        final String success;

        Route(String page, String method, String apiSuffix, String expectedStatus, String effect, String button,
                String success) {
            this.page = page;
            this.method = method;
            this.apiSuffix = apiSuffix;
            this.expectedStatus = expectedStatus;
            this.effect = effect;
            this.button = button;
            this.success = success;
        }

        String url() {
            return LIST + "/" + ID + "/" + page;
        }

        String apiUrl() {
            return COURSE_API + apiSuffix;
        }
    }

    private static final String MANAGED = ("{\"courseId\":\"%s\",\"title\":\"Tango base\","
            + "\"shortDescription\":\"Corta\","
            + "\"description\":\"Larga\",\"professorId\":\"0a1b2c3d-1d2a-4e3b-9c4d-5b6f7c8e9f00\","
            + "\"imageUrl\":\"https://img.example/t.png\",\"category\":\"Tango\",\"level\":\"BEGINNER\","
            + "\"premium\":false,\"status\":\"PUBLISHED\"}").formatted(ID);

    private static String course(String title, String status) {
        return ("{\"courseId\":\"%s\",\"title\":\"%s\",\"shortDescription\":\"Corta\",\"description\":\"Larga\","
                + "\"professorId\":\"0a1b2c3d-1d2a-4e3b-9c4d-5b6f7c8e9f00\",\"thumbnailUrl\":\"https://img.example/t.png\","
                + "\"category\":\"Tango\",\"level\":\"BEGINNER\",\"isPremium\":false,\"status\":\"%s\",\"modules\":[],"
                + "\"stats\":{\"moduleCount\":0,\"lessonCount\":0,\"totalDuration\":\"0m\"}}")
                .formatted(ID, title, status);
    }

    private Cookie logIn(String role) throws Exception {
        WIRE_MOCK_SERVER.stubFor(WireMock.post(urlEqualTo("/api/v1/auth/login")).willReturn(aResponse()
                .withStatus(200).withHeader("Content-Type", "application/json")
                .withHeader("X-Refresh-Token", "refresh-token-abc")
                .withBody("{\"access_token\": \"%s\", \"token_type\": \"Bearer\", \"expires_in\": 3600}"
                        .formatted(TestAccessTokens.withRole(role)))));
        Cookie session = mockMvc.perform(post("/login").param("email", "user@example.com")
                        .param("password", "Password1").with(csrf()))
                .andReturn().getResponse().getCookie("SESSION");
        assertThat(session).isNotNull();
        return session;
    }

    private static void stub(String method, String path, int status, String body) {
        WIRE_MOCK_SERVER.stubFor(WireMock.request(method, urlPathEqualTo(path)).willReturn(aResponse()
                .withStatus(status).withHeader("Content-Type", "application/json").withBody(body)));
    }

    private static void stubProblem(String method, String path, int status, String code) {
        stub(method, path, status, "{\"status\":%d,\"detail\":\"%s\",\"code\":\"%s\"}".formatted(status, SECRET, code));
    }

    private static void stubCourse(Route route) {
        stub("GET", COURSE_API, 200, course(TITLE, route.expectedStatus));
    }

    private static MockHttpServletRequestBuilder confirm(Route route, Cookie session) {
        return post(route.url()).cookie(session).with(csrf());
    }

    /** The {@code <main>} of the response, after checking what no screen of these may leak or cache. */
    private static String render(MockHttpServletResponse response) throws Exception {
        assertThat(response.getHeader("Cache-Control")).contains("no-store");
        assertThat(response.getContentAsString()).doesNotContain("/api/v1").doesNotContain(SECRET);
        return mainOf(response.getContentAsString());
    }

    // ---- confirmation pages ----

    @ParameterizedTest(name = "{0}")
    @EnumSource(Route.class)
    @DisplayName("the page asks, escapes the title, shows the state and the effect, and posts with CSRF")
    void confirmationPage(Route route) throws Exception {
        stubCourse(route);

        MockHttpServletResponse response = mockMvc.perform(get(route.url()).cookie(logIn("INSTRUCTOR")))
                .andReturn().getResponse();
        String main = render(response);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(main).contains("<strong>" + ESCAPED_TITLE + "</strong>", route.effect, "Estado actual:")
                .doesNotContain("<script>boom").doesNotContain("Este curso ya está")
                .containsPattern("<form[^>]*action=\"" + route.url() + "\"[^>]*method=\"post\"")
                .containsPattern("<input type=\"hidden\" name=\"_csrf\"")
                .containsPattern("<button[^>]*type=\"submit\"[^>]*>" + route.button + "</button>")
                .containsPattern("<a[^>]*href=\"/gestion/cursos-virtuales\">Volver</a>");
        assertThat(main.contains("btn-danger")).isEqualTo(route == Route.DELETE);
        assertThat(main.contains("Solo se pueden borrar cursos en borrador."))
                .isEqualTo(route == Route.DELETE);
        WIRE_MOCK_SERVER.verify(1, anyRequestedFor(urlPathMatching(API + ".*")));
    }

    static Stream<Arguments> mismatches() {
        return Stream.of(
                Arguments.of(Route.PUBLISH, "PUBLISHED", "Este curso ya está publicado.", "Publicado"),
                Arguments.of(Route.UNPUBLISH, "DRAFT", "Este curso ya está en borrador.", "Borrador"),
                Arguments.of(Route.DELETE, "ARCHIVED", "Este curso está archivado.", "Archivado"),
                Arguments.of(Route.PUBLISH, "SUSPENDED", "No conocemos el estado de este curso.", "Sin dato"));
    }

    @ParameterizedTest(name = "{0} on a {1} course")
    @MethodSource("mismatches")
    @DisplayName("a state the action does not expect is an informative note: the page stays and the API decides")
    void stateNote(Route route, String status, String note, String label) throws Exception {
        stub("GET", COURSE_API, 200, course("Tango base", status));

        MockHttpServletResponse response = mockMvc.perform(get(route.url()).cookie(logIn("ADMIN")))
                .andReturn().getResponse();
        String main = render(response);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(main).contains(note).contains("<strong>" + label + "</strong>").contains(route.button)
                .containsPattern("<div[^>]*role=\"status\"[^>]*>\\s*<p>" + note);
    }

    // ---- confirming ----

    @ParameterizedTest(name = "{0}")
    @EnumSource(Route.class)
    @DisplayName("confirming makes the exact call with the user's Bearer, goes to the list and confirms once")
    void confirmSucceeds(Route route) throws Exception {
        stub(route.method, route.apiUrl(), route == Route.DELETE ? 204 : 200, route == Route.DELETE ? "" : MANAGED);
        stub("GET", API, 200, "{\"courses\":[]}");
        Cookie session = logIn("INSTRUCTOR");

        mockMvc.perform(confirm(route, session)).andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl(LIST)).andExpect(flash().attribute("virtualCourseSaved", route.success));

        String bearer = "Bearer " + TestAccessTokens.withRole("INSTRUCTOR");
        WIRE_MOCK_SERVER.verify(1, route == Route.DELETE
                ? deleteRequestedFor(urlEqualTo(route.apiUrl())).withHeader("Authorization", equalTo(bearer))
                : putRequestedFor(urlEqualTo(route.apiUrl())).withHeader("Authorization", equalTo(bearer)));
        WIRE_MOCK_SERVER.verify(1, anyRequestedFor(urlPathMatching(API + ".*")));
        String first = mainOf(mockMvc.perform(get(LIST).cookie(session)).andReturn().getResponse()
                .getContentAsString());
        String second = mainOf(mockMvc.perform(get(LIST).cookie(session)).andReturn().getResponse()
                .getContentAsString());
        assertThat(first).containsPattern("<[^>]*role=\"status\"[^>]*>\\s*<p>" + route.success);
        assertThat(second).doesNotContain(route.success);
    }

    static Stream<Arguments> unpublishable() {
        return Stream.of(
                Arguments.of("\"detail\":\"El curso no tiene <b>módulos</b>.\",",
                        "El curso no tiene &lt;b&gt;módulos&lt;/b&gt;.", "<b>módulos"),
                Arguments.of("", NOT_PUBLISHABLE, "<b>"));
    }

    @ParameterizedTest(name = "reason: {1}")
    @MethodSource("unpublishable")
    @DisplayName("a course that is not publishable shows the API's reason, escaped, or a generic one, and a fix link")
    void notPublishable(String detail, String shown, String raw) throws Exception {
        stubCourse(Route.PUBLISH);
        stub("PUT", Route.PUBLISH.apiUrl(), 400, "{\"status\":400," + detail + "\"code\":\"COURSE_NOT_PUBLISHABLE\"}");

        MockHttpServletResponse response = mockMvc.perform(confirm(Route.PUBLISH, logIn("ADMIN")))
                .andReturn().getResponse();
        String main = render(response);

        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(main).contains(shown, Route.PUBLISH.button).doesNotContain(raw)
                .containsPattern("<div[^>]*role=\"alert\"[^>]*>\\s*<p>El curso")
                .containsPattern("<a[^>]*href=\"/gestion/cursos-virtuales/" + ID
                        + "/estructura\">Revisar la estructura</a>");
    }

    static Stream<Arguments> refusals() {
        return Stream.of(
                Arguments.of(Route.DELETE, 409, "COURSE_NOT_DRAFT", 409,
                        "Solo se puede borrar un curso en borrador. Despublicalo primero."),
                Arguments.of(Route.PUBLISH, 409, "COURSE_NOT_DRAFT", 409,
                        "Solo se puede publicar un curso en borrador."),
                Arguments.of(Route.UNPUBLISH, 409, "COURSE_NOT_DRAFT", 503, GENERIC),
                Arguments.of(Route.DELETE, 409, "COURSE_HAS_PROGRESS", 409,
                        "No se puede borrar el curso porque ya tiene progreso de alumnos."),
                Arguments.of(Route.PUBLISH, 409, "COURSE_HAS_PROGRESS", 503, GENERIC),
                Arguments.of(Route.UNPUBLISH, 409, "COURSE_HAS_PROGRESS", 503, GENERIC),
                Arguments.of(Route.PUBLISH, 400, "INVALID_REQUEST", 503, GENERIC),
                Arguments.of(Route.UNPUBLISH, 500, "OTHER", 503, GENERIC),
                Arguments.of(Route.DELETE, 500, "OTHER", 503, GENERIC));
    }

    @ParameterizedTest(name = "{0}: API {1} {2} -> page {3}")
    @MethodSource("refusals")
    @DisplayName("the other refusals are worded from the code, on the confirmation page again, without the API's body")
    void refused(Route route, int apiStatus, String code, int pageStatus, String message) throws Exception {
        stubCourse(route);
        stubProblem(route.method, route.apiUrl(), apiStatus, code);

        MockHttpServletResponse response = mockMvc.perform(confirm(route, logIn("ADMIN"))).andReturn().getResponse();
        String main = render(response);

        assertThat(response.getStatus()).isEqualTo(pageStatus);
        assertThat(main).containsPattern("<div[^>]*role=\"alert\"[^>]*>\\s*<p>" + message)
                .contains(route.button).doesNotContain("Revisar la estructura");
    }

    @Test
    @DisplayName("a course with student progress cannot be deleted: the page says so and never shows the code")
    void deleteRefusedForProgress() throws Exception {
        stubCourse(Route.DELETE);
        stubProblem("DELETE", Route.DELETE.apiUrl(), 409, "COURSE_HAS_PROGRESS");

        MockHttpServletResponse response =
            mockMvc.perform(confirm(Route.DELETE, logIn("ADMIN"))).andReturn().getResponse();

        assertThat(response.getStatus()).isEqualTo(409);
        assertThat(render(response)).doesNotContain("COURSE_HAS_PROGRESS", "Despublicalo primero")
            .contains(Route.DELETE.button);
    }

    static Stream<Arguments> notOwnedOrGone() {
        return Stream.of(Route.values()).flatMap(route -> Stream.of(
                Arguments.of(route, 403, "COURSE_NOT_OWNED", "No podés gestionar este curso."),
                Arguments.of(route, 404, "COURSE_NOT_FOUND", "No encontramos el curso.")));
    }

    @ParameterizedTest(name = "{0}: API {1} {2}")
    @MethodSource("notOwnedOrGone")
    @DisplayName("a course that is not owned or not found is a notice page, on opening and on confirming")
    void notOwnedOrNotFound(Route route, int status, String code, String message) throws Exception {
        stubProblem("GET", COURSE_API, status, code);
        stubProblem(route.method, route.apiUrl(), status, code);
        Cookie session = logIn("INSTRUCTOR");

        for (MockHttpServletRequestBuilder request : new MockHttpServletRequestBuilder[] {
            get(route.url()).cookie(session), confirm(route, session)}) {
            MockHttpServletResponse response = mockMvc.perform(request).andReturn().getResponse();

            assertThat(response.getStatus()).isEqualTo(status);
            assertThat(render(response)).contains(message, "Volver a los cursos virtuales").doesNotContain("<form");
        }
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(Route.class)
    @DisplayName("when the API is down, opening is a 503 notice and confirming a 503 notice with the action message")
    void apiDown(Route route) throws Exception {
        stubProblem("GET", COURSE_API, 500, "OTHER");
        stubProblem(route.method, route.apiUrl(), 500, "OTHER");
        Cookie session = logIn("ADMIN");

        MockHttpServletResponse opening = mockMvc.perform(get(route.url()).cookie(session)).andReturn().getResponse();
        MockHttpServletResponse confirming = mockMvc.perform(confirm(route, session)).andReturn().getResponse();

        assertThat(opening.getStatus()).isEqualTo(503);
        assertThat(render(opening)).contains("No pudimos cargar el curso en este momento.");
        assertThat(confirming.getStatus()).isEqualTo(503);
        assertThat(render(confirming)).contains(GENERIC);
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(Route.class)
    @DisplayName("an id that is not a UUID is a 404 on both methods, without calling the API")
    void invalidId(Route route) throws Exception {
        Cookie session = logIn("ADMIN");
        String url = route.url().replace(ID, "no-es-uuid");

        mockMvc.perform(get(url).cookie(session)).andExpect(status().isNotFound());
        mockMvc.perform(post(url).cookie(session).with(csrf())).andExpect(status().isNotFound());

        WIRE_MOCK_SERVER.verify(0, anyRequestedFor(urlPathMatching(API + ".*")));
    }

    // ---- access ----

    @ParameterizedTest(name = "{0} is refused")
    @ValueSource(strings = {"RECEPTIONIST", "STUDENT"})
    @DisplayName("every other role gets a 403 on every route, and the API is never asked")
    void otherRolesAreRefused(String role) throws Exception {
        Cookie session = logIn(role);

        for (Route route : Route.values()) {
            mockMvc.perform(get(route.url()).cookie(session)).andExpect(status().isForbidden());
            mockMvc.perform(confirm(route, session)).andExpect(status().isForbidden());
        }

        WIRE_MOCK_SERVER.verify(0, anyRequestedFor(urlPathMatching(API + ".*")));
    }

    @Test
    @DisplayName("an anonymous visitor goes to the login page; a confirmation without CSRF is refused")
    void anonymousAndCsrf() throws Exception {
        Cookie session = logIn("ADMIN");

        for (Route route : Route.values()) {
            mockMvc.perform(get(route.url())).andExpect(redirectedUrlPattern("**/login"));
            mockMvc.perform(post(route.url()).with(csrf())).andExpect(redirectedUrlPattern("**/login"));
            mockMvc.perform(post(route.url()).cookie(session)).andExpect(status().isForbidden());
        }

        WIRE_MOCK_SERVER.verify(0, anyRequestedFor(urlPathMatching(API + ".*")));
    }
}
