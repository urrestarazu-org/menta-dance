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
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * The virtual course creation and edit screens (#367): who may open them, what they render, what they send to the
 * Virtual API (the exact bodies) and how each validation failure and API refusal comes back, without ever showing
 * the API's body. Each case logs in for real against the stubbed APIs.
 */
@DisplayName("Management virtual course form Integration Tests")
class ManagementVirtualCourseFormIntegrationTest extends BaseIntegrationTest {

    private static final String LIST = "/gestion/cursos-virtuales";
    private static final String NEW = LIST + "/nuevo";
    private static final String ID = "5b6f7c8e-1d2a-4e3b-9c4d-0a1b2c3d4e5f";
    private static final String EDIT = LIST + "/" + ID + "/editar";
    private static final String ADA_ID = "0a1b2c3d-1d2a-4e3b-9c4d-5b6f7c8e9f00";
    private static final String API = "/api/v1/admin/virtual/courses";
    private static final String COURSE_API = API + "/" + ID;
    private static final String USERS_API = "/api/v1/admin/users";
    private static final String SECRET = "secret-detail";
    private static final String BODY = "\"title\":\"Tango base\",\"shortDescription\":\"Corta\","
            + "\"description\":\"Larga\",\"imageUrl\":\"https://img.example/t.png\",\"category\":\"Tango\","
            + "\"level\":\"BEGINNER\"";
    private static final String COURSE = ("{\"courseId\":\"%s\",\"title\":\"Tango base\","
            + "\"shortDescription\":\"Corta\",\"description\":\"Larga\",\"professorId\":\"%s\",\"imageUrl\":\"https://img.example/t.png\","
            + "\"category\":\"Tango\",\"level\":\"BEGINNER\",\"premium\":false,\"status\":\"DRAFT\"}")
            .formatted(ID, ADA_ID);
    private static final String DETAIL = ("{\"courseId\":\"%s\",\"title\":\"Vals\",\"shortDescription\":\"Pasos\","
            + "\"description\":\"Curso completo\",\"professorId\":\"%s\","
            + "\"thumbnailUrl\":\"https://img.example/v.png\","
            + "\"category\":\"Vals\",\"level\":\"ADVANCED\",\"isPremium\":true,\"status\":\"PUBLISHED\",\"modules\":[],"
            + "\"stats\":{\"moduleCount\":0,\"lessonCount\":0,\"totalDuration\":\"0m\"}}").formatted(ID, ADA_ID);

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

    private static void stubInstructors(long total) {
        stub("GET", USERS_API, 200, ("{\"items\":[{\"id\":\"%s\",\"email\":\"ada@example.com\",\"firstName\":\"Ada\","
                + "\"lastName\":\"Lovelace\",\"role\":\"INSTRUCTOR\",\"status\":\"ACTIVE\",\"demo\":false,"
                + "\"createdAt\":\"2026-11-10T09:30:00Z\"}],\"page\":0,\"size\":50,\"totalElements\":%d,"
                + "\"totalPages\":1}").formatted(ADA_ID, total));
    }

    /** A complete submission (the title has spaces around it); the extra pairs replace or add parameters. */
    private static MockHttpServletRequestBuilder submit(String url, Cookie session, String... extra) {
        Map<String, String> params = new LinkedHashMap<>(Map.of("title", " Tango base ", "shortDescription", "Corta",
                "description", "Larga", "imageUrl", "https://img.example/t.png", "category", "Tango",
                "level", "BEGINNER"));
        for (int i = 0; i < extra.length; i += 2) {
            params.put(extra[i], extra[i + 1]);
        }
        MockHttpServletRequestBuilder request = post(url).cookie(session).with(csrf());
        params.forEach(request::param);
        return request;
    }

    private String render(MockHttpServletRequestBuilder request) throws Exception {
        MockHttpServletResponse response = mockMvc.perform(request).andReturn().getResponse();
        assertThat(response.getHeader("Cache-Control")).contains("no-store");
        assertThat(response.getContentAsString()).doesNotContain("/api/v1").doesNotContain(SECRET);
        return mainOf(response.getContentAsString());
    }

    // ---- create ----

    @Test
    @DisplayName("the administrator's form offers the instructors and themselves, the instructor's has no such field")
    void createFormByRole() throws Exception {
        stubInstructors(51);

        assertThat(render(get(NEW).cookie(logIn("ADMIN")))).contains("Nuevo curso virtual", "Yo (administrador)",
                "Ada Lovelace", "Se muestran los primeros 50 profesores.", "se crea como borrador")
                .containsPattern("<form[^>]*action=\"/gestion/cursos-virtuales\"").doesNotContain("premium");
        assertThat(render(get(NEW).cookie(logIn("INSTRUCTOR")))).contains("Nuevo curso virtual")
                .doesNotContain("professorId").doesNotContain("premium");
        WIRE_MOCK_SERVER.verify(1, anyRequestedFor(urlPathEqualTo(USERS_API)));
    }

    @Test
    @DisplayName("an administrator choosing an instructor sends its id, trimmed texts, and is sent to the list")
    void adminAssignsAnInstructor() throws Exception {
        stubInstructors(1);
        stub("POST", API, 201, COURSE);

        mockMvc.perform(submit(LIST, logIn("ADMIN"), "professorId", ADA_ID)).andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl(LIST))
                .andExpect(flash().attribute("virtualCourseSaved", "Curso creado como borrador."));

        WIRE_MOCK_SERVER.verify(1, postRequestedFor(urlEqualTo(API))
                .withHeader("Authorization", equalTo("Bearer " + TestAccessTokens.withRole("ADMIN")))
                .withRequestBody(equalToJson("{" + BODY + ",\"professorId\":\"" + ADA_ID + "\"}")));
    }

    @ParameterizedTest(name = "{0} sends no professorId")
    @ValueSource(strings = {"ADMIN", "INSTRUCTOR"})
    @DisplayName("an administrator who picks nobody, and an instructor whatever they post, send no professorId")
    void noProfessorIdSent(String role) throws Exception {
        stubInstructors(1);
        stub("POST", API, 201, COURSE);

        mockMvc.perform(submit(LIST, logIn(role), "professorId", "INSTRUCTOR".equals(role) ? ADA_ID : ""))
                .andExpect(redirectedUrl(LIST));

        WIRE_MOCK_SERVER.verify(1, postRequestedFor(urlEqualTo(API)).withRequestBody(equalToJson("{" + BODY + "}")));
        WIRE_MOCK_SERVER.verify("INSTRUCTOR".equals(role) ? 0 : 1, anyRequestedFor(urlPathEqualTo(USERS_API)));
    }

    static Stream<Arguments> invalidFields() {
        return Stream.of(
                Arguments.of("title", "", "Ingresá el título."),
                Arguments.of("shortDescription", "x".repeat(501),
                        "La descripción corta puede tener hasta 500 caracteres."),
                Arguments.of("description", "   ", "Ingresá la descripción."),
                Arguments.of("imageUrl", "ftp://img.example/t.png",
                        "La URL de la imagen debe empezar con http:// o https://."),
                Arguments.of("category", "x".repeat(101), "La categoría puede tener hasta 100 caracteres."),
                Arguments.of("level", "EXPERT", "Elegí un nivel."),
                Arguments.of("professorId", "9f9f9f9f-1d2a-4e3b-9c4d-5b6f7c8e9f00",
                        "Elegí un profesor/a de la lista."));
    }

    @ParameterizedTest(name = "{0} = \"{1}\"")
    @MethodSource("invalidFields")
    @DisplayName("an invalid field shows its message, in the summary and next to the field, and the API is not asked")
    void invalidCreate(String field, String value, String message) throws Exception {
        stubInstructors(1);

        String main = render(submit(LIST, logIn("ADMIN"), field, value));

        assertThat(main).contains(message, "Hay 1 error en el formulario").contains("href=\"#" + field + "\"")
                .contains("Tango");
        WIRE_MOCK_SERVER.verify(0, anyRequestedFor(urlPathMatching(API + ".*")));
    }

    static Stream<Arguments> refusals() {
        return Stream.of(
                Arguments.of(400, "INVALID_REQUEST", 400, "Revisá los datos ingresados."),
                Arguments.of(400, "PROFESSOR_MISMATCH", 400, "Solo podés crear cursos a tu nombre."),
                Arguments.of(409, "COURSE_NOT_DRAFT", 503, "No pudimos guardar el curso. Intentá de nuevo."),
                Arguments.of(409, "COURSE_HAS_PROGRESS", 503, "No pudimos guardar el curso. Intentá de nuevo."),
                Arguments.of(500, "OTHER", 503, "No pudimos guardar el curso. Intentá de nuevo."));
    }

    @ParameterizedTest(name = "API {0} {1} -> page {2}")
    @MethodSource("refusals")
    @DisplayName("a refusal of the API is a general form error worded by its code, keeping what was typed")
    void createRefused(int apiStatus, String code, int pageStatus, String message) throws Exception {
        stubInstructors(1);
        stubProblem("POST", API, apiStatus, code);
        Cookie session = logIn("ADMIN");

        MockHttpServletResponse response = mockMvc.perform(submit(LIST, session)).andReturn().getResponse();

        assertThat(response.getStatus()).isEqualTo(pageStatus);
        assertThat(mainOf(response.getContentAsString())).contains(message, "Tango base");
        assertThat(response.getContentAsString()).doesNotContain(SECRET).doesNotContain("/api/v1");
    }

    @Test
    @DisplayName("without the list of instructors the administrator's form is a 503 with a notice")
    void instructorsUnavailable() throws Exception {
        stubProblem("GET", USERS_API, 500, "X");
        Cookie session = logIn("ADMIN");

        for (MockHttpServletRequestBuilder request : new MockHttpServletRequestBuilder[] {
            get(NEW).cookie(session), submit(LIST, session)}) {
            MockHttpServletResponse response = mockMvc.perform(request).andReturn().getResponse();

            assertThat(response.getStatus()).isEqualTo(503);
            assertThat(mainOf(response.getContentAsString())).contains("No pudimos cargar los profesores.");
        }
        WIRE_MOCK_SERVER.verify(0, postRequestedFor(urlEqualTo(API)));
    }

    // ---- edit ----

    @Test
    @DisplayName("the edit form is prefilled from the detail, has the premium box and no instructor field")
    void editPrefill() throws Exception {
        stub("GET", COURSE_API, 200, DETAIL);

        String main = render(get(EDIT).cookie(logIn("INSTRUCTOR")));

        assertThat(main).contains("Editar curso virtual", "Curso completo", "Guardar cambios")
                .containsPattern("<form[^>]*action=\"/gestion/cursos-virtuales/" + ID + "/editar\"")
                .containsPattern("value=\"Vals\"").containsPattern("value=\"https://img.example/v.png\"")
                .containsPattern("<option value=\"ADVANCED\" selected=\"selected\"")
                .containsPattern("<input type=\"checkbox\"[^>]*checked=\"checked\"")
                .containsPattern("<label[^>]*for=\"premium\"[^>]*>Curso premium")
                .contains("aria-describedby=\"premium-help\"").doesNotContain("professorId");
    }

    @ParameterizedTest(name = "premium {0}")
    @ValueSource(booleans = {true, false})
    @DisplayName("sends every field of the form, with the premium box as the flag, and goes back to the list")
    void editSends(boolean premium) throws Exception {
        stub("PUT", COURSE_API, 200, COURSE);

        mockMvc.perform(premium ? submit(EDIT, logIn("INSTRUCTOR"), "premium", "on") : submit(EDIT, logIn("ADMIN")))
                .andExpect(redirectedUrl(LIST))
                .andExpect(flash().attribute("virtualCourseSaved", "Curso actualizado."));

        WIRE_MOCK_SERVER.verify(1, putRequestedFor(urlEqualTo(COURSE_API))
                .withRequestBody(equalToJson("{" + BODY + ",\"premium\":" + premium + "}")));
    }

    static Stream<Arguments> editProblems() {
        return Stream.of(
                Arguments.of(403, "COURSE_NOT_OWNED", 403, "No podés gestionar este curso."),
                Arguments.of(404, "COURSE_NOT_FOUND", 404, "No encontramos el curso."));
    }

    @ParameterizedTest(name = "API {0} {1} -> page {2}")
    @MethodSource("editProblems")
    @DisplayName("a course that is not owned or not found is a notice page, on opening and on saving")
    void editProblem(int apiStatus, String code, int pageStatus, String message) throws Exception {
        stubProblem("GET", COURSE_API, apiStatus, code);
        stubProblem("PUT", COURSE_API, apiStatus, code);
        Cookie session = logIn("INSTRUCTOR");

        for (MockHttpServletRequestBuilder request : new MockHttpServletRequestBuilder[] {
            get(EDIT).cookie(session), submit(EDIT, session)}) {
            MockHttpServletResponse response = mockMvc.perform(request).andReturn().getResponse();

            assertThat(response.getStatus()).isEqualTo(pageStatus);
            assertThat(mainOf(response.getContentAsString())).contains(message, "Volver a los cursos virtuales");
            assertThat(response.getContentAsString()).doesNotContain(SECRET).doesNotContain("/api/v1");
        }
    }

    @ParameterizedTest(name = "API {0} {1} on opening -> 503 notice")
    @CsvSource({"400,INVALID_REQUEST", "500,OTHER"})
    @DisplayName("a course the API cannot give is a 503 notice page")
    void editUnreadable(int apiStatus, String code) throws Exception {
        stubProblem("GET", COURSE_API, apiStatus, code);

        MockHttpServletResponse response = mockMvc.perform(get(EDIT).cookie(logIn("ADMIN"))).andReturn().getResponse();

        assertThat(response.getStatus()).isEqualTo(503);
        assertThat(mainOf(response.getContentAsString())).contains("No pudimos cargar el curso en este momento.");
    }

    @Test
    @DisplayName("an id that is not a UUID is a 404 without calling the API, and a refused save keeps the form")
    void invalidIdAndRefusedEdit() throws Exception {
        Cookie session = logIn("ADMIN");

        mockMvc.perform(get(LIST + "/no-es-uuid/editar").cookie(session)).andExpect(status().isNotFound());
        WIRE_MOCK_SERVER.verify(0, anyRequestedFor(urlPathMatching(API + ".*")));

        stubProblem("PUT", COURSE_API, 400, "INVALID_REQUEST");
        assertThat(render(submit(EDIT, session))).contains("Revisá los datos ingresados.", "Tango base");
        assertThat(render(submit(EDIT, session, "title", ""))).contains("Ingresá el título.");
    }

    @Test
    @DisplayName("the list confirms a saved course once, in a status region")
    void confirmationShownOnce() throws Exception {
        stub("GET", API, 200, "{\"courses\":[]}");
        stub("PUT", COURSE_API, 200, COURSE);
        Cookie session = logIn("ADMIN");
        mockMvc.perform(submit(EDIT, session));

        String first = mainOf(mockMvc.perform(get(LIST).cookie(session)).andReturn().getResponse()
                .getContentAsString());
        String second = mainOf(mockMvc.perform(get(LIST).cookie(session)).andReturn().getResponse()
                .getContentAsString());

        assertThat(first).containsPattern("<[^>]*role=\"status\"[^>]*>\\s*<p>Curso actualizado.</p>");
        assertThat(second).doesNotContain("Curso actualizado.");
    }

    // ---- access ----

    @ParameterizedTest(name = "{0} is refused")
    @ValueSource(strings = {"RECEPTIONIST", "STUDENT"})
    @DisplayName("every other role gets a 403 on every route, and the API is never asked")
    void otherRolesAreRefused(String role) throws Exception {
        Cookie session = logIn(role);

        mockMvc.perform(get(NEW).cookie(session)).andExpect(status().isForbidden());
        mockMvc.perform(get(EDIT).cookie(session)).andExpect(status().isForbidden());
        mockMvc.perform(submit(LIST, session)).andExpect(status().isForbidden());
        mockMvc.perform(submit(EDIT, session)).andExpect(status().isForbidden());

        WIRE_MOCK_SERVER.verify(0, anyRequestedFor(urlPathMatching(API + ".*")));
        WIRE_MOCK_SERVER.verify(0, anyRequestedFor(urlPathEqualTo(USERS_API)));
    }

    @Test
    @DisplayName("an anonymous visitor goes to the login page; a submission without CSRF is refused")
    void anonymousAndCsrf() throws Exception {
        for (String url : new String[] {NEW, EDIT}) {
            mockMvc.perform(get(url)).andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrlPattern("**/login"));
        }
        mockMvc.perform(post(LIST).with(csrf())).andExpect(redirectedUrlPattern("**/login"));
        Cookie session = logIn("ADMIN");

        mockMvc.perform(post(LIST).cookie(session).param("title", "Tango")).andExpect(status().isForbidden());
        mockMvc.perform(post(EDIT).cookie(session).param("title", "Tango")).andExpect(status().isForbidden());

        WIRE_MOCK_SERVER.verify(0, anyRequestedFor(urlPathMatching(API + ".*")));
    }
}
