package com.menta.bff.infrastructure.integration;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.anyRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.deleteRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
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
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * What the structure screen of a virtual course offers on each lesson (#831): the title as a link to the edit form,
 * the move up and down buttons (a swap of orders with the neighbour, two updates to the Virtual API) and the
 * deletion with its confirmation page. Covers what is rendered, what is sent, how every refusal is worded without
 * showing the API's body, and who may do it.
 */
@DisplayName("Management virtual lesson actions Integration Tests")
class ManagementVirtualLessonActionsIntegrationTest extends BaseIntegrationTest {

    private static final String ID = "5b6f7c8e-1d2a-4e3b-9c4d-0a1b2c3d4e5f";
    private static final String M1 = "11111111-1111-4111-8111-111111111111";
    private static final String M2 = "22222222-2222-4222-8222-222222222222";
    private static final String L1 = "aaaaaaa1-aaaa-4aaa-8aaa-aaaaaaaaaaa1";
    private static final String L2 = "aaaaaaa2-aaaa-4aaa-8aaa-aaaaaaaaaaa2";
    private static final String L3 = "aaaaaaa3-aaaa-4aaa-8aaa-aaaaaaaaaaa3";
    private static final String L4 = "aaaaaaa4-aaaa-4aaa-8aaa-aaaaaaaaaaa4";
    private static final String COURSE = "/gestion/cursos-virtuales/" + ID;
    private static final String STRUCTURE = COURSE + "/estructura";
    private static final String COURSE_API = "/api/v1/admin/virtual/courses/" + ID;
    private static final String SECRET = "secret-detail";
    private static final String TITLE = "<script>boom()</script>";
    private static final String ESCAPED_TITLE = "&lt;script&gt;boom()&lt;/script&gt;";
    private static final String LOAD_FAILED =
            "No pudimos cargar el curso en este momento. Intentá nuevamente en unos minutos.";
    private static final String MOVE_FAILED = "No pudimos mover la lección. Intentá de nuevo.";
    private static final String ACTION_FAILED = "No pudimos completar la acción. Intentá de nuevo.";

    private static String lessonUrl(String moduleId, String lessonId, String action) {
        return COURSE + "/modulos/" + moduleId + "/lecciones/" + lessonId + "/" + action;
    }

    private static String lessonApi(String lessonId) {
        return "/api/v1/admin/virtual/lessons/" + lessonId;
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

    private static String lesson(String id, String title, int order) {
        return "{\"lessonId\":\"%s\",\"title\":\"%s\",\"description\":\"Descripción\",\"durationMinutes\":10,"
                .formatted(id, title) + "\"isFree\":true,\"order\":%d,\"videoId\":null}".formatted(order);
    }

    /** What the API answers to a change of a lesson. */
    private static String managed(String id, int order) {
        return ("{\"lessonId\":\"%s\",\"moduleId\":\"%s\",\"courseId\":\"%s\",\"title\":\"x\","
                + "\"description\":\"Descripción\",\"videoId\":null,\"durationMinutes\":10,\"free\":true,"
                + "\"order\":%d}").formatted(id, M1, ID, order);
    }

    private static String module(String id, String title, int order, String... lessons) {
        return "{\"moduleId\":\"%s\",\"title\":\"%s\",\"preview\":false,\"order\":%d,\"lessons\":[%s]}"
                .formatted(id, title, order, String.join(",", lessons));
    }

    private static void stubCourse(String... modules) {
        stub("GET", COURSE_API, 200, ("{\"courseId\":\"%s\",\"title\":\"Tango base\",\"shortDescription\":\"Corta\","
                + "\"description\":\"Larga\",\"professorId\":\"%s\",\"thumbnailUrl\":\"https://img.example/t.png\","
                + "\"category\":\"Tango\",\"level\":\"BEGINNER\",\"isPremium\":false,\"status\":\"DRAFT\","
                + "\"modules\":[%s],\"stats\":{\"moduleCount\":%d,\"lessonCount\":4,\"totalDuration\":\"40m\"}}")
                .formatted(ID, M1, String.join(",", modules), modules.length));
    }

    /** Three lessons in the first module, returned out of order on purpose, and one alone in the second. */
    private static void stubLessons() {
        stubCourse(module(M1, "Pasos básicos", 1, lesson(L3, "Tercer paso", 2), lesson(L1, "Primer paso", 0),
                lesson(L2, "Segundo paso", 1)), module(M2, "Giros", 2, lesson(L4, "Giro único", 0)));
    }

    private static MockHttpServletRequestBuilder send(String url, Cookie session, boolean withCsrf) {
        MockHttpServletRequestBuilder request = post(url).cookie(session);
        return withCsrf ? request.with(csrf()) : request;
    }

    private static List<MockHttpServletRequestBuilder> posts(Cookie session, boolean withCsrf) {
        return List.of(send(lessonUrl(M1, L2, "subir"), session, withCsrf),
                send(lessonUrl(M1, L2, "bajar"), session, withCsrf),
                send(lessonUrl(M1, L2, "borrar"), session, withCsrf));
    }

    private String render(MockHttpServletRequestBuilder request) throws Exception {
        MockHttpServletResponse response = mockMvc.perform(request).andReturn().getResponse();
        assertThat(response.getHeader("Cache-Control")).contains("no-store");
        assertThat(response.getContentAsString()).doesNotContain("/api/v1").doesNotContain(SECRET);
        return mainOf(response.getContentAsString());
    }

    private static void verifyOrderSent(String lessonId, int order, int times) {
        WIRE_MOCK_SERVER.verify(times, putRequestedFor(urlEqualTo(lessonApi(lessonId)))
                .withHeader("Authorization", equalTo("Bearer " + TestAccessTokens.withRole("INSTRUCTOR")))
                .withRequestBody(equalToJson("{\"order\":" + order + "}")));
    }

    // ---- what the structure renders ----

    @Test
    @DisplayName("each lesson, in order, has its title linked to the edit form, with module and lesson in the name")
    void titleLinksToEdit() throws Exception {
        stubLessons();

        Cookie session = logIn("INSTRUCTOR");
        String main = render(get(STRUCTURE).cookie(session));
        String second = render(get(STRUCTURE + "?modulo=" + M2).cookie(session));

        // The detail lists the lessons of the selected module only (#849): the first one by default, then ?modulo=.
        assertThat(second).containsPattern("<a[^>]*href=\"" + lessonUrl(M2, L4, "editar") + "\">Giro único<span");
        assertThat(main).containsSubsequence("Primer paso", "Segundo paso", "Tercer paso")
                .containsPattern("<a[^>]*href=\"" + lessonUrl(M1, L2, "editar")
                        + "\">Segundo paso<span class=\"sr-only\">\\s*, lección del módulo Pasos básicos</span></a>")
                .containsPattern("<a[^>]*href=\"" + lessonUrl(M1, L2, "editar")
                        + "\">Editar<span class=\"sr-only\">\\s*lección Segundo paso</span></a>");
    }

    static Stream<Arguments> positions() {
        return Stream.of(Arguments.of(M1, L1, false, true), Arguments.of(M1, L2, true, true),
                Arguments.of(M1, L3, true, false), Arguments.of(M2, L4, false, false));
    }

    @ParameterizedTest(name = "lesson {1}: up {2}, down {3}")
    @MethodSource("positions")
    @DisplayName("the first lesson of a module has no Subir and the last has no Bajar; every one has Borrar")
    void buttonsByPosition(String moduleId, String lessonId, boolean up, boolean down) throws Exception {
        stubLessons();

        String main = render(get(STRUCTURE + "?modulo=" + moduleId).cookie(logIn("ADMIN")));

        assertThat(main.contains(lessonUrl(moduleId, lessonId, "subir"))).isEqualTo(up);
        assertThat(main.contains(lessonUrl(moduleId, lessonId, "bajar"))).isEqualTo(down);
        assertThat(main).containsPattern("<a[^>]*href=\"" + lessonUrl(moduleId, lessonId, "borrar")
                + "\">Borrar<span class=\"sr-only\">\\s*lección ");
        if (up) {
            assertThat(main).containsPattern("<form[^>]*method=\"post\"[^>]*action=\""
                    + lessonUrl(moduleId, lessonId, "subir")
                    + "\"[\\s\\S]*?Subir<span class=\"sr-only\">\\s*la lección ");
        }
        if (down) {
            assertThat(main).containsPattern("action=\"" + lessonUrl(moduleId, lessonId, "bajar")
                    + "\"[\\s\\S]*?Bajar<span class=\"sr-only\">\\s*la lección ");
        }
    }

    // ---- move ----

    static Stream<Arguments> moves() {
        return Stream.of(
                Arguments.of(lessonUrl(M1, L2, "subir"), L2, 0, L1, 1),
                Arguments.of(lessonUrl(M1, L2, "bajar"), L2, 2, L3, 1),
                Arguments.of(lessonUrl(M1, L3, "subir"), L3, 1, L2, 2),
                Arguments.of(lessonUrl(M1, L1, "bajar"), L1, 1, L2, 0));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("moves")
    @DisplayName("a move swaps the orders with the neighbour, one update each, and goes back to the structure")
    void move(String url, String lessonId, int lessonOrder, String neighbourId, int neighbourOrder) throws Exception {
        stubLessons();
        stub("PUT", lessonApi(lessonId), 200, managed(lessonId, lessonOrder));
        stub("PUT", lessonApi(neighbourId), 200, managed(neighbourId, neighbourOrder));

        mockMvc.perform(send(url, logIn("INSTRUCTOR"), true))
                .andExpect(redirectedUrl(STRUCTURE + "?modulo=" + M1))
                .andExpect(flash().attribute("structureAlert", "Lección movida."))
                .andExpect(flash().attribute("structureAlertKind", "success"));

        verifyOrderSent(lessonId, lessonOrder, 1);
        verifyOrderSent(neighbourId, neighbourOrder, 1);
        WIRE_MOCK_SERVER.verify(2, putRequestedFor(urlPathMatching("/api/v1/admin/.*")));
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {
        COURSE + "/modulos/" + M1 + "/lecciones/" + L1 + "/subir",
        COURSE + "/modulos/" + M1 + "/lecciones/" + L3 + "/bajar",
        COURSE + "/modulos/" + M2 + "/lecciones/" + L4 + "/subir",
        COURSE + "/modulos/" + M1 + "/lecciones/" + L4 + "/subir",
        COURSE + "/modulos/" + M1 + "/lecciones/bbbbbbb1-bbbb-4bbb-8bbb-bbbbbbbbbbb1/subir",
        COURSE + "/modulos/33333333-3333-4333-8333-333333333333/lecciones/" + L1 + "/bajar"})
    @DisplayName("a lesson on the edge, or one that is not there any more, is not moved and the API is not asked to")
    void moveNowhere(String url) throws Exception {
        stubLessons();

        mockMvc.perform(send(url, logIn("ADMIN"), true))
                .andExpect(redirectedUrl(STRUCTURE + "?modulo=" + url.split("/")[5]))
                .andExpect(flash().attribute("structureAlertKind", "info"));

        WIRE_MOCK_SERVER.verify(0, putRequestedFor(urlPathMatching("/api/v1/admin/.*")));
    }

    @Test
    @DisplayName("the notice of a move is shown once, and then the structure is the same as before")
    void noticeIsShownOnce() throws Exception {
        stubLessons();
        stub("PUT", lessonApi(L1), 200, managed(L1, 1));
        stub("PUT", lessonApi(L2), 200, managed(L2, 0));
        Cookie session = logIn("ADMIN");

        mockMvc.perform(send(lessonUrl(M1, L2, "subir"), session, true))
                .andExpect(redirectedUrl(STRUCTURE + "?modulo=" + M1));

        String first = render(get(STRUCTURE + "?modulo=" + M1).cookie(session));
        String second = render(get(STRUCTURE + "?modulo=" + M1).cookie(session));
        assertThat(first).containsPattern("<[^>]*role=\"status\"[^>]*>\\s*<p>Lección movida\\.</p>");
        assertThat(second).doesNotContain("Lección movida.");
    }

    @Test
    @DisplayName("when the second update fails the first lesson goes back to its order and the user is told")
    void secondUpdateFails() throws Exception {
        stubLessons();
        stub("PUT", lessonApi(L2), 200, managed(L2, 0));
        stubProblem("PUT", lessonApi(L1), 500, "OTHER");

        MockHttpServletResponse response = mockMvc.perform(send(lessonUrl(M1, L2, "subir"), logIn("INSTRUCTOR"), true))
                .andReturn().getResponse();

        assertThat(response.getStatus()).isEqualTo(503);
        assertThat(mainOf(response.getContentAsString())).contains(MOVE_FAILED);
        assertThat(response.getContentAsString()).doesNotContain(SECRET).doesNotContain("/api/v1");
        verifyOrderSent(L2, 0, 1);
        verifyOrderSent(L2, 1, 1);
    }

    // ---- delete ----

    @Test
    @DisplayName("the confirmation names the lesson, escaped, and the effect on the students' progress")
    void confirmationPage() throws Exception {
        stubCourse(module(M1, "Pasos básicos", 1, lesson(L2, TITLE, 0)));

        MockHttpServletResponse response = mockMvc.perform(get(lessonUrl(M1, L2, "borrar"))
                .cookie(logIn("INSTRUCTOR"))).andReturn().getResponse();

        assertThat(response.getHeader("Cache-Control")).contains("no-store");
        assertThat(response.getContentAsString()).doesNotContain("/api/v1").doesNotContain("<script>boom");
        assertThat(mainOf(response.getContentAsString())).contains("Borrar lección", ESCAPED_TITLE, "Pasos básicos",
                        "el progreso de los alumnos en esta lección", "Esta acción no se puede deshacer")
                .containsPattern("<form[^>]*action=\"" + lessonUrl(M1, L2, "borrar") + "\"[^>]*method=\"post\"")
                .containsPattern("<button[^>]*class=\"[^\"]*btn-danger[^\"]*\"[^>]*>Sí, borrar la lección</button>")
                .containsPattern("<a[^>]*href=\"" + STRUCTURE + "\">Volver a la estructura</a>");
        WIRE_MOCK_SERVER.verify(0, anyRequestedFor(urlPathMatching("/api/v1/admin/virtual/lessons/.*")));
    }

    @Test
    @DisplayName("confirming deletes the lesson in the API and goes back to the structure, with the notice once")
    void confirmDeletes() throws Exception {
        stub("DELETE", lessonApi(L2), 204, "");
        Cookie session = logIn("INSTRUCTOR");

        mockMvc.perform(send(lessonUrl(M1, L2, "borrar"), session, true))
                .andExpect(redirectedUrl(STRUCTURE + "?modulo=" + M1))
                .andExpect(flash().attribute("structureAlert", "Lección borrada."))
                .andExpect(flash().attribute("structureAlertKind", "success"));

        WIRE_MOCK_SERVER.verify(1, deleteRequestedFor(urlEqualTo(lessonApi(L2)))
                .withHeader("Authorization", equalTo("Bearer " + TestAccessTokens.withRole("INSTRUCTOR"))));
        stubLessons();
        String first = render(get(STRUCTURE + "?modulo=" + M1).cookie(session));
        String second = render(get(STRUCTURE + "?modulo=" + M1).cookie(session));
        assertThat(first).containsPattern("role=\"status\"[^>]*>\\s*<p>Lección borrada\\.</p>");
        assertThat(second).doesNotContain("Lección borrada.");
    }

    @Test
    @DisplayName("a lesson or module that is not in the course is a 404 notice, and an id that is not a UUID too")
    void notFoundPages() throws Exception {
        stubLessons();
        Cookie session = logIn("ADMIN");
        String unknown = "bbbbbbb1-bbbb-4bbb-8bbb-bbbbbbbbbbb1";

        for (Object[] c : new Object[][] {{lessonUrl(M1, unknown, "borrar"), "No encontramos la lección."},
            {lessonUrl(M1, L4, "borrar"), "No encontramos la lección."},
            {lessonUrl("33333333-3333-4333-8333-333333333333", L1, "borrar"), "No encontramos el módulo."}}) {
            MockHttpServletResponse response = mockMvc.perform(get((String) c[0]).cookie(session)).andReturn()
                    .getResponse();

            assertThat(response.getStatus()).isEqualTo(404);
            assertThat(mainOf(response.getContentAsString())).contains((String) c[1], "Volver a los cursos virtuales");
        }
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {COURSE + "/modulos/" + M1 + "/lecciones/no-es-uuid/borrar",
        COURSE + "/modulos/no-es-uuid/lecciones/" + L1 + "/borrar",
        "/gestion/cursos-virtuales/no-es-uuid/modulos/" + M1 + "/lecciones/" + L1 + "/borrar"})
    @DisplayName("an id that is not a UUID is a 404 on the confirmation, without calling the API")
    void invalidIdsOnTheConfirmation(String url) throws Exception {
        mockMvc.perform(get(url).cookie(logIn("ADMIN"))).andExpect(status().isNotFound());

        WIRE_MOCK_SERVER.verify(0, anyRequestedFor(urlPathMatching("/api/v1/admin/.*")));
    }

    @Test
    @DisplayName("a lesson id that is not a UUID is a 404 on the deletion, without calling the API")
    void invalidLessonIdOnDelete() throws Exception {
        MockHttpServletResponse response = mockMvc.perform(send(lessonUrl(M1, "no-es-uuid", "borrar"),
                logIn("ADMIN"), true)).andReturn().getResponse();

        assertThat(response.getStatus()).isEqualTo(404);
        assertThat(mainOf(response.getContentAsString())).contains("No encontramos la lección.");
        WIRE_MOCK_SERVER.verify(0, anyRequestedFor(urlPathMatching("/api/v1/admin/.*")));
    }

    // ---- refusals ----

    static Stream<Arguments> problems() {
        return Stream.of(
                Arguments.of(403, "COURSE_NOT_OWNED", 403, "No podés gestionar este curso."),
                Arguments.of(404, "COURSE_NOT_FOUND", 404, "No encontramos el curso."),
                Arguments.of(404, "MODULE_NOT_FOUND", 404, "No encontramos el módulo."),
                Arguments.of(404, "LESSON_NOT_FOUND", 404, "No encontramos la lección."));
    }

    @ParameterizedTest(name = "API {0} {1} -> page {2}")
    @MethodSource("problems")
    @DisplayName("a refusal of the API is a notice page, worded from its code, on the confirmation, delete and move")
    void problemPages(int apiStatus, String code, int pageStatus, String message) throws Exception {
        stubProblem("GET", COURSE_API, apiStatus, code);
        stubProblem("DELETE", lessonApi(L2), apiStatus, code);
        Cookie session = logIn("INSTRUCTOR");

        for (MockHttpServletRequestBuilder request : List.of(get(lessonUrl(M1, L2, "borrar")).cookie(session),
                send(lessonUrl(M1, L2, "borrar"), session, true), send(lessonUrl(M1, L2, "subir"), session, true))) {
            MockHttpServletResponse response = mockMvc.perform(request).andReturn().getResponse();

            assertThat(response.getStatus()).isEqualTo(pageStatus);
            assertThat(mainOf(response.getContentAsString())).contains(message, "Volver a los cursos virtuales");
            assertThat(response.getContentAsString()).doesNotContain(SECRET).doesNotContain("/api/v1");
        }
    }

    @Test
    @DisplayName("a refused swap on a lesson that is gone is a 404 notice, and the first lesson is put back")
    void moveRefusedByTheApi() throws Exception {
        stubLessons();
        stub("PUT", lessonApi(L2), 200, managed(L2, 0));
        stubProblem("PUT", lessonApi(L1), 404, "LESSON_NOT_FOUND");

        MockHttpServletResponse response = mockMvc.perform(send(lessonUrl(M1, L2, "subir"), logIn("INSTRUCTOR"), true))
                .andReturn().getResponse();

        assertThat(response.getStatus()).isEqualTo(404);
        assertThat(mainOf(response.getContentAsString())).contains("No encontramos la lección.");
        verifyOrderSent(L2, 1, 1);
    }

    @Test
    @DisplayName("an API that fails is a 503: a notice to read or move, the confirmation again to delete")
    void unavailable() throws Exception {
        stubProblem("GET", COURSE_API, 500, "OTHER");
        Cookie session = logIn("ADMIN");

        MockHttpServletResponse read = mockMvc.perform(get(lessonUrl(M1, L2, "borrar")).cookie(session))
                .andReturn().getResponse();
        assertThat(read.getStatus()).isEqualTo(503);
        assertThat(mainOf(read.getContentAsString())).contains(LOAD_FAILED);

        MockHttpServletResponse moved = mockMvc.perform(send(lessonUrl(M1, L2, "bajar"), session, true))
                .andReturn().getResponse();
        assertThat(moved.getStatus()).isEqualTo(503);
        assertThat(mainOf(moved.getContentAsString())).contains(MOVE_FAILED);

        MockHttpServletResponse neither = mockMvc.perform(send(lessonUrl(M1, L2, "borrar"), session, true))
                .andReturn().getResponse();
        assertThat(neither.getStatus()).isEqualTo(503);
        assertThat(mainOf(neither.getContentAsString())).contains(ACTION_FAILED);

        stubCourse(module(M1, "Pasos básicos", 1, lesson(L2, TITLE, 0)));
        stubProblem("DELETE", lessonApi(L2), 500, "OTHER");
        MockHttpServletResponse again = mockMvc.perform(send(lessonUrl(M1, L2, "borrar"), session, true))
                .andReturn().getResponse();
        assertThat(again.getStatus()).isEqualTo(503);
        assertThat(mainOf(again.getContentAsString())).contains(ACTION_FAILED, ESCAPED_TITLE, "Sí, borrar la lección");
        assertThat(again.getContentAsString()).doesNotContain(SECRET).doesNotContain("/api/v1");
    }

    // ---- access ----

    @ParameterizedTest(name = "{0} is refused")
    @ValueSource(strings = {"RECEPTIONIST", "STUDENT"})
    @DisplayName("every other role gets a 403 on every route, and the API is never asked")
    void otherRolesAreRefused(String role) throws Exception {
        Cookie session = logIn(role);
        for (MockHttpServletRequestBuilder request : Stream.concat(
                Stream.of(get(lessonUrl(M1, L2, "borrar")).cookie(session)), posts(session, true).stream()).toList()) {
            mockMvc.perform(request).andExpect(status().isForbidden());
        }

        WIRE_MOCK_SERVER.verify(0, anyRequestedFor(urlPathMatching("/api/v1/admin/.*")));
    }

    @Test
    @DisplayName("an anonymous visitor goes to the login page; a submission without CSRF is refused")
    void anonymousAndCsrf() throws Exception {
        mockMvc.perform(get(lessonUrl(M1, L2, "borrar"))).andExpect(redirectedUrlPattern("**/login"));
        mockMvc.perform(post(lessonUrl(M1, L2, "subir")).with(csrf())).andExpect(redirectedUrlPattern("**/login"));
        Cookie session = logIn("ADMIN");

        for (MockHttpServletRequestBuilder request : posts(session, false)) {
            mockMvc.perform(request).andExpect(status().isForbidden());
        }

        WIRE_MOCK_SERVER.verify(0, anyRequestedFor(urlPathMatching("/api/v1/admin/.*")));
    }
}
