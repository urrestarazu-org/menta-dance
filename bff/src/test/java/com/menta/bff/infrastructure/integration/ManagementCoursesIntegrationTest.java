package com.menta.bff.infrastructure.integration;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.anyRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.patchRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.menta.bff.infrastructure.integration.HtmlSections.mainOf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.http.Fault;
import jakarta.servlet.http.Cookie;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * The physical course screens (#368): who may open the list and the create and edit forms, what they render,
 * what they send to the Physical API (the weekday and time converted to UTC, always together) and how each answer
 * and each API error code comes back, without ever showing the API's body. Each case logs in for real against the
 * stubbed Auth API and reuses the session cookie. An instructor's own display name comes from the stubbed Auth API's
 * current-user endpoint (#749).
 */
@ExtendWith(OutputCaptureExtension.class)
@DisplayName("Management courses Integration Tests")
class ManagementCoursesIntegrationTest extends BaseIntegrationTest {

    private static final String LIST = "/gestion/cursos-presenciales";
    private static final String NEW = LIST + "/nuevo";
    private static final String COURSE_ID = "5b6f7c8e-1d2a-4e3b-9c4d-0a1b2c3d4e5f";
    private static final String EDIT = LIST + "/" + COURSE_ID + "/editar";
    private static final String ADA_ID = "0a1b2c3d-1d2a-4e3b-9c4d-5b6f7c8e9f00";
    private static final String COURSES_API = "/api/v1/admin/physical/courses";
    private static final String COURSE_API = COURSES_API + "/" + COURSE_ID;
    private static final String USERS_API = "/api/v1/admin/users";
    private static final String ME_API = "/api/v1/users/me";
    private static final String NO_STORE = "no-store";
    private static final String[] FIELDS = {"title", "description", "professorId", "localDay", "localTime",
        "durationMinutes", "level", "capacity"};

    /** Wednesday 01:00 UTC is Tuesday 22:00 in Buenos Aires. */
    private static String courseJson(String id, String title, String status) {
        return ("{\"courseId\":\"%s\",\"title\":\"%s\",\"description\":\"Nivel inicial\",\"professorId\":\"%s\","
                + "\"professorName\":\"Ada Lovelace\",\"dayOfWeek\":\"WEDNESDAY\",\"startTime\":\"01:00:00\","
                + "\"durationMinutes\":60,\"level\":\"BEGINNER\",\"capacity\":15,\"status\":\"%s\"}")
                .formatted(id, title, ADA_ID, status);
    }

    private static final String SALSA = courseJson(COURSE_ID, "Salsa", "ACTIVE");

    private Cookie logInWith(String accessToken) throws Exception {
        WIRE_MOCK_SERVER.stubFor(WireMock.post(urlEqualTo("/api/v1/auth/login")).willReturn(aResponse()
                .withStatus(200).withHeader("Content-Type", "application/json")
                .withHeader("X-Refresh-Token", "refresh-token-abc")
                .withBody("{\"access_token\": \"%s\", \"token_type\": \"Bearer\", \"expires_in\": 3600}"
                        .formatted(accessToken))));
        Cookie session = mockMvc.perform(post("/login").param("email", "user@example.com")
                        .param("password", "Password1").with(csrf()))
                .andReturn().getResponse().getCookie("SESSION");
        assertThat(session).isNotNull();
        return session;
    }

    private Cookie logIn(String role) throws Exception {
        return logInWith(TestAccessTokens.withRole(role));
    }

    private static String instructorToken() {
        return TestAccessTokens.withPayload("{\"sub\":\"" + ADA_ID + "\",\"role\":\"INSTRUCTOR\"}");
    }

    private static void stubCourses(int status, String body) {
        WIRE_MOCK_SERVER.stubFor(WireMock.get(urlPathEqualTo(COURSES_API)).willReturn(aResponse()
                .withStatus(status).withHeader("Content-Type", "application/json").withBody(body)));
    }

    private static void stubCourseList(String... courses) {
        stubCourses(200, "{\"courses\":[%s]}".formatted(String.join(",", courses)));
    }

    private static void stubProblem(String method, String path, int status, String code) {
        WIRE_MOCK_SERVER.stubFor(WireMock.request(method, urlPathEqualTo(path)).willReturn(aResponse()
                .withStatus(status).withHeader("Content-Type", "application/problem+json")
                .withBody(("{\"type\":\"https://menta.dance/problems/x\",\"status\":%d,\"detail\":\"secret-detail\","
                        + "\"code\":\"%s\"}").formatted(status, code))));
    }

    private static void stubCreated() {
        WIRE_MOCK_SERVER.stubFor(WireMock.post(urlPathEqualTo(COURSES_API)).willReturn(aResponse()
                .withStatus(201).withHeader("Content-Type", "application/json").withBody(SALSA)));
    }

    private static void stubUpdated() {
        WIRE_MOCK_SERVER.stubFor(WireMock.patch(urlPathEqualTo(COURSE_API)).willReturn(aResponse()
                .withStatus(200).withHeader("Content-Type", "application/json").withBody(SALSA)));
    }

    private static String instructorItem(String id, String email, String first, String last) {
        return ("{\"id\":\"%s\",\"email\":\"%s\",\"firstName\":\"%s\",\"lastName\":\"%s\",\"role\":\"INSTRUCTOR\","
                + "\"status\":\"ACTIVE\",\"demo\":false,\"createdAt\":\"2026-11-10T09:30:00\"}")
                .formatted(id, email, first, last);
    }

    private static void stubInstructors(long total, String... items) {
        WIRE_MOCK_SERVER.stubFor(WireMock.get(urlPathEqualTo(USERS_API)).willReturn(aResponse()
                .withStatus(200).withHeader("Content-Type", "application/json")
                .withBody("{\"items\":[%s],\"page\":0,\"size\":50,\"totalElements\":%d,\"totalPages\":1}"
                        .formatted(String.join(",", items), total))));
    }

    private static void stubAda() {
        stubInstructors(1, instructorItem(ADA_ID, "ada@example.com", "Ada", "Lovelace"));
    }

    /** The signed-in instructor's own account, as {@code GET /api/v1/users/me} answers it. */
    private static void stubOwnAccount(String firstName, String lastName) {
        WIRE_MOCK_SERVER.stubFor(WireMock.get(urlPathEqualTo(ME_API)).willReturn(aResponse()
                .withStatus(200).withHeader("Content-Type", "application/json")
                .withBody("{\"userId\":\"%s\",\"firstName\":%s,\"lastName\":%s,\"role\":\"INSTRUCTOR\"}"
                        .formatted(ADA_ID, firstName, lastName))));
    }

    private static MockHttpServletRequestBuilder form(
            MockHttpServletRequestBuilder request, Cookie session, String... namesAndValues) {
        request.cookie(session).with(csrf());
        for (int i = 0; i < namesAndValues.length; i += 2) {
            request.param(namesAndValues[i], namesAndValues[i + 1]);
        }
        return request;
    }

    /** A complete submission of the form (Tuesday 22:00 local); the extra pairs replace the defaults. */
    private static MockHttpServletRequestBuilder submission(
            MockHttpServletRequestBuilder request, Cookie session, String... extra) {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("title", " Salsa ");
        params.put("description", "Nivel inicial");
        params.put("localDay", "TUESDAY");
        params.put("localTime", "22:00");
        params.put("durationMinutes", "60");
        params.put("level", "BEGINNER");
        params.put("capacity", "15");
        for (int i = 0; i < extra.length; i += 2) {
            params.put(extra[i], extra[i + 1]);
        }
        request.cookie(session).with(csrf());
        params.forEach(request::param);
        return request;
    }

    private String mainOfGet(String url, Cookie session) throws Exception {
        return mainOf(mockMvc.perform(get(url).cookie(session)).andReturn().getResponse().getContentAsString());
    }

    // ---- access ----

    @ParameterizedTest(name = "{0} opens the list")
    @ValueSource(strings = {"ADMIN", "INSTRUCTOR"})
    @DisplayName("an administrator and an instructor open the list, which is never cached")
    void adminAndInstructorOpenTheList(String role) throws Exception {
        stubCourseList(SALSA);

        MvcResult result = mockMvc.perform(get(LIST).cookie(logIn(role))).andExpect(status().isOk()).andReturn();

        assertThat(result.getResponse().getHeader("Cache-Control")).contains(NO_STORE);
        WIRE_MOCK_SERVER.verify(getRequestedFor(urlPathEqualTo(COURSES_API))
                .withHeader("Authorization", equalTo("Bearer " + TestAccessTokens.withRole(role))));
    }

    @ParameterizedTest(name = "{0} is refused")
    @ValueSource(strings = {"RECEPTIONIST", "STUDENT"})
    @DisplayName("every other role gets a 403 on every route, and the API is never asked")
    void otherRolesAreRefused(String role) throws Exception {
        Cookie session = logIn(role);

        mockMvc.perform(get(LIST).cookie(session)).andExpect(status().isForbidden());
        mockMvc.perform(get(NEW).cookie(session)).andExpect(status().isForbidden());
        mockMvc.perform(get(EDIT).cookie(session)).andExpect(status().isForbidden());
        mockMvc.perform(submission(post(NEW), session)).andExpect(status().isForbidden());
        mockMvc.perform(submission(post(EDIT), session)).andExpect(status().isForbidden());

        WIRE_MOCK_SERVER.verify(0, anyRequestedFor(urlPathEqualTo(COURSES_API)));
        WIRE_MOCK_SERVER.verify(0, anyRequestedFor(urlPathEqualTo(COURSE_API)));
    }

    @Test
    @DisplayName("an anonymous visitor is sent to the login page on every route")
    void anonymousGoesToLogin() throws Exception {
        for (String url : new String[] {LIST, NEW, EDIT}) {
            mockMvc.perform(get(url)).andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrlPattern("**/login"));
        }
        mockMvc.perform(post(NEW).with(csrf())).andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));
    }

    @Test
    @DisplayName("a submission without a CSRF token is refused for an administrator, and the API is not asked")
    void submissionsNeedCsrf() throws Exception {
        stubAda();
        stubCourseList(SALSA);
        Cookie session = logIn("ADMIN");

        mockMvc.perform(post(NEW).cookie(session).param("title", "Salsa")).andExpect(status().isForbidden());
        mockMvc.perform(post(EDIT).cookie(session).param("title", "Salsa")).andExpect(status().isForbidden());

        WIRE_MOCK_SERVER.verify(0, postRequestedFor(urlPathEqualTo(COURSES_API)));
        WIRE_MOCK_SERVER.verify(0, patchRequestedFor(urlPathEqualTo(COURSE_API)));
    }

    // ---- list ----

    @Test
    @DisplayName("renders the courses in an accessible table inside the management layout, status as text")
    void rendersTheList() throws Exception {
        stubCourseList(SALSA, courseJson("1b6f7c8e-1d2a-4e3b-9c4d-0a1b2c3d4e5f", "Bachata", "INACTIVE"));

        String html = mockMvc.perform(get(LIST).cookie(logIn("ADMIN"))).andReturn().getResponse()
                .getContentAsString();
        String main = mainOf(html);

        assertThat(html).contains("<title>Cursos presenciales - Menta Dance</title>");
        assertThat(main).containsPattern("<h1[^>]*>\\s*Cursos presenciales\\s*</h1>")
                .containsPattern("<a[^>]*aria-current=\"page\"[^>]*>\\s*Cursos presenciales\\s*</a>")
                .containsPattern("<a[^>]*href=\"/gestion/cursos-presenciales/nuevo\"[^>]*>\\s*Nuevo curso\\s*</a>")
                .containsPattern("<caption[^>]*>\\s*Cursos presenciales \\(<span>2</span>\\)")
                .containsPattern("<th scope=\"col\"[^>]*>Horario</th>")
                .containsPattern("<th scope=\"row\"[^>]*>Salsa</th>")
                .containsPattern("<td[^>]*>Ada Lovelace</td>")
                .containsPattern("<td[^>]*>Martes 22:00</td>")
                .containsPattern("<td[^>]*>60 min</td>")
                .containsPattern("<td[^>]*>Principiante</td>")
                .containsPattern("<td[^>]*>15</td>")
                .containsPattern("<span[^>]*class=\"badge badge-success\"[^>]*>Activo</span>")
                .containsPattern("<span[^>]*class=\"badge badge-neutral\"[^>]*>Inactivo</span>")
                .containsPattern("<a href=\"/gestion/cursos-presenciales/" + COURSE_ID + "/editar\">Editar"
                        + "<span class=\"sr-only\"> el curso Salsa</span></a>")
                .doesNotContain("role=\"alert\"");
    }

    @Test
    @DisplayName("an empty list says so in a status region and shows no table")
    void emptyList() throws Exception {
        stubCourseList();

        String main = mainOfGet(LIST, logIn("INSTRUCTOR"));

        assertThat(main).containsPattern("<[^>]*role=\"status\"[^>]*>\\s*<p>Todavía no hay cursos presenciales.</p>")
                .doesNotContain("<table");
    }

    @ParameterizedTest(name = "API status {0}")
    @ValueSource(ints = {401, 403, 429, 500, 503})
    @DisplayName("a failing API is a 503 with the page's own notice and no API detail")
    void listUnavailable(int apiStatus, CapturedOutput output) throws Exception {
        stubProblem("GET", COURSES_API, apiStatus, "SOMETHING_ELSE");

        MvcResult result = mockMvc.perform(get(LIST).cookie(logIn("ADMIN"))).andReturn();
        String main = mainOf(result.getResponse().getContentAsString());

        assertThat(result.getResponse().getStatus()).isEqualTo(503);
        assertThat(result.getResponse().getHeader("Cache-Control")).contains(NO_STORE);
        assertThat(main).containsPattern("<[^>]*role=\"alert\"[^>]*>\\s*<p>No pudimos cargar los cursos en este "
                        + "momento. Intentá nuevamente en unos minutos.</p>")
                .contains("Nuevo curso").doesNotContain("<table")
                .doesNotContain("secret-detail").doesNotContain("SOMETHING_ELSE");
        assertThat(output.getAll()).doesNotContain("secret-detail").doesNotContain(TestAccessTokens.withRole("ADMIN"));
    }

    @Test
    @DisplayName("the texts of a course are escaped")
    void listEscapes() throws Exception {
        stubCourseList(courseJson(COURSE_ID, "<script>alert(1)</script>", "ACTIVE"));

        String main = mainOfGet(LIST, logIn("ADMIN"));

        assertThat(main).doesNotContain("<script>alert(1)</script>")
                .contains("&lt;script&gt;alert(1)&lt;/script&gt;");
    }

    // ---- create as administrator ----

    @Test
    @DisplayName("the form lists the first 50 instructors in a select, with every field labelled, and CSRF")
    void adminCreateForm() throws Exception {
        stubInstructors(1, instructorItem(ADA_ID, "ada@example.com", "Ada", "Lovelace"),
                instructorItem("9a1b2c3d-1d2a-4e3b-9c4d-5b6f7c8e9f00", "sin@example.com", "null", "null")
                        .replace("\"null\"", "null"));

        MvcResult result = mockMvc.perform(get(NEW).cookie(logIn("ADMIN"))).andExpect(status().isOk()).andReturn();
        String main = mainOf(result.getResponse().getContentAsString());

        assertThat(result.getResponse().getHeader("Cache-Control")).contains(NO_STORE);
        assertThat(main).containsPattern("<h1[^>]*>\\s*Nuevo curso\\s*</h1>")
                .containsPattern("<a[^>]*aria-current=\"page\"[^>]*>\\s*Cursos presenciales\\s*</a>")
                .containsPattern("<form[^>]*action=\"/gestion/cursos-presenciales/nuevo\"")
                .containsPattern("<form[^>]*method=\"post\"")
                .containsPattern("<input[^>]*type=\"hidden\"[^>]*name=\"_csrf\"")
                .containsPattern("<input[^>]*id=\"title\"[^>]*name=\"title\"")
                .containsPattern("<textarea[^>]*id=\"description\"[^>]*name=\"description\"")
                .containsPattern("<select[^>]*id=\"professorId\"[^>]*name=\"professorId\"")
                .containsPattern("<option[^>]*value=\"" + ADA_ID + "\"[^>]*>\\s*Ada Lovelace\\s*</option>")
                .containsPattern("<option[^>]*>\\s*sin@example.com\\s*</option>")
                .containsPattern("<select[^>]*id=\"localDay\"[^>]*name=\"localDay\"")
                .containsPattern("<option[^>]*value=\"TUESDAY\"[^>]*>\\s*Martes\\s*</option>")
                .containsPattern("<input[^>]*id=\"localTime\"[^>]*type=\"time\"")
                .containsPattern("<input[^>]*id=\"durationMinutes\"[^>]*type=\"number\"")
                .containsPattern("<select[^>]*id=\"level\"[^>]*name=\"level\"")
                .containsPattern("<option[^>]*value=\"ADVANCED\"[^>]*>\\s*Avanzado\\s*</option>")
                .containsPattern("<input[^>]*id=\"capacity\"[^>]*type=\"number\"")
                .containsPattern("<button[^>]*type=\"submit\"[^>]*>\\s*Crear curso\\s*</button>")
                .doesNotContain("id=\"status\"").doesNotContain("data-error-summary")
                .doesNotContain("Se muestran los primeros 50");
        for (String field : FIELDS) {
            assertThat(main).containsPattern("<label[^>]*for=\"" + field + "\"");
        }
        WIRE_MOCK_SERVER.verify(getRequestedFor(urlPathEqualTo(USERS_API)).withQueryParam("role", equalTo("INSTRUCTOR"))
                .withQueryParam("size", equalTo("50")).withQueryParam("page", equalTo("0")));
    }

    @Test
    @DisplayName("a full page of instructors says that only the first 50 are shown")
    void fullInstructorPage() throws Exception {
        stubInstructors(51, instructorItem(ADA_ID, "ada@example.com", "Ada", "Lovelace"));

        String main = mainOfGet(NEW, logIn("ADMIN"));

        assertThat(main).contains("Se muestran los primeros 50 profesores.")
                .containsPattern("<select[^>]*id=\"professorId\"[^>]*aria-describedby=\"professorId-help\"");
    }

    @Test
    @DisplayName("without the instructors the form says so with a 503 instead of offering an empty select")
    void instructorsUnavailable() throws Exception {
        WIRE_MOCK_SERVER.stubFor(WireMock.get(urlPathEqualTo(USERS_API)).willReturn(aResponse().withStatus(500)));
        Cookie session = logIn("ADMIN");

        MvcResult get = mockMvc.perform(get(NEW).cookie(session)).andReturn();
        MvcResult submit = mockMvc.perform(submission(post(NEW), session, "professorId", ADA_ID)).andReturn();

        for (MvcResult result : new MvcResult[] {get, submit}) {
            assertThat(result.getResponse().getStatus()).isEqualTo(503);
            assertThat(mainOf(result.getResponse().getContentAsString()))
                    .containsPattern("<[^>]*role=\"alert\"[^>]*>\\s*<p>No pudimos cargar los profesores.");
        }
        WIRE_MOCK_SERVER.verify(0, postRequestedFor(urlPathEqualTo(COURSES_API)));
    }

    @Test
    @DisplayName("creates the course converting Tuesday 22:00 in Buenos Aires to Wednesday 01:00 UTC, with the "
            + "instructor taken from the list, and redirects to the list with nothing in the URL")
    void adminCreates() throws Exception {
        stubAda();
        stubCreated();

        MvcResult result = mockMvc.perform(submission(post(NEW), logIn("ADMIN"), "professorId", ADA_ID,
                        "professorName", "Tampered Name"))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl(LIST)).andReturn();

        assertThat(result.getResponse().getHeader("Cache-Control")).contains(NO_STORE);
        WIRE_MOCK_SERVER.verify(1, postRequestedFor(urlPathEqualTo(COURSES_API))
                .withHeader("Authorization", equalTo("Bearer " + TestAccessTokens.withRole("ADMIN")))
                .withRequestBody(equalToJson(("{\"title\":\"Salsa\",\"description\":\"Nivel inicial\","
                        + "\"professorId\":\"%s\",\"professorName\":\"Ada Lovelace\",\"dayOfWeek\":\"WEDNESDAY\","
                        + "\"startTime\":\"01:00:00\",\"durationMinutes\":60,\"level\":\"BEGINNER\","
                        + "\"capacity\":15}").formatted(ADA_ID))));
    }

    @Test
    @DisplayName("the list confirms the creation once, in a status region")
    void confirmationShownOnce() throws Exception {
        stubAda();
        stubCreated();
        stubCourseList(SALSA);
        Cookie session = logIn("ADMIN");
        mockMvc.perform(submission(post(NEW), session, "professorId", ADA_ID));

        String first = mainOfGet(LIST, session);
        String second = mainOfGet(LIST, session);

        assertThat(first).containsPattern("<[^>]*role=\"status\"[^>]*>\\s*<p>Creamos el curso.</p>");
        assertThat(second).doesNotContain("Creamos el curso");
    }

    @Test
    @DisplayName("without a chosen instructor the form fails next to the professor field and never relies on the "
            + "API's answer")
    void professorIsRequired() throws Exception {
        stubAda();
        stubCreated();

        String main = mainOf(mockMvc.perform(submission(post(NEW), logIn("ADMIN")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());

        assertThat(main).contains("data-error-summary").contains("Hay 1 error en el formulario")
                .containsPattern("<a href=\"#professorId\">Elegí un profesor/a de la lista.</a>")
                .containsPattern("<p[^>]*class=\"field-error\"[^>]*>Elegí un profesor/a de la lista.</p>")
                .containsPattern("<select[^>]*id=\"professorId\"[^>]*aria-invalid=\"true\"")
                .containsPattern("<select[^>]*id=\"professorId\"[^>]*aria-describedby=\"professorId-help\"")
                .containsPattern("<input[^>]*id=\"title\"[^>]*value=\"Salsa\"");
        WIRE_MOCK_SERVER.verify(0, postRequestedFor(urlPathEqualTo(COURSES_API)));
    }

    @Test
    @DisplayName("an instructor id that is not among the listed ones is refused like a missing one")
    void unlistedInstructor() throws Exception {
        stubAda();

        String main = mainOf(mockMvc.perform(submission(post(NEW), logIn("ADMIN"), "professorId",
                        "9a1b2c3d-1d2a-4e3b-9c4d-5b6f7c8e9f00")).andReturn().getResponse().getContentAsString());

        assertThat(main).contains("Elegí un profesor/a de la lista.");
        WIRE_MOCK_SERVER.verify(0, postRequestedFor(urlPathEqualTo(COURSES_API)));
    }

    @Test
    @DisplayName("an empty submission shows one Spanish error per field with the summary focused, and no API call")
    void emptySubmission() throws Exception {
        stubAda();

        MvcResult result = mockMvc.perform(form(post(NEW), logIn("ADMIN"))).andExpect(status().isOk()).andReturn();
        String main = mainOf(result.getResponse().getContentAsString());

        assertThat(main).contains("Hay 8 errores en el formulario").contains("tabindex=\"-1\"")
                .containsPattern("<a href=\"#title\">Ingresá el título</a>")
                .containsPattern("<a href=\"#description\">Ingresá la descripción</a>")
                .containsPattern("<a href=\"#professorId\">Elegí un profesor/a de la lista.</a>")
                .containsPattern("<a href=\"#localDay\">Elegí un día de la semana</a>")
                .containsPattern("<a href=\"#localTime\">Ingresá la hora de inicio con el formato HH:mm</a>")
                .containsPattern("<a href=\"#durationMinutes\">Ingresá una duración de 1 minuto o más</a>")
                .containsPattern("<a href=\"#level\">Elegí un nivel de la lista</a>")
                .containsPattern("<a href=\"#capacity\">Ingresá un cupo de 1 persona o más</a>");
        for (String field : FIELDS) {
            assertThat(main)
                    .containsPattern("<(input|select|textarea)[^>]*id=\"" + field + "\"[^>]*aria-invalid=\"true\"")
                    .contains("id=\"" + field + "-help\"");
        }
        assertThat(main).doesNotContain("autofocus");
        WIRE_MOCK_SERVER.verify(0, postRequestedFor(urlPathEqualTo(COURSES_API)));
    }

    @Test
    @DisplayName("zero duration and capacity and a bad time are local errors that keep what was typed, escaped")
    void numbersAndTimeAreChecked() throws Exception {
        stubAda();

        String main = mainOf(mockMvc.perform(submission(post(NEW), logIn("ADMIN"), "professorId", ADA_ID,
                        "durationMinutes", "0", "capacity", "-1", "localTime", "25:00", "title", "\"><b>x</b>"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());

        assertThat(main).contains("Hay 3 errores en el formulario")
                .containsPattern("<input[^>]*id=\"durationMinutes\"[^>]*value=\"0\"")
                .containsPattern("<input[^>]*id=\"localTime\"[^>]*value=\"25:00\"")
                .contains("value=\"&quot;&gt;&lt;b&gt;x&lt;/b&gt;\"").doesNotContain("<b>x</b>")
                .containsPattern("<option[^>]*value=\"" + ADA_ID + "\"[^>]*selected[^>]*>")
                .containsPattern("<option[^>]*value=\"TUESDAY\"[^>]*selected[^>]*>");
    }

    @ParameterizedTest(name = "{0} (API {1}) is {2}")
    @CsvSource({
        "PROFESSOR_REQUIRED, 400, professorId",
        "PROFESSOR_MISMATCH, 400, professorId",
        "INVALID_REQUEST, 400, form",
        "SESSION_ALREADY_EXISTS, 409, form",
        "SOMETHING_ELSE, 500, form"})
    @DisplayName("each API refusal of a creation is worded in Spanish at its field or as a general error")
    void creationRefusals(String code, int apiStatus, String where, CapturedOutput output) throws Exception {
        stubAda();
        stubProblem("POST", COURSES_API, apiStatus, code);

        MvcResult result = mockMvc.perform(submission(post(NEW), logIn("ADMIN"), "professorId", ADA_ID)).andReturn();
        String main = mainOf(result.getResponse().getContentAsString());

        if ("professorId".equals(where)) {
            assertThat(result.getResponse().getStatus()).isEqualTo(400);
            assertThat(main).containsPattern("<a href=\"#professorId\">[^<]+</a>")
                    .containsPattern("<select[^>]*id=\"professorId\"[^>]*aria-invalid=\"true\"");
        } else {
            boolean invalid = "INVALID_REQUEST".equals(code);
            assertThat(result.getResponse().getStatus()).isEqualTo(invalid ? 400 : 503);
            assertThat(main).containsPattern("<[^>]*role=\"alert\"[^>]*>\\s*<p>"
                    + (invalid ? "Revisá los datos e intentá de nuevo."
                            : "No pudimos guardar el curso. Probá de nuevo en unos minutos.") + "</p>")
                    .doesNotContain("data-error-summary");
        }
        assertThat(main).doesNotContain("secret-detail").doesNotContain(code)
                .containsPattern("<input[^>]*id=\"title\"[^>]*value=\"Salsa\"");
        assertThat(result.getResponse().getHeader("Cache-Control")).contains(NO_STORE);
        assertThat(output.getAll()).doesNotContain("secret-detail").doesNotContain(TestAccessTokens.withRole("ADMIN"));
    }

    @Test
    @DisplayName("an outage while saving is a 503 general error with the data kept")
    void creationOutage() throws Exception {
        stubAda();
        WIRE_MOCK_SERVER.stubFor(WireMock.post(urlPathEqualTo(COURSES_API))
                .willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)));

        MvcResult result = mockMvc.perform(submission(post(NEW), logIn("ADMIN"), "professorId", ADA_ID)).andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(503);
        assertThat(mainOf(result.getResponse().getContentAsString()))
                .contains("No pudimos guardar el curso. Probá de nuevo en unos minutos.")
                .containsPattern("<input[^>]*id=\"title\"[^>]*value=\"Salsa\"");
    }

    // ---- create as instructor ----

    @Test
    @DisplayName("an instructor gets no picker: the course goes to their own id (from the token) and the name the "
            + "Auth API knows for their account")
    void instructorCreates() throws Exception {
        stubOwnAccount("\" Ada \"", "\"Lovelace\"");
        stubCreated();
        Cookie session = logInWith(instructorToken());

        String formPage = mainOfGet(NEW, session);
        mockMvc.perform(submission(post(NEW), session, "professorId", "9a1b2c3d-1d2a-4e3b-9c4d-5b6f7c8e9f00"))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl(LIST));

        assertThat(formPage).doesNotContain("name=\"professorId\"").contains("Ada Lovelace")
                .containsPattern("<button[^>]*type=\"submit\"[^>]*>\\s*Crear curso\\s*</button>");
        WIRE_MOCK_SERVER.verify(0, getRequestedFor(urlPathEqualTo(USERS_API)));
        WIRE_MOCK_SERVER.verify(getRequestedFor(urlPathEqualTo(ME_API))
                .withHeader("Authorization", equalTo("Bearer " + instructorToken())));
        WIRE_MOCK_SERVER.verify(1, postRequestedFor(urlPathEqualTo(COURSES_API))
                .withHeader("Authorization", equalTo("Bearer " + instructorToken()))
                .withRequestBody(equalToJson("{\"professorId\":\"" + ADA_ID + "\","
                        + "\"professorName\":\"Ada Lovelace\",\"dayOfWeek\":\"WEDNESDAY\","
                        + "\"startTime\":\"01:00:00\"}", true, true)));
    }

    @ParameterizedTest(name = "current-user endpoint: {0}")
    @ValueSource(strings = {"401", "500", "no-profile", "connection-reset"})
    @DisplayName("when the Auth API cannot give the instructor's name, the form says so with a 503 and nothing is "
            + "sent")
    void instructorWithoutName(String failure, CapturedOutput output) throws Exception {
        switch (failure) {
            case "no-profile" -> stubOwnAccount("null", "null");
            case "connection-reset" -> WIRE_MOCK_SERVER.stubFor(WireMock.get(urlPathEqualTo(ME_API))
                    .willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)));
            default -> stubProblem("GET", ME_API, Integer.parseInt(failure), "SOMETHING_ELSE");
        }
        stubCreated();
        Cookie session = logInWith(instructorToken());

        MvcResult get = mockMvc.perform(get(NEW).cookie(session)).andReturn();
        MvcResult submit = mockMvc.perform(submission(post(NEW), session)).andReturn();

        for (MvcResult result : new MvcResult[] {get, submit}) {
            assertThat(result.getResponse().getStatus()).isEqualTo(503);
            assertThat(mainOf(result.getResponse().getContentAsString()))
                    .containsPattern("<[^>]*role=\"alert\"[^>]*>\\s*<p>Por ahora no podemos crear cursos desde tu "
                            + "cuenta en la web. Pedile a un administrador que cree el curso a tu nombre.</p>")
                    .doesNotContain("secret-detail");
        }
        WIRE_MOCK_SERVER.verify(0, postRequestedFor(urlPathEqualTo(COURSES_API)));
        assertThat(output.getAll()).doesNotContain("secret-detail").doesNotContain(instructorToken());
    }

    @Test
    @DisplayName("an API refusal on the instructor's own course shows at the professor block")
    void instructorMismatch() throws Exception {
        stubOwnAccount("\"Ada\"", "\"Lovelace\"");
        stubProblem("POST", COURSES_API, 400, "PROFESSOR_MISMATCH");

        String main = mainOf(mockMvc.perform(submission(post(NEW), logInWith(instructorToken()))).andReturn()
                .getResponse().getContentAsString());

        assertThat(main).containsPattern("<a href=\"#professorId\">Solo podés crear cursos a tu nombre.</a>")
                .containsPattern("<p[^>]*class=\"field-error\"[^>]*>Solo podés crear cursos a tu nombre.</p>");
    }

    // ---- edit ----

    @Test
    @DisplayName("the edit form is prefilled with the local day and time, shows the professor read-only and offers "
            + "the status")
    void editForm() throws Exception {
        stubCourseList(courseJson("1b6f7c8e-1d2a-4e3b-9c4d-0a1b2c3d4e5f", "Otro", "ACTIVE"), SALSA);

        MvcResult result = mockMvc.perform(get(EDIT).cookie(logIn("INSTRUCTOR"))).andExpect(status().isOk())
                .andReturn();
        String main = mainOf(result.getResponse().getContentAsString());

        assertThat(result.getResponse().getHeader("Cache-Control")).contains(NO_STORE);
        assertThat(main).containsPattern("<h1[^>]*>\\s*Editar curso\\s*</h1>")
                .containsPattern("<form[^>]*action=\"" + EDIT + "\"")
                .containsPattern("<input[^>]*type=\"hidden\"[^>]*name=\"_csrf\"")
                .containsPattern("<input[^>]*id=\"title\"[^>]*value=\"Salsa\"")
                .containsPattern("<textarea[^>]*id=\"description\"[^>]*>Nivel inicial</textarea>")
                .containsPattern("<option[^>]*value=\"TUESDAY\"[^>]*selected[^>]*>\\s*Martes\\s*</option>")
                .containsPattern("<input[^>]*id=\"localTime\"[^>]*value=\"22:00\"")
                .containsPattern("<input[^>]*id=\"durationMinutes\"[^>]*value=\"60\"")
                .containsPattern("<option[^>]*value=\"BEGINNER\"[^>]*selected[^>]*>\\s*Principiante\\s*</option>")
                .containsPattern("<input[^>]*id=\"capacity\"[^>]*value=\"15\"")
                .containsPattern("<option[^>]*value=\"ACTIVE\"[^>]*selected[^>]*>\\s*Activo\\s*</option>")
                .containsPattern("<option[^>]*value=\"INACTIVE\"[^>]*>\\s*Inactivo\\s*</option>")
                .containsPattern("<button[^>]*type=\"submit\"[^>]*>\\s*Guardar cambios\\s*</button>")
                .contains("No se puede cambiar el profesor de un curso.")
                .doesNotContain("id=\"professorId\"").doesNotContain("name=\"professorId\"");
        assertThat(main).containsPattern("<p>Ada Lovelace</p>");
    }

    @Test
    @DisplayName("the texts of the course are escaped in the edit form")
    void editEscapes() throws Exception {
        stubCourseList(courseJson(COURSE_ID, "\\\"><script>alert(1)</script>", "ACTIVE"));

        String main = mainOfGet(EDIT, logIn("ADMIN"));

        assertThat(main).doesNotContain("<script>alert(1)</script>").contains("&lt;script&gt;alert(1)&lt;/script&gt;");
    }

    @Test
    @DisplayName("saves the changes sending day and time together in UTC, and redirects with a confirmation")
    void editSaves() throws Exception {
        stubCourseList(SALSA);
        stubUpdated();
        Cookie session = logIn("INSTRUCTOR");

        mockMvc.perform(submission(post(EDIT), session, "localDay", "FRIDAY", "localTime", "23:30",
                        "capacity", "20", "status", "INACTIVE", "professorId", "ignored"))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl(LIST));
        stubCourseList(SALSA);
        String list = mainOfGet(LIST, session);

        WIRE_MOCK_SERVER.verify(1, patchRequestedFor(urlPathEqualTo(COURSE_API))
                .withHeader("Authorization", equalTo("Bearer " + TestAccessTokens.withRole("INSTRUCTOR")))
                .withRequestBody(equalToJson("{\"title\":\"Salsa\",\"description\":\"Nivel inicial\","
                        + "\"dayOfWeek\":\"SATURDAY\",\"startTime\":\"02:30:00\",\"durationMinutes\":60,"
                        + "\"level\":\"BEGINNER\",\"capacity\":20,\"status\":\"INACTIVE\"}")));
        assertThat(list).containsPattern("<[^>]*role=\"status\"[^>]*>\\s*<p>Guardamos los cambios del curso.</p>");
    }

    @Test
    @DisplayName("a course with assigned sessions that cannot be deactivated fails at the status field, keeping the "
            + "other changes on screen")
    void deactivationRefused(CapturedOutput output) throws Exception {
        stubCourseList(SALSA);
        stubProblem("PATCH", COURSE_API, 409, "COURSE_HAS_ACTIVE_ASSIGNMENTS");

        MvcResult result = mockMvc.perform(submission(post(EDIT), logIn("ADMIN"), "status", "INACTIVE",
                "capacity", "20")).andReturn();
        String main = mainOf(result.getResponse().getContentAsString());

        assertThat(result.getResponse().getStatus()).isEqualTo(409);
        assertThat(main).contains("Hay 1 error en el formulario")
                .containsPattern("<a href=\"#status\">No se puede desactivar: tiene alumnos asignados a sesiones "
                        + "futuras.</a>")
                .containsPattern("<select[^>]*id=\"status\"[^>]*aria-invalid=\"true\"")
                .containsPattern("<select[^>]*id=\"status\"[^>]*aria-describedby=\"status-help\"")
                .containsPattern("<option[^>]*value=\"INACTIVE\"[^>]*selected[^>]*>")
                .containsPattern("<input[^>]*id=\"capacity\"[^>]*value=\"20\"")
                .doesNotContain("secret-detail").doesNotContain("COURSE_HAS_ACTIVE_ASSIGNMENTS");
        assertThat(output.getAll()).doesNotContain("secret-detail");
    }

    @ParameterizedTest(name = "{0} (API {1}) is page {2}")
    @CsvSource({"COURSE_NOT_OWNED, 403, 403, No podés gestionar este curso.",
        "COURSE_NOT_FOUND, 404, 404, No encontramos ese curso."})
    @DisplayName("a course that is not the caller's or is gone is a notice page with its own status")
    void editRefusedAsNoticePage(String code, int apiStatus, int pageStatus, String message) throws Exception {
        stubCourseList(SALSA);
        stubProblem("PATCH", COURSE_API, apiStatus, code);

        MvcResult result = mockMvc.perform(submission(post(EDIT), logIn("INSTRUCTOR"))).andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(pageStatus);
        assertThat(mainOf(result.getResponse().getContentAsString())).contains(message)
                .contains("Volver a los cursos").doesNotContain("secret-detail").doesNotContain(code);
        assertThat(result.getResponse().getHeader("Cache-Control")).contains(NO_STORE);
    }

    @Test
    @DisplayName("an invalid request and an unexpected failure on edit are general errors with the data kept")
    void editGeneralErrors() throws Exception {
        stubCourseList(SALSA);
        Cookie session = logIn("ADMIN");

        stubProblem("PATCH", COURSE_API, 400, "INVALID_REQUEST");
        MvcResult invalid = mockMvc.perform(submission(post(EDIT), session, "status", "ACTIVE")).andReturn();
        stubProblem("PATCH", COURSE_API, 500, "SOMETHING_ELSE");
        MvcResult failed = mockMvc.perform(submission(post(EDIT), session, "status", "ACTIVE")).andReturn();

        assertThat(invalid.getResponse().getStatus()).isEqualTo(400);
        assertThat(mainOf(invalid.getResponse().getContentAsString())).contains("Revisá los datos e intentá de nuevo.");
        assertThat(failed.getResponse().getStatus()).isEqualTo(503);
        assertThat(mainOf(failed.getResponse().getContentAsString()))
                .contains("No pudimos guardar el curso. Probá de nuevo en unos minutos.")
                .containsPattern("<input[^>]*id=\"title\"[^>]*value=\"Salsa\"")
                .doesNotContain("SOMETHING_ELSE");
    }

    @Test
    @DisplayName("a local error on edit shows the form again and sends nothing")
    void editLocalError() throws Exception {
        stubCourseList(SALSA);

        String main = mainOf(mockMvc.perform(submission(post(EDIT), logIn("ADMIN"), "capacity", "0", "status", "X"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());

        assertThat(main).contains("Hay 2 errores en el formulario")
                .containsPattern("<a href=\"#capacity\">Ingresá un cupo de 1 persona o más</a>")
                .containsPattern("<a href=\"#status\">Elegí un estado de la lista</a>");
        WIRE_MOCK_SERVER.verify(0, patchRequestedFor(urlPathEqualTo(COURSE_API)));
    }

    @ParameterizedTest(name = "id \"{0}\"")
    @ValueSource(strings = {"not-a-uuid", "1234", "5b6f7c8e-1d2a-4e3b-9c4d-0a1b2c3d4e5f-x", "script"})
    @DisplayName("an id that is not a UUID is a 404 notice on the form and on the submission, without calling the API")
    void malformedId(String id) throws Exception {
        Cookie session = logIn("ADMIN");
        String url = LIST + "/" + id + "/editar";

        MvcResult get = mockMvc.perform(get(url).cookie(session)).andReturn();
        MvcResult submit = mockMvc.perform(submission(post(url), session)).andReturn();

        for (MvcResult result : new MvcResult[] {get, submit}) {
            assertThat(result.getResponse().getStatus()).isEqualTo(404);
            assertThat(mainOf(result.getResponse().getContentAsString())).contains("No encontramos ese curso.")
                    .doesNotContain("<form");
        }
        WIRE_MOCK_SERVER.verify(0, anyRequestedFor(urlPathEqualTo(COURSES_API)));
        WIRE_MOCK_SERVER.verify(0, anyRequestedFor(urlPathEqualTo(COURSE_API)));
    }

    @Test
    @DisplayName("a UUID the API does not list for the caller is a 404 notice, and a failing API a 503 notice")
    void unknownCourseAndOutage() throws Exception {
        Cookie session = logIn("INSTRUCTOR");
        stubCourseList(courseJson("1b6f7c8e-1d2a-4e3b-9c4d-0a1b2c3d4e5f", "Otro", "ACTIVE"));
        MvcResult unknown = mockMvc.perform(get(EDIT).cookie(session)).andReturn();
        stubProblem("GET", COURSES_API, 500, "SOMETHING_ELSE");
        MvcResult outage = mockMvc.perform(get(EDIT).cookie(session)).andReturn();

        assertThat(unknown.getResponse().getStatus()).isEqualTo(404);
        assertThat(outage.getResponse().getStatus()).isEqualTo(503);
        assertThat(mainOf(outage.getResponse().getContentAsString()))
                .contains("No pudimos cargar el curso en este momento.").doesNotContain("SOMETHING_ELSE");
    }
}
