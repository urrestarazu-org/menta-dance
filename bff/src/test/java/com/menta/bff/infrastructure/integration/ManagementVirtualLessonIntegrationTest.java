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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
 * The lesson management of a virtual course (#367): who may open it, the forms, what they send to the Virtual API
 * (the exact bodies; an empty video id is never sent on edit) and how each refusal comes back, without ever showing
 * the API's body.
 */
@DisplayName("Management virtual lesson Integration Tests")
class ManagementVirtualLessonIntegrationTest extends BaseIntegrationTest {

    private static final String ID = "5b6f7c8e-1d2a-4e3b-9c4d-0a1b2c3d4e5f";
    private static final String M1 = "11111111-1111-4111-8111-111111111111";
    private static final String M2 = "22222222-2222-4222-8222-222222222222";
    private static final String L1 = "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa";
    private static final String L2 = "bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb";
    private static final String COURSE = "/gestion/cursos-virtuales/" + ID;
    private static final String STRUCTURE = COURSE + "/estructura";
    private static final String NEW = COURSE + "/modulos/" + M1 + "/lecciones/nueva";
    private static final String CREATE = COURSE + "/modulos/" + M1 + "/lecciones";
    private static final String EDIT = CREATE + "/" + L1 + "/editar";
    private static final String COURSE_API = "/api/v1/admin/virtual/courses/" + ID;
    private static final String LESSONS_API = "/api/v1/admin/virtual/modules/" + M1 + "/lessons";
    private static final String LESSON_API = "/api/v1/admin/virtual/lessons/" + L1;
    private static final String SECRET = "secret-detail";
    private static final String VIDEO = "video-0001";
    private static final String READ_FAILED = "No pudimos cargar el curso en este momento. Intentá nuevamente en unos "
            + "minutos.";
    private static final String SAVE_FAILED = "No pudimos guardar la lección. Intentá de nuevo.";
    private static final String DURATION = "Ingresá la duración en minutos: un número entero de 0 a 9999.";
    private static final String VIDEO_LENGTH = "El identificador del video puede tener hasta 128 caracteres.";

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
        return "{\"lessonId\":\"%s\",\"title\":\"%s\",\"description\":\"Texto de la lección\",\"durationMinutes\":%d,"
                .formatted(id, title, minutes) + "\"isFree\":%s,\"order\":1,\"videoId\":%s}"
                .formatted(free, videoId == null ? "null" : "\"" + videoId + "\"");
    }

    /** A course whose first module has two lessons, one with a video and free; the second module is empty. */
    private static void stubCourse() {
        stub("GET", COURSE_API, 200, ("{\"courseId\":\"%s\",\"title\":\"Tango base\",\"shortDescription\":\"Corta\","
                + "\"description\":\"Larga\",\"professorId\":\"%s\",\"thumbnailUrl\":\"https://img.example/t.png\","
                + "\"category\":\"Tango\",\"level\":\"BEGINNER\",\"isPremium\":false,\"status\":\"DRAFT\","
                + "\"modules\":[{\"moduleId\":\"%s\",\"title\":\"Pasos básicos\",\"preview\":false,\"order\":1,"
                + "\"lessons\":[%s,%s]},{\"moduleId\":\"%s\",\"title\":\"Giros\",\"preview\":false,\"order\":2,"
                + "\"lessons\":[]}],\"stats\":{\"moduleCount\":2,\"lessonCount\":2,\"totalDuration\":\"25m\"}}")
                .formatted(ID, M1, M1, lesson(L1, "Primer paso", 10, true, VIDEO),
                        lesson(L2, "Segundo paso", 15, false, null), M2));
    }

    private static void stubWrites() {
        String body = "{\"lessonId\":\"%s\",\"moduleId\":\"%s\",\"courseId\":\"" + ID + "\",\"title\":\"Primer paso\","
                + "\"description\":\"Texto\",\"videoId\":null,\"durationMinutes\":10,\"free\":true,\"order\":1}";
        stub("POST", LESSONS_API, 201, body.formatted(L1, M1));
        stub("PUT", LESSON_API, 200, body.formatted(L1, M1));
    }

    /** Valid fields of the form with one of them replaced; {@code null} removes it. */
    private static String[] fields(String name, String value) {
        Map<String, String> fields = new LinkedHashMap<>(Map.of("title", "Primer paso",
                "description", "Texto de la lección", "durationMinutes", "10"));
        fields.put(name, value);
        return fields.entrySet().stream().filter(e -> e.getValue() != null)
                .flatMap(e -> Stream.of(e.getKey(), e.getValue())).toArray(String[]::new);
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
        return List.of(send(CREATE, session, withCsrf, fields("x", null)),
                send(EDIT, session, withCsrf, fields("x", null)));
    }

    private String render(MockHttpServletRequestBuilder request) throws Exception {
        MockHttpServletResponse response = mockMvc.perform(request).andReturn().getResponse();
        assertThat(response.getHeader("Cache-Control")).contains("no-store");
        assertThat(response.getContentAsString()).doesNotContain("/api/v1").doesNotContain(SECRET);
        return mainOf(response.getContentAsString());
    }

    // ---- structure ----

    @Test
    @DisplayName("the structure links every module to Agregar lección and every lesson to Editar, by accessible name")
    void structureLinks() throws Exception {
        stubCourse();

        String main = render(get(STRUCTURE).cookie(logIn("INSTRUCTOR")));

        assertThat(main).containsPattern("<a[^>]*href=\"" + CREATE + "/nueva\">Agregar lección<span class=\"sr-only\">"
                        + "\\s*al módulo Pasos básicos</span></a>")
                .containsPattern("<a[^>]*href=\"/gestion/cursos-virtuales/" + ID + "/modulos/" + M2
                        + "/lecciones/nueva\">Agregar lección<span class=\"sr-only\">\\s*al módulo Giros</span></a>")
                .containsPattern("<a[^>]*href=\"" + EDIT + "\">Editar<span class=\"sr-only\">\\s*lección Primer paso"
                        + "</span></a>")
                .containsPattern("<a[^>]*href=\"" + CREATE + "/" + L2 + "/editar\">Editar<span class=\"sr-only\">"
                        + "\\s*lección Segundo paso</span></a>")
                .doesNotContain(VIDEO).doesNotContain("Borrar");
    }

    // ---- create ----

    @Test
    @DisplayName("the new lesson form posts to the module, starts with duration 0 and goes back to the structure")
    void newForm() throws Exception {
        String main = render(get(NEW).cookie(logIn("ADMIN")));

        assertThat(main).contains("Nueva lección", "Título", "Descripción", "Identificador del video (opcional)",
                "Duración (minutos)", "Lección gratuita: se puede ver sin suscripción")
                .containsPattern("<form[^>]*action=\"" + CREATE + "\"")
                .containsPattern("name=\"durationMinutes\"[^>]*value=\"0\"")
                .containsPattern("<a[^>]*href=\"" + STRUCTURE + "\">Cancelar").doesNotContain("name=\"order\"")
                .doesNotContain("Si lo dejás vacío");
        WIRE_MOCK_SERVER.verify(0, anyRequestedFor(urlPathMatching("/api/v1/admin/.*")));
    }

    static Stream<Arguments> creations() {
        return Stream.of(
                Arguments.of(null, null, "{\"title\":\"Primer paso\",\"description\":\"Texto de la lección\","
                        + "\"durationMinutes\":10,\"free\":false}"),
                Arguments.of(" " + VIDEO + " ", "on", "{\"title\":\"Primer paso\",\"description\":\"Texto de la "
                        + "lección\",\"videoId\":\"" + VIDEO + "\",\"durationMinutes\":10,\"free\":true}"));
    }

    @ParameterizedTest(name = "video {0}, free {1}")
    @MethodSource("creations")
    @DisplayName("creates the lesson with the exact body: trimmed, no order, no video when empty")
    void create(String video, String free, String body) throws Exception {
        stubWrites();
        Cookie session = logIn("INSTRUCTOR");
        String[] params = fields("videoId", video);
        MockHttpServletRequestBuilder request = send(CREATE, session, true, params);
        if (free != null) {
            request.param("free", free);
        }

        mockMvc.perform(request).andExpect(status().is3xxRedirection()).andExpect(redirectedUrl(STRUCTURE))
                .andExpect(flash().attribute("structureAlert", "Lección creada."))
                .andExpect(flash().attribute("structureAlertKind", "success"));

        WIRE_MOCK_SERVER.verify(1, postRequestedFor(urlEqualTo(LESSONS_API))
                .withHeader("Authorization", equalTo("Bearer " + TestAccessTokens.withRole("INSTRUCTOR")))
                .withRequestBody(equalToJson(body)));
    }

    static Stream<Arguments> invalidFields() {
        return Stream.of(
                Arguments.of("title", " ", "Ingresá el título de la lección."),
                Arguments.of("title", "x".repeat(256), "El título puede tener hasta 255 caracteres."),
                Arguments.of("description", "", "Ingresá la descripción de la lección."),
                Arguments.of("description", "x".repeat(5001), "La descripción puede tener hasta 5000 caracteres."),
                Arguments.of("videoId", "dos palabras", "El identificador del video no puede tener espacios."),
                Arguments.of("videoId", "x".repeat(129), VIDEO_LENGTH),
                Arguments.of("durationMinutes", "abc", DURATION), Arguments.of("durationMinutes", "-1", DURATION),
                Arguments.of("durationMinutes", "10000", DURATION));
    }

    @ParameterizedTest(name = "{0} = {2}")
    @MethodSource("invalidFields")
    @DisplayName("an invalid field is reported in the summary and next to it, on both forms, without the API")
    void invalidField(String field, String value, String message) throws Exception {
        Cookie session = logIn("ADMIN");

        for (String url : new String[] {CREATE, EDIT}) {
            assertThat(render(send(url, session, true, fields(field, value)))).contains(message,
                    "Hay 1 error en el formulario", "href=\"#" + field + "\"");
        }
        WIRE_MOCK_SERVER.verify(0, anyRequestedFor(urlPathMatching("/api/v1/admin/.*")));
    }

    // ---- edit ----

    @Test
    @DisplayName("the edit form is prefilled from the lesson in the course detail, with its video id")
    void editPrefill() throws Exception {
        stubCourse();

        String main = render(get(EDIT).cookie(logIn("INSTRUCTOR")));

        assertThat(main).contains("Editar lección", "Guardar cambios", "Si lo dejás vacío no se cambia el video "
                + "asignado.").containsPattern("<form[^>]*action=\"" + EDIT + "\"")
                .containsPattern("value=\"Primer paso\"").containsPattern("value=\"" + VIDEO + "\"")
                .containsPattern("value=\"10\"").contains("Texto de la lección")
                .containsPattern("<input[^>]*type=\"checkbox\"[^>]*checked=\"checked\"");
    }

    static Stream<Arguments> missing() {
        return Stream.of(
                Arguments.of(COURSE + "/modulos/" + M2 + "/lecciones/" + L1 + "/editar", "No encontramos la lección."),
                Arguments.of(CREATE + "/" + L2.replace("b", "c") + "/editar", "No encontramos la lección."),
                Arguments.of(COURSE + "/modulos/" + L2 + "/lecciones/" + L1 + "/editar",
                        "No encontramos el módulo."));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("missing")
    @DisplayName("a module or lesson that is not in the detail is a 404 notice")
    void editMissing(String url, String message) throws Exception {
        stubCourse();

        MockHttpServletResponse response = mockMvc.perform(get(url).cookie(logIn("ADMIN"))).andReturn().getResponse();

        assertThat(response.getStatus()).isEqualTo(404);
        assertThat(mainOf(response.getContentAsString())).contains(message);
    }

    @ParameterizedTest(name = "video \"{0}\"")
    @ValueSource(strings = {"", "video-0002"})
    @DisplayName("edits with the exact body, and an empty video id is not sent")
    void editSends(String video) throws Exception {
        stubWrites();
        String sent = video.isEmpty() ? "" : "\"videoId\":\"" + video + "\",";

        mockMvc.perform(send(EDIT, logIn("ADMIN"), true, fields("videoId", video))
                        .param("free", "on")).andExpect(redirectedUrl(STRUCTURE))
                .andExpect(flash().attribute("structureAlert", "Lección actualizada."));

        WIRE_MOCK_SERVER.verify(1, putRequestedFor(urlEqualTo(LESSON_API)).withRequestBody(equalToJson(
                "{\"title\":\"Primer paso\",\"description\":\"Texto de la lección\"," + sent
                        + "\"durationMinutes\":10,\"free\":true}")));
    }

    // ---- refusals ----

    static Stream<Arguments> problems() {
        return Stream.of(
                Arguments.of(403, "COURSE_NOT_OWNED", "No podés gestionar este curso."),
                Arguments.of(404, "COURSE_NOT_FOUND", "No encontramos el curso."),
                Arguments.of(404, "MODULE_NOT_FOUND", "No encontramos el módulo."),
                Arguments.of(404, "LESSON_NOT_FOUND", "No encontramos la lección."));
    }

    @ParameterizedTest(name = "API {0} {1}")
    @MethodSource("problems")
    @DisplayName("a refusal of the API is a notice page, with its own status, on every screen that calls it")
    void problemPages(int apiStatus, String code, String message) throws Exception {
        for (String method : new String[] {"GET", "POST", "PUT"}) {
            for (String path : new String[] {COURSE_API, LESSONS_API, LESSON_API}) {
                stubProblem(method, path, apiStatus, code);
            }
        }
        Cookie session = logIn("INSTRUCTOR");

        for (MockHttpServletRequestBuilder request : List.of(get(EDIT).cookie(session),
                send(CREATE, session, true, fields("x", null)), send(EDIT, session, true, fields("x", null)))) {
            MockHttpServletResponse response = mockMvc.perform(request).andReturn().getResponse();

            assertThat(response.getStatus()).isEqualTo(apiStatus);
            assertThat(mainOf(response.getContentAsString())).contains(message, "Volver a los cursos virtuales");
            assertThat(response.getContentAsString()).doesNotContain(SECRET).doesNotContain("/api/v1");
        }
    }

    @Test
    @DisplayName("an API that fails is a 503, and a 400 is a form error: both keep what was typed")
    void unavailableAndInvalid() throws Exception {
        for (String method : new String[] {"GET", "POST", "PUT"}) {
            for (String path : new String[] {COURSE_API, LESSONS_API, LESSON_API}) {
                stubProblem(method, path, 500, "OTHER");
            }
        }
        Cookie session = logIn("ADMIN");

        assertUnavailable(get(EDIT).cookie(session), READ_FAILED);
        assertUnavailable(send(CREATE, session, true, fields("x", null)), SAVE_FAILED);
        assertUnavailable(send(EDIT, session, true, fields("x", null)), SAVE_FAILED);

        stubProblem("POST", LESSONS_API, 400, "INVALID_REQUEST");
        stubProblem("PUT", LESSON_API, 400, "INVALID_REQUEST");
        for (String url : new String[] {CREATE, EDIT}) {
            MockHttpServletResponse invalid = mockMvc.perform(send(url, session, true, fields("x", null)))
                    .andReturn().getResponse();
            assertThat(invalid.getStatus()).isEqualTo(400);
            assertThat(mainOf(invalid.getContentAsString())).contains("Revisá los datos ingresados.", "Primer paso");
        }
    }

    private void assertUnavailable(MockHttpServletRequestBuilder request, String message) throws Exception {
        MockHttpServletResponse response = mockMvc.perform(request).andReturn().getResponse();
        assertThat(response.getStatus()).isEqualTo(503);
        assertThat(mainOf(response.getContentAsString())).contains(message);
        assertThat(response.getContentAsString()).doesNotContain(SECRET).doesNotContain("/api/v1");
    }

    // ---- access ----

    @ParameterizedTest(name = "{0} is refused")
    @ValueSource(strings = {"RECEPTIONIST", "STUDENT"})
    @DisplayName("every other role gets a 403 on every route, and the API is never asked")
    void otherRolesAreRefused(String role) throws Exception {
        Cookie session = logIn(role);

        for (MockHttpServletRequestBuilder request : Stream.concat(Stream.of(get(NEW), get(EDIT))
                .map(r -> r.cookie(session)), posts(session, true).stream()).toList()) {
            mockMvc.perform(request).andExpect(status().isForbidden());
        }

        WIRE_MOCK_SERVER.verify(0, anyRequestedFor(urlPathMatching("/api/v1/admin/.*")));
    }

    @Test
    @DisplayName("an anonymous visitor goes to the login page; a submission without CSRF is refused")
    void anonymousAndCsrf() throws Exception {
        for (String url : new String[] {NEW, EDIT}) {
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
