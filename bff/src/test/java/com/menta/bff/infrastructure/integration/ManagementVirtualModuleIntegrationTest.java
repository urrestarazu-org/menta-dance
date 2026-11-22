package com.menta.bff.infrastructure.integration;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.anyRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
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
 * The structure screen of a virtual course and its module management (#367): who may open them, what they render
 * (modules in order, lessons read only, the move buttons by position), what they send to the Virtual API (the exact
 * bodies, the whole list of ids on a move) and how each refusal comes back, without ever showing the API's body.
 */
@DisplayName("Management virtual module Integration Tests")
class ManagementVirtualModuleIntegrationTest extends BaseIntegrationTest {

    private static final String LIST = "/gestion/cursos-virtuales";
    private static final String ID = "5b6f7c8e-1d2a-4e3b-9c4d-0a1b2c3d4e5f";
    private static final String M1 = "11111111-1111-4111-8111-111111111111";
    private static final String M2 = "22222222-2222-4222-8222-222222222222";
    private static final String M3 = "33333333-3333-4333-8333-333333333333";
    private static final String COURSE = LIST + "/" + ID;
    private static final String STRUCTURE = COURSE + "/estructura";
    private static final String NEW = COURSE + "/modulos/nuevo";
    private static final String CREATE = COURSE + "/modulos";
    private static final String EDIT = COURSE + "/modulos/" + M2 + "/editar";
    private static final String UP = COURSE + "/modulos/" + M2 + "/subir";
    private static final String DOWN = COURSE + "/modulos/" + M2 + "/bajar";
    private static final String COURSE_API = "/api/v1/admin/virtual/courses/" + ID;
    private static final String MODULES_API = COURSE_API + "/modules";
    private static final String REORDER_API = MODULES_API + "/reorder";
    private static final String MODULE_API = "/api/v1/admin/virtual/modules/" + M2;
    private static final String SECRET = "secret-detail";
    private static final String VIDEO = "video-0001";
    private static final String MODULE_JSON = "{\"moduleId\":\"%s\",\"courseId\":\"" + ID
            + "\",\"title\":\"Giros\",\"preview\":false,\"order\":2}";
    private static final String CHANGED = "La estructura del curso cambió. Revisá el orden e intentá de nuevo.";

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

    private static String lesson(String id, String title, int minutes, boolean free, String videoId) {
        return "{\"lessonId\":\"%s\",\"title\":\"%s\",\"description\":\"Descripción\",\"durationMinutes\":%d,"
                .formatted(id, title, minutes) + "\"isFree\":%s,\"order\":1,\"videoId\":%s}"
                .formatted(free, videoId == null ? "null" : "\"" + videoId + "\"");
    }

    private static String module(String id, String title, boolean preview, int order, String... lessons) {
        return "{\"moduleId\":\"%s\",\"title\":\"%s\",\"preview\":%s,\"order\":%d,\"lessons\":[%s]}"
                .formatted(id, title, preview, order, String.join(",", lessons));
    }

    private static void stubCourse(String... modules) {
        stub("GET", COURSE_API, 200, ("{\"courseId\":\"%s\",\"title\":\"Tango base\",\"shortDescription\":\"Corta\","
                + "\"description\":\"Larga\",\"professorId\":\"%s\",\"thumbnailUrl\":\"https://img.example/t.png\","
                + "\"category\":\"Tango\",\"level\":\"BEGINNER\",\"isPremium\":false,\"status\":\"DRAFT\","
                + "\"modules\":[%s],\"stats\":{\"moduleCount\":%d,\"lessonCount\":2,\"totalDuration\":\"25m\"}}")
                .formatted(ID, M1, String.join(",", modules), modules.length));
    }

    /** Three modules, returned out of order on purpose: the screen shows them by their {@code order}. */
    private static void stubThreeModules() {
        stubCourse(module(M2, "Giros", false, 2), module(M3, "Cierre", false, 3),
                module(M1, "Pasos básicos", true, 1, lesson("l-1", "Primer paso", 10, true, VIDEO),
                        lesson("l-2", "Segundo paso", 15, false, null)));
    }

    private static MockHttpServletRequestBuilder send(String url, Cookie session, boolean withCsrf, String... params) {
        MockHttpServletRequestBuilder request = post(url).cookie(session);
        if (withCsrf) {
            request.with(csrf());
        }
        for (int i = 0; i < params.length; i += 2) {
            request.param(params[i], params[i + 1]);
        }
        return request;
    }

    private static List<MockHttpServletRequestBuilder> posts(Cookie session, boolean withCsrf) {
        return List.of(send(CREATE, session, withCsrf, "title", "Pasos"),
                send(EDIT, session, withCsrf, "title", "Pasos"), send(UP, session, withCsrf),
                send(DOWN, session, withCsrf));
    }

    private static List<MockHttpServletRequestBuilder> routes(Cookie session) {
        return Stream.concat(Stream.of(get(STRUCTURE), get(NEW), get(EDIT)).map(r -> r.cookie(session)),
                posts(session, true).stream()).toList();
    }

    private String render(MockHttpServletRequestBuilder request) throws Exception {
        MockHttpServletResponse response = mockMvc.perform(request).andReturn().getResponse();
        assertThat(response.getHeader("Cache-Control")).contains("no-store");
        assertThat(response.getContentAsString()).doesNotContain("/api/v1").doesNotContain(SECRET)
                .doesNotContain(VIDEO);
        return mainOf(response.getContentAsString());
    }

    // ---- structure ----

    @Test
    @DisplayName("shows the course, its modules in order with their lessons read only, and the links")
    void structure() throws Exception {
        stubThreeModules();

        String main = render(get(STRUCTURE).cookie(logIn("INSTRUCTOR")));

        assertThat(main).contains("Tango base", "Borrador", "3", "25m").containsSubsequence("Pasos básicos", "Giros",
                "Cierre").containsSubsequence("Primer paso", "10 min", "Gratuita", "Con video", "Segundo paso",
                "15 min", "Con suscripción", "Sin video").containsOnlyOnce("Vista previa")
                .containsPattern("<a[^>]*href=\"/gestion/cursos-virtuales\">Volver")
                .containsPattern("<a[^>]*href=\"/gestion/cursos-virtuales/" + ID + "/modulos/" + M2
                        + "/editar\">Editar módulo<span class=\"sr-only\">\\s*Giros</span></a>")
                .containsPattern("<a[^>]*href=\"/gestion/cursos-virtuales/" + ID
                        + "/modulos/nuevo\">Agregar módulo</a>")
                .doesNotContain("disabled").doesNotContain("Publicar").doesNotContain("Borrar curso");
    }

    static Stream<Arguments> positions() {
        return Stream.of(Arguments.of(M1, false, true), Arguments.of(M2, true, true), Arguments.of(M3, true, false));
    }

    @ParameterizedTest(name = "module {0}: up {1}, down {2}")
    @MethodSource("positions")
    @DisplayName("the first module has no Subir and the last has no Bajar, as forms with an accessible name")
    void moveButtonsByPosition(String moduleId, boolean up, boolean down) throws Exception {
        stubThreeModules();

        String main = render(get(STRUCTURE).cookie(logIn("ADMIN")));

        assertThat(main.contains("/modulos/" + moduleId + "/subir")).isEqualTo(up);
        assertThat(main.contains("/modulos/" + moduleId + "/bajar")).isEqualTo(down);
        if (up) {
            assertThat(main).containsPattern("<form[^>]*method=\"post\"[^>]*action=\"/gestion/cursos-virtuales/" + ID
                    + "/modulos/" + moduleId + "/subir\"")
                    .containsPattern("Subir<span class=\"sr-only\">\\s*el módulo");
        }
    }

    @Test
    @DisplayName("a single module has no move buttons and an empty course says so")
    void singleAndEmpty() throws Exception {
        Cookie session = logIn("ADMIN");
        stubCourse(module(M1, "Pasos básicos", false, 1));
        assertThat(render(get(STRUCTURE).cookie(session))).contains("Pasos básicos").doesNotContain("Subir")
                .doesNotContain("Bajar").doesNotContain("todavía no tiene módulos");

        stubCourse();
        assertThat(render(get(STRUCTURE).cookie(session))).contains("Este curso todavía no tiene módulos.",
                "Agregar módulo").doesNotContain("Subir").doesNotContain("Bajar");
    }

    // ---- create ----

    @Test
    @DisplayName("the new module form posts to the course and goes back to the structure")
    void newForm() throws Exception {
        assertThat(render(get(NEW).cookie(logIn("ADMIN")))).contains("Nuevo módulo", "Título",
                "Vista previa gratuita: las lecciones de este módulo se pueden ver sin suscripción")
                .containsPattern("<form[^>]*action=\"/gestion/cursos-virtuales/" + ID + "/modulos\"")
                .containsPattern("<a[^>]*href=\"/gestion/cursos-virtuales/" + ID + "/estructura\">Cancelar")
                .doesNotContain("name=\"order\"");
        WIRE_MOCK_SERVER.verify(0, anyRequestedFor(urlPathMatching("/api/v1/admin/.*")));
    }

    static Stream<Arguments> invalidTitles() {
        return Stream.of(Arguments.of("", "Ingresá el título."), Arguments.of("   ", "Ingresá el título."),
                Arguments.of("x".repeat(256), "El título puede tener hasta 255 caracteres."));
    }

    @ParameterizedTest(name = "preview {0}")
    @ValueSource(booleans = {true, false})
    @DisplayName("creates the module with a trimmed title and the preview flag, and no order")
    void create(boolean preview) throws Exception {
        stub("POST", MODULES_API, 201, MODULE_JSON.formatted(M1));
        Cookie session = logIn("INSTRUCTOR");

        mockMvc.perform(preview ? send(CREATE, session, true, "title", " Pasos ", "preview", "on")
                        : send(CREATE, session, true, "title", " Pasos "))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl(STRUCTURE))
                .andExpect(flash().attribute("structureAlert", "Módulo creado."))
                .andExpect(flash().attribute("structureAlertKind", "success"));

        WIRE_MOCK_SERVER.verify(1, postRequestedFor(urlEqualTo(MODULES_API))
                .withHeader("Authorization", equalTo("Bearer " + TestAccessTokens.withRole("INSTRUCTOR")))
                .withRequestBody(equalToJson("{\"title\":\"Pasos\",\"preview\":" + preview + "}")));
    }

    @ParameterizedTest(name = "{1}")
    @MethodSource("invalidTitles")
    @DisplayName("an invalid title shows its message, in the summary and next to the field, and the API is not asked")
    void invalidTitle(String title, String message) throws Exception {
        MockHttpServletRequestBuilder create = send(CREATE, logIn("ADMIN"), true, "title", title, "preview", "on");

        assertThat(render(create)).contains(message, "Hay 1 error en el formulario").contains("href=\"#title\"")
                .containsPattern("<input[^>]*type=\"checkbox\"[^>]*checked=\"checked\"");
        WIRE_MOCK_SERVER.verify(0, anyRequestedFor(urlPathMatching("/api/v1/admin/.*")));
    }

    // ---- edit ----

    @Test
    @DisplayName("the edit form is prefilled from the module in the course detail")
    void editPrefill() throws Exception {
        stubCourse(module(M1, "Pasos", false, 1), module(M2, "Giros", true, 2));

        assertThat(render(get(EDIT).cookie(logIn("INSTRUCTOR")))).contains("Editar módulo", "Guardar cambios")
                .containsPattern("<form[^>]*action=\"/gestion/cursos-virtuales/" + ID + "/modulos/" + M2
                        + "/editar\"").containsPattern("value=\"Giros\"")
                .containsPattern("<input[^>]*type=\"checkbox\"[^>]*checked=\"checked\"");
    }

    @Test
    @DisplayName("a module that is not in the detail is a 404 notice")
    void editUnknownModule() throws Exception {
        stubCourse(module(M1, "Pasos", false, 1));

        MockHttpServletResponse response = mockMvc.perform(get(EDIT).cookie(logIn("ADMIN"))).andReturn().getResponse();

        assertThat(response.getStatus()).isEqualTo(404);
        assertThat(mainOf(response.getContentAsString())).contains("No encontramos el módulo.");
    }

    @Test
    @DisplayName("sends title and preview to the module, and goes back to the structure")
    void editSends() throws Exception {
        stub("PUT", MODULE_API, 200, MODULE_JSON.formatted(M2));

        mockMvc.perform(send(EDIT, logIn("ADMIN"), true, "title", " Giros ")).andExpect(redirectedUrl(STRUCTURE))
                .andExpect(flash().attribute("structureAlert", "Módulo actualizado."));

        WIRE_MOCK_SERVER.verify(1, putRequestedFor(urlEqualTo(MODULE_API))
                .withRequestBody(equalToJson("{\"title\":\"Giros\",\"preview\":false}")));
    }

    // ---- move ----

    static Stream<Arguments> moves() {
        return Stream.of(
                Arguments.of(UP, new String[] {M2, M1, M3}),
                Arguments.of(DOWN, new String[] {M1, M3, M2}),
                Arguments.of(COURSE + "/modulos/" + M3 + "/subir", new String[] {M1, M3, M2}),
                Arguments.of(COURSE + "/modulos/" + M1 + "/bajar", new String[] {M2, M1, M3}));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("moves")
    @DisplayName("a move sends the whole list of ids in the new order and goes back to the structure")
    void move(String url, String[] expected) throws Exception {
        stubThreeModules();
        stub("PUT", REORDER_API, 200, "{\"modules\":[]}");

        mockMvc.perform(send(url, logIn("INSTRUCTOR"), true)).andExpect(redirectedUrl(STRUCTURE))
                .andExpect(flash().attribute("structureAlert", "Módulo movido."))
                .andExpect(flash().attribute("structureAlertKind", "success"));

        WIRE_MOCK_SERVER.verify(1, putRequestedFor(urlEqualTo(REORDER_API)).withRequestBody(
                equalToJson("{\"moduleIds\":[\"%s\",\"%s\",\"%s\"]}".formatted((Object[]) expected))));
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {COURSE + "/modulos/" + M1 + "/subir", COURSE + "/modulos/" + M3 + "/bajar",
        COURSE + "/modulos/44444444-4444-4444-8444-444444444444/subir"})
    @DisplayName("a module on the edge, or one that no longer exists, is not moved and the API is not asked to")
    void moveNowhere(String url) throws Exception {
        stubThreeModules();

        mockMvc.perform(send(url, logIn("ADMIN"), true)).andExpect(redirectedUrl(STRUCTURE))
                .andExpect(flash().attribute("structureAlertKind", "info"));

        WIRE_MOCK_SERVER.verify(0, putRequestedFor(urlPathMatching("/api/v1/admin/.*")));
    }

    @Test
    @DisplayName("a changed structure is an error notice on the structure, shown once, and nothing else changes")
    void moveMismatch() throws Exception {
        stubThreeModules();
        stubProblem("PUT", REORDER_API, 409, "MODULE_REORDER_MISMATCH");
        Cookie session = logIn("ADMIN");

        mockMvc.perform(send(UP, session, true)).andExpect(redirectedUrl(STRUCTURE))
                .andExpect(flash().attribute("structureAlert", CHANGED))
                .andExpect(flash().attribute("structureAlertKind", "error"));

        String first = render(get(STRUCTURE).cookie(session));
        String second = render(get(STRUCTURE).cookie(session));
        assertThat(first).containsPattern("<[^>]*role=\"alert\"[^>]*>\\s*<p>" + CHANGED + "</p>");
        assertThat(second).doesNotContain(CHANGED);
    }

    // ---- refusals ----

    static Stream<Arguments> problems() {
        return Stream.of(
                Arguments.of(403, "COURSE_NOT_OWNED", 403, "No podés gestionar este curso."),
                Arguments.of(404, "COURSE_NOT_FOUND", 404, "No encontramos el curso."),
                Arguments.of(404, "MODULE_NOT_FOUND", 404, "No encontramos el módulo."));
    }

    @ParameterizedTest(name = "API {0} {1} -> page {2}")
    @MethodSource("problems")
    @DisplayName("a course or module that is not owned or not found is a notice page on every screen")
    void problemPages(int apiStatus, String code, int pageStatus, String message) throws Exception {
        for (String method : new String[] {"GET", "POST", "PUT"}) {
            for (String path : new String[] {COURSE_API, MODULES_API, REORDER_API, MODULE_API}) {
                stubProblem(method, path, apiStatus, code);
            }
        }
        Cookie session = logIn("INSTRUCTOR");

        for (MockHttpServletRequestBuilder request : List.of(get(STRUCTURE).cookie(session),
                get(EDIT).cookie(session), send(CREATE, session, true, "title", "Pasos"),
                send(EDIT, session, true, "title", "Pasos"), send(UP, session, true))) {
            MockHttpServletResponse response = mockMvc.perform(request).andReturn().getResponse();

            assertThat(response.getStatus()).isEqualTo(pageStatus);
            assertThat(mainOf(response.getContentAsString())).contains(message, "Volver a los cursos virtuales");
            assertThat(response.getContentAsString()).doesNotContain(SECRET).doesNotContain("/api/v1");
        }
    }

    @Test
    @DisplayName("an API that fails is a 503: a notice page to read or move, a form error to save, keeping the text")
    void unavailable() throws Exception {
        for (String method : new String[] {"GET", "POST", "PUT"}) {
            for (String path : new String[] {COURSE_API, MODULES_API, REORDER_API, MODULE_API}) {
                stubProblem(method, path, 500, "OTHER");
            }
        }
        Cookie session = logIn("ADMIN");

        String read = "No pudimos cargar el curso en este momento. Intentá nuevamente en unos minutos.";
        String save = "No pudimos guardar el módulo. Intentá de nuevo.";
        String moveFailed = "No pudimos mover el módulo. Intentá de nuevo.";
        Object[][] cases = {{get(STRUCTURE).cookie(session), read}, {get(EDIT).cookie(session), read},
            {send(CREATE, session, true, "title", "Pasos"), save}, {send(EDIT, session, true, "title", "Pasos"), save},
            {send(DOWN, session, true), moveFailed}};
        for (Object[] c : cases) {
            MockHttpServletResponse response = mockMvc.perform((MockHttpServletRequestBuilder) c[0]).andReturn()
                    .getResponse();

            assertThat(response.getStatus()).isEqualTo(503);
            assertThat(mainOf(response.getContentAsString())).contains((String) c[1]);
            assertThat(response.getContentAsString()).doesNotContain(SECRET).doesNotContain("/api/v1");
        }
        stubProblem("POST", MODULES_API, 400, "INVALID_REQUEST");
        MockHttpServletResponse invalid = mockMvc.perform(send(CREATE, session, true, "title", "Pasos"))
                .andReturn().getResponse();
        assertThat(invalid.getStatus()).isEqualTo(400);
        assertThat(mainOf(invalid.getContentAsString())).contains("Revisá los datos ingresados.", "Pasos");
    }

    @Test
    @DisplayName("an id that is not a UUID is a 404 without calling the API")
    void invalidId() throws Exception {
        mockMvc.perform(get(LIST + "/no-es-uuid/estructura").cookie(logIn("ADMIN"))).andExpect(status().isNotFound());

        WIRE_MOCK_SERVER.verify(0, anyRequestedFor(urlPathMatching("/api/v1/admin/.*")));
    }

    // ---- access ----

    @ParameterizedTest(name = "{0} is refused")
    @ValueSource(strings = {"RECEPTIONIST", "STUDENT"})
    @DisplayName("every other role gets a 403 on every route, and the API is never asked")
    void otherRolesAreRefused(String role) throws Exception {
        for (MockHttpServletRequestBuilder request : routes(logIn(role))) {
            mockMvc.perform(request).andExpect(status().isForbidden());
        }

        WIRE_MOCK_SERVER.verify(0, anyRequestedFor(urlPathMatching("/api/v1/admin/.*")));
    }

    @Test
    @DisplayName("an anonymous visitor goes to the login page; a submission without CSRF is refused")
    void anonymousAndCsrf() throws Exception {
        for (String url : new String[] {STRUCTURE, NEW, EDIT}) {
            mockMvc.perform(get(url)).andExpect(redirectedUrlPattern("**/login"));
        }
        mockMvc.perform(post(CREATE).with(csrf())).andExpect(redirectedUrlPattern("**/login"));
        Cookie session = logIn("ADMIN");

        for (MockHttpServletRequestBuilder request : posts(session, false)) {
            mockMvc.perform(request).andExpect(status().isForbidden());
        }

        WIRE_MOCK_SERVER.verify(0, anyRequestedFor(urlPathMatching("/api/v1/admin/.*")));
    }
}
