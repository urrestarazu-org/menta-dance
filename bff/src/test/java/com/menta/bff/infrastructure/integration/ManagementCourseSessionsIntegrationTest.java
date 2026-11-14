package com.menta.bff.infrastructure.integration;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.anyRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.matching;
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
import jakarta.servlet.http.Cookie;
import java.time.LocalDate;
import java.time.ZoneId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * The sessions screens of a physical course (#368): who may open them, what the list shows and asks the Physical
 * API for (the local window converted to UTC), what the single-session form sends (the local date and time
 * converted to UTC) and how each answer and each API error code comes back, without ever showing the API's body.
 * Each case logs in for real against the stubbed Auth API and reuses the session cookie.
 */
@ExtendWith(OutputCaptureExtension.class)
@DisplayName("Management course sessions Integration Tests")
class ManagementCourseSessionsIntegrationTest extends BaseIntegrationTest {

    private static final ZoneId ACADEMY = ZoneId.of("America/Argentina/Buenos_Aires");
    private static final String COURSE_ID = "5b6f7c8e-1d2a-4e3b-9c4d-0a1b2c3d4e5f";
    private static final String SESSION_A = "a1b2c3d4-1d2a-4e3b-9c4d-0a1b2c3d4e01";
    private static final String SESSION_B = "a1b2c3d4-1d2a-4e3b-9c4d-0a1b2c3d4e02";
    private static final String ADA_ID = "0a1b2c3d-1d2a-4e3b-9c4d-5b6f7c8e9f00";
    private static final String BASE = "/gestion/cursos-presenciales";
    private static final String SESSIONS = BASE + "/" + COURSE_ID + "/sesiones";
    private static final String COURSES_API = "/api/v1/admin/physical/courses";
    private static final String SESSIONS_API = COURSES_API + "/" + COURSE_ID + "/sessions";
    private static final String NO_STORE = "no-store";

    /** Wednesday 01:00 UTC is Tuesday 22:00 in Buenos Aires. */
    private static final String SALSA = ("{\"courseId\":\"%s\",\"title\":\"Salsa\",\"description\":\"Nivel inicial\","
            + "\"professorId\":\"%s\",\"professorName\":\"Ada Lovelace\",\"dayOfWeek\":\"WEDNESDAY\","
            + "\"startTime\":\"01:00:00\",\"durationMinutes\":60,\"level\":\"BEGINNER\",\"capacity\":15,"
            + "\"status\":\"ACTIVE\"}").formatted(COURSE_ID, ADA_ID);

    private static String sessionJson(String id, String scheduledAt, int capacity, int assigned, int holds,
            String status) {
        return ("{\"sessionId\":\"%s\",\"courseId\":\"%s\",\"scheduledAt\":\"%s\",\"capacity\":%d,"
                + "\"assignedSpots\":%d,\"activeCapacityHolds\":%d,\"availableSpots\":%d,\"status\":\"%s\","
                + "\"notes\":null}").formatted(id, COURSE_ID, scheduledAt, capacity, assigned, holds,
                capacity - assigned - holds, status);
    }

    /** Tuesday 2026-11-17 22:00 local, 3 of 10 spots taken. */
    private static final String FIRST = sessionJson(SESSION_A, "2026-11-18T01:00:00Z", 10, 2, 1, "SCHEDULED");
    /** Tuesday 2026-11-24 22:00 local, cancelled. */
    private static final String SECOND = sessionJson(SESSION_B, "2026-11-25T01:00:00Z", 10, 0, 0, "CANCELLED");

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

    private static void stubSessions(String... sessions) {
        WIRE_MOCK_SERVER.stubFor(WireMock.get(urlPathEqualTo(SESSIONS_API)).willReturn(aResponse()
                .withStatus(200).withHeader("Content-Type", "application/json")
                .withBody("{\"sessions\":[%s]}".formatted(String.join(",", sessions)))));
    }

    private static void stubCourse() {
        WIRE_MOCK_SERVER.stubFor(WireMock.get(urlPathEqualTo(COURSES_API)).willReturn(aResponse()
                .withStatus(200).withHeader("Content-Type", "application/json")
                .withBody("{\"courses\":[%s]}".formatted(SALSA))));
    }

    private static void stubProblem(String method, String path, int status, String code) {
        WIRE_MOCK_SERVER.stubFor(WireMock.request(method, urlPathEqualTo(path)).willReturn(aResponse()
                .withStatus(status).withHeader("Content-Type", "application/problem+json")
                .withBody(("{\"type\":\"https://menta.dance/problems/x\",\"status\":%d,\"detail\":\"secret-detail\","
                        + "\"code\":\"%s\"}").formatted(status, code))));
    }

    private static void stubConflict(String path, String... instants) {
        WIRE_MOCK_SERVER.stubFor(WireMock.post(urlPathEqualTo(path)).willReturn(aResponse()
                .withStatus(409).withHeader("Content-Type", "application/problem+json")
                .withBody(("{\"type\":\"https://menta.dance/problems/x\",\"status\":409,\"detail\":\"secret-detail\","
                        + "\"code\":\"SESSION_ALREADY_EXISTS\",\"conflictingDates\":[%s]}")
                        .formatted("\"" + String.join("\",\"", instants) + "\""))));
    }

    private static void stubCreated() {
        WIRE_MOCK_SERVER.stubFor(WireMock.post(urlPathEqualTo(SESSIONS_API)).willReturn(aResponse()
                .withStatus(201).withHeader("Content-Type", "application/json").withBody(FIRST)));
    }

    /** Both reads that draw the page: the sessions and the course. */
    private static void stubPage(String... sessions) {
        stubSessions(sessions);
        stubCourse();
    }

    private static MockHttpServletRequestBuilder submit(
            MockHttpServletRequestBuilder request, Cookie session, String... namesAndValues) {
        request.cookie(session).with(csrf());
        for (int i = 0; i < namesAndValues.length; i += 2) {
            request.param(namesAndValues[i], namesAndValues[i + 1]);
        }
        return request;
    }

    private String mainOfGet(String url, Cookie session) throws Exception {
        return mainOf(mockMvc.perform(get(url).cookie(session)).andReturn().getResponse().getContentAsString());
    }

    // ---- access ----

    @ParameterizedTest(name = "{0} opens the sessions")
    @ValueSource(strings = {"ADMIN", "INSTRUCTOR"})
    @DisplayName("an administrator and an instructor open the sessions page, which is never cached")
    void adminAndInstructorOpenThePage(String role) throws Exception {
        stubPage(FIRST);

        MvcResult result = mockMvc.perform(get(SESSIONS).cookie(logIn(role))).andExpect(status().isOk()).andReturn();

        assertThat(result.getResponse().getHeader("Cache-Control")).contains(NO_STORE);
        WIRE_MOCK_SERVER.verify(getRequestedFor(urlPathEqualTo(SESSIONS_API))
                .withHeader("Authorization", equalTo("Bearer " + TestAccessTokens.withRole(role))));
    }

    @ParameterizedTest(name = "{0} is refused")
    @ValueSource(strings = {"RECEPTIONIST", "STUDENT"})
    @DisplayName("every other role gets a 403 on every route, and the API is never asked")
    void otherRolesAreRefused(String role) throws Exception {
        Cookie session = logIn(role);

        mockMvc.perform(get(SESSIONS).cookie(session)).andExpect(status().isForbidden());
        mockMvc.perform(submit(post(SESSIONS), session, "localDate", "2026-11-17", "localTime", "22:00"))
                .andExpect(status().isForbidden());

        WIRE_MOCK_SERVER.verify(0, anyRequestedFor(urlPathEqualTo(SESSIONS_API)));
        WIRE_MOCK_SERVER.verify(0, anyRequestedFor(urlPathEqualTo(COURSES_API)));
    }

    @Test
    @DisplayName("an anonymous visitor is sent to the login page")
    void anonymousGoesToLogin() throws Exception {
        mockMvc.perform(get(SESSIONS)).andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));
        mockMvc.perform(post(SESSIONS).with(csrf())).andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));
    }

    @Test
    @DisplayName("a submission without a CSRF token is refused, and the API is not asked")
    void submissionsNeedCsrf() throws Exception {
        stubPage(FIRST);
        stubCreated();
        Cookie session = logIn("ADMIN");

        mockMvc.perform(post(SESSIONS).cookie(session).param("localDate", "2026-11-17").param("localTime", "22:00"))
                .andExpect(status().isForbidden());

        WIRE_MOCK_SERVER.verify(0, postRequestedFor(urlPathEqualTo(SESSIONS_API)));
    }

    // ---- list ----

    @Test
    @DisplayName("renders the sessions in an accessible table, earliest first, with the spots and the status as text")
    void rendersTheList() throws Exception {
        stubPage(SECOND, FIRST);

        String html = mockMvc.perform(get(SESSIONS).cookie(logIn("ADMIN"))).andReturn().getResponse()
                .getContentAsString();
        String main = mainOf(html);

        assertThat(html).contains("<title>Sesiones del curso - Menta Dance</title>");
        assertThat(main).containsPattern("<h1[^>]*>\\s*Sesiones de <span>Salsa</span>\\s*</h1>")
                .containsPattern("<a[^>]*aria-current=\"page\"[^>]*>\\s*Cursos presenciales\\s*</a>")
                .contains("Martes 22:00").contains("Ada Lovelace")
                .containsPattern("<a href=\"/gestion/cursos-presenciales\">Volver a los cursos</a>")
                .containsPattern("<caption[^>]*>\\s*Sesiones <span>del \\d\\d/\\d\\d/\\d{4} al \\d\\d/\\d\\d/\\d{4}"
                        + "</span> \\(<span>2</span>\\)")
                .containsPattern("<th scope=\"col\"[^>]*>Fecha y hora</th>")
                .containsPattern("<th scope=\"col\"[^>]*>Ocupados / cupo</th>")
                .containsPattern("<th scope=\"col\"[^>]*>Disponibles</th>")
                .containsPattern("<th scope=\"col\"[^>]*>Estado</th>")
                .containsPattern("<th scope=\"row\"[^>]*>\\s*<time datetime=\"2026-11-18T01:00:00Z\">"
                        + "martes 17 de noviembre a las 22:00</time>")
                .containsPattern("<td[^>]*>3 / 10</td>")
                .containsPattern("<td[^>]*>7</td>")
                .containsPattern("<span[^>]*class=\"badge badge-success\"[^>]*>Programada</span>")
                .containsPattern("<span[^>]*class=\"badge badge-neutral\"[^>]*>Cancelada</span>")
                .doesNotContain("role=\"alert\"");
        assertThat(main.indexOf("17 de noviembre")).isPositive().isLessThan(main.indexOf("24 de noviembre"));
    }

    @Test
    @DisplayName("by default it asks for today (in the academy's zone) to 90 days ahead, as UTC instants")
    void defaultWindow() throws Exception {
        stubPage();
        Cookie session = logIn("ADMIN");

        LocalDate before = LocalDate.now(ACADEMY);
        mockMvc.perform(get(SESSIONS).cookie(session)).andExpect(status().isOk());
        LocalDate after = LocalDate.now(ACADEMY);

        WIRE_MOCK_SERVER.verify(getRequestedFor(urlPathEqualTo(SESSIONS_API))
                .withQueryParam("from", matching(startOf(before) + "|" + startOf(after)))
                .withQueryParam("to", matching(startOf(before.plusDays(91)) + "|" + startOf(after.plusDays(91)))));
    }

    /** The UTC instant at which a day starts in Buenos Aires (UTC-3), as the adapter sends it. */
    private static String startOf(LocalDate day) {
        return day.atStartOfDay(ACADEMY).toInstant().toString();
    }

    @Test
    @DisplayName("desde and hasta choose the days, converted to UTC, and fill the filter")
    void validWindow() throws Exception {
        stubPage(FIRST);

        String main = mainOfGet(SESSIONS + "?desde=2026-11-01&hasta=2026-11-30", logIn("ADMIN"));

        WIRE_MOCK_SERVER.verify(getRequestedFor(urlPathEqualTo(SESSIONS_API))
                .withQueryParam("from", equalTo("2026-11-01T03:00:00Z"))
                .withQueryParam("to", equalTo("2026-12-01T03:00:00Z")));
        assertThat(main).containsPattern("<input[^>]*id=\"desde\"[^>]*type=\"date\"[^>]*value=\"2026-11-01\"")
                .containsPattern("<input[^>]*id=\"hasta\"[^>]*type=\"date\"[^>]*value=\"2026-11-30\"")
                .containsPattern("<label[^>]*for=\"desde\"[^>]*>Desde</label>")
                .containsPattern("<caption[^>]*>\\s*Sesiones <span>del 01/11/2026 al 30/11/2026</span>")
                .doesNotContain("No pudimos usar esas fechas");
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"desde=basura", "hasta=2026-13-45", "desde=2026-12-31&hasta=2026-12-01",
        "desde=2020-01-01&hasta=2026-01-01", "desde=%3Cscript%3E"})
    @DisplayName("a window that cannot be used falls back to the default one, with a hint and never an error")
    void invalidWindow(String query) throws Exception {
        stubPage(FIRST);
        Cookie session = logIn("ADMIN");

        MvcResult result = mockMvc.perform(get(SESSIONS + "?" + query).cookie(session)).andReturn();
        String main = mainOf(result.getResponse().getContentAsString());

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(main).containsPattern("<[^>]*role=\"status\"[^>]*>\\s*<p>No pudimos usar esas fechas. "
                        + "Mostramos los próximos 90 días.</p>")
                .containsPattern("<caption[^>]*>\\s*Sesiones <span>del \\d\\d/\\d\\d/\\d{4} al")
                .doesNotContain("<script>");
        LocalDate today = LocalDate.now(ACADEMY);
        WIRE_MOCK_SERVER.verify(getRequestedFor(urlPathEqualTo(SESSIONS_API))
                .withQueryParam("from", matching(startOf(today) + "|" + startOf(today.minusDays(1)) + "|"
                        + startOf(today.plusDays(1)))));
    }

    @Test
    @DisplayName("a course without sessions in the window says so, and still offers the creation form")
    void emptyList() throws Exception {
        stubPage();

        String main = mainOfGet(SESSIONS, logIn("INSTRUCTOR"));

        assertThat(main).containsPattern("<[^>]*role=\"status\"[^>]*>\\s*<p>Este curso no tiene sesiones en "
                        + "estas fechas.</p>")
                .doesNotContain("<table").contains("Crear una sesión");
    }

    @ParameterizedTest(name = "API status {0}")
    @ValueSource(ints = {401, 429, 500, 503})
    @DisplayName("a failing API is a 503 with the page's own notice and no API detail")
    void listUnavailable(int apiStatus, CapturedOutput output) throws Exception {
        stubProblem("GET", SESSIONS_API, apiStatus, "SOMETHING_ELSE");
        stubCourse();

        MvcResult result = mockMvc.perform(get(SESSIONS).cookie(logIn("ADMIN"))).andReturn();
        String main = mainOf(result.getResponse().getContentAsString());

        assertThat(result.getResponse().getStatus()).isEqualTo(503);
        assertThat(result.getResponse().getHeader("Cache-Control")).contains(NO_STORE);
        assertThat(main).contains("No pudimos cargar las sesiones en este momento. Intentá nuevamente en unos minutos.")
                .doesNotContain("<table").doesNotContain("secret-detail").doesNotContain("SOMETHING_ELSE");
        assertThat(output.getAll()).doesNotContain("secret-detail").doesNotContain(TestAccessTokens.withRole("ADMIN"));
    }

    @Test
    @DisplayName("an instructor on a course that is not theirs gets the 403 notice")
    void courseNotOwned() throws Exception {
        stubProblem("GET", SESSIONS_API, 403, "COURSE_NOT_OWNED");
        stubCourse();

        MvcResult result = mockMvc.perform(get(SESSIONS).cookie(logIn("INSTRUCTOR"))).andReturn();
        String main = mainOf(result.getResponse().getContentAsString());

        assertThat(result.getResponse().getStatus()).isEqualTo(403);
        assertThat(main).contains("No podés gestionar este curso.").contains("Volver a los cursos")
                .doesNotContain("secret-detail").doesNotContain("<table");
    }

    @Test
    @DisplayName("a course that does not exist gets the 404 notice")
    void courseNotFound() throws Exception {
        stubProblem("GET", SESSIONS_API, 404, "COURSE_NOT_FOUND");
        stubCourse();

        MvcResult result = mockMvc.perform(get(SESSIONS).cookie(logIn("ADMIN"))).andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(404);
        assertThat(mainOf(result.getResponse().getContentAsString())).contains("No encontramos ese curso.");
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"no-es-un-uuid", "123", "5b6f7c8e-1d2a-4e3b-9c4d"})
    @DisplayName("an id that is not a UUID is a 404 and the API is never asked")
    void malformedCourseId(String id) throws Exception {
        Cookie session = logIn("ADMIN");

        MvcResult result = mockMvc.perform(get(BASE + "/" + id + "/sesiones").cookie(session)).andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(404);
        assertThat(mainOf(result.getResponse().getContentAsString())).contains("No encontramos ese curso.");
        WIRE_MOCK_SERVER.verify(0, anyRequestedFor(urlPathEqualTo(COURSES_API)));
        WIRE_MOCK_SERVER.verify(0, anyRequestedFor(urlPathEqualTo(COURSES_API + "/" + id + "/sessions")));
    }

    @Test
    @DisplayName("the texts of the course and the notes are escaped")
    void listEscapes() throws Exception {
        stubSessions(FIRST);
        WIRE_MOCK_SERVER.stubFor(WireMock.get(urlPathEqualTo(COURSES_API)).willReturn(aResponse()
                .withStatus(200).withHeader("Content-Type", "application/json")
                .withBody("{\"courses\":[%s]}".formatted(SALSA.replace("Salsa", "<script>alert(1)</script>")))));

        String main = mainOfGet(SESSIONS, logIn("ADMIN"));

        assertThat(main).doesNotContain("<script>alert(1)</script>")
                .contains("&lt;script&gt;alert(1)&lt;/script&gt;");
    }

    @Test
    @DisplayName("the course list links to the sessions of each course")
    void courseListLinksToSessions() throws Exception {
        stubCourse();

        String main = mainOfGet(BASE, logIn("ADMIN"));

        assertThat(main).contains("<a href=\"/gestion/cursos-presenciales/" + COURSE_ID + "/sesiones\">Sesiones"
                + "<span class=\"sr-only\"> del curso Salsa</span></a>");
    }

    // ---- create one ----

    @Test
    @DisplayName("the creation form has every field labelled, the course's usual time and CSRF, next to the list")
    void creationForm() throws Exception {
        stubPage(FIRST);

        String main = mainOfGet(SESSIONS, logIn("ADMIN"));

        assertThat(main).containsPattern("<h2[^>]*>Sesiones existentes</h2>")
                .containsPattern("<h2[^>]*>Crear una sesión</h2>")
                .containsPattern("<form[^>]*action=\"" + SESSIONS + "\"[^>]*method=\"post\"")
                .containsPattern("<input[^>]*type=\"hidden\"[^>]*name=\"_csrf\"")
                .containsPattern("<input[^>]*id=\"localDate\"[^>]*type=\"date\"[^>]*name=\"localDate\"")
                .containsPattern("<input[^>]*id=\"localTime\"[^>]*type=\"time\"[^>]*value=\"22:00\"")
                .containsPattern("<input[^>]*id=\"capacity\"[^>]*type=\"number\"")
                .containsPattern("<input[^>]*id=\"notes\"[^>]*type=\"text\"")
                .containsPattern("<button[^>]*type=\"submit\"[^>]*>\\s*Crear sesión\\s*</button>")
                .doesNotContain("data-error-summary");
        for (String field : new String[] {"localDate", "localTime", "capacity", "notes"}) {
            assertThat(main).containsPattern("<label[^>]*for=\"" + field + "\"");
        }
        assertThat(main.indexOf("Sesiones existentes")).isLessThan(main.indexOf("Crear una sesión"));
    }

    @Test
    @DisplayName("creates the session converting Tuesday 22:00 in Buenos Aires to Wednesday 01:00 UTC, and "
            + "redirects to the list with nothing but the course in the URL")
    void createsConvertingToUtc() throws Exception {
        stubPage(FIRST);
        stubCreated();

        MvcResult result = mockMvc.perform(submit(post(SESSIONS), logIn("ADMIN"),
                        "localDate", "2026-11-17", "localTime", "22:00"))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl(SESSIONS)).andReturn();

        assertThat(result.getResponse().getHeader("Cache-Control")).contains(NO_STORE);
        WIRE_MOCK_SERVER.verify(1, postRequestedFor(urlPathEqualTo(SESSIONS_API))
                .withHeader("Authorization", equalTo("Bearer " + TestAccessTokens.withRole("ADMIN")))
                .withRequestBody(equalToJson("{\"date\":\"2026-11-18\",\"startTime\":\"01:00:00\"}")));
    }

    @Test
    @DisplayName("the capacity and the notes travel when given")
    void createsWithCapacityAndNotes() throws Exception {
        stubPage(FIRST);
        stubCreated();

        mockMvc.perform(submit(post(SESSIONS), logIn("INSTRUCTOR"), "localDate", "2026-11-17", "localTime", "22:00",
                "capacity", "12", "notes", " Traer zapatos ")).andExpect(status().is3xxRedirection());

        WIRE_MOCK_SERVER.verify(1, postRequestedFor(urlPathEqualTo(SESSIONS_API)).withRequestBody(equalToJson(
                "{\"date\":\"2026-11-18\",\"startTime\":\"01:00:00\",\"capacity\":12,\"notes\":\"Traer zapatos\"}")));
    }

    @Test
    @DisplayName("the list confirms the creation once, in a status region")
    void confirmationShownOnce() throws Exception {
        stubPage(FIRST);
        stubCreated();
        Cookie session = logIn("ADMIN");
        mockMvc.perform(submit(post(SESSIONS), session, "localDate", "2026-11-17", "localTime", "22:00"));

        String first = mainOfGet(SESSIONS, session);
        String second = mainOfGet(SESSIONS, session);

        assertThat(first).containsPattern("<[^>]*role=\"status\"[^>]*>\\s*<p>Creamos la sesión.</p>");
        assertThat(second).doesNotContain("Creamos la sesión");
    }

    @Test
    @DisplayName("a date the API already has a session for fails next to the date field, in the academy's zone, "
            + "with a 409, and the page keeps the list and what was typed")
    void conflictOnTheDate() throws Exception {
        stubPage(FIRST);
        stubConflict(SESSIONS_API, "2026-11-18T01:00:00Z");

        MvcResult result = mockMvc.perform(submit(post(SESSIONS), logIn("ADMIN"),
                "localDate", "2026-11-17", "localTime", "22:00", "capacity", "12")).andReturn();
        String main = mainOf(result.getResponse().getContentAsString());

        assertThat(result.getResponse().getStatus()).isEqualTo(409);
        assertThat(main).contains("data-error-summary").contains("Hay 1 error en el formulario")
                .containsPattern("<a href=\"#localDate\">Ya existe una sesión de este curso el martes 17/11 "
                        + "22:00.</a>")
                .containsPattern("<p[^>]*class=\"field-error\"[^>]*>Ya existe una sesión de este curso el martes "
                        + "17/11 22:00.</p>")
                .containsPattern("<input[^>]*id=\"localDate\"[^>]*aria-invalid=\"true\"")
                .containsPattern("<input[^>]*id=\"localDate\"[^>]*value=\"2026-11-17\"")
                .containsPattern("<input[^>]*id=\"capacity\"[^>]*value=\"12\"")
                .containsPattern("<caption[^>]*>\\s*Sesiones")
                .doesNotContain("secret-detail").doesNotContain("Creamos la sesión");
    }

    @Test
    @DisplayName("an invalid form is checked locally, next to the fields, and the API is not asked to create")
    void invalidFormIsNotSent() throws Exception {
        stubPage(FIRST);
        stubCreated();

        MvcResult result = mockMvc.perform(submit(post(SESSIONS), logIn("ADMIN"),
                "localDate", "2026-02-30", "localTime", "25:00", "capacity", "0")).andReturn();
        String main = mainOf(result.getResponse().getContentAsString());

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(main).contains("data-error-summary").contains("Hay 3 errores en el formulario")
                .containsPattern("<a href=\"#localDate\">Ingresá una fecha válida, con el formato aaaa-mm-dd</a>")
                .containsPattern("<a href=\"#localTime\">Ingresá la hora con el formato HH:mm</a>")
                .containsPattern("<a href=\"#capacity\">Ingresá un cupo de 1 persona o más</a>")
                .containsPattern("<input[^>]*id=\"capacity\"[^>]*aria-describedby=\"capacity-help\"");
        WIRE_MOCK_SERVER.verify(0, postRequestedFor(urlPathEqualTo(SESSIONS_API)));
    }

    @Test
    @DisplayName("an empty submission asks for the date")
    void emptySubmission() throws Exception {
        stubPage();
        stubCreated();

        String main = mainOf(mockMvc.perform(submit(post(SESSIONS), logIn("ADMIN"))).andReturn().getResponse()
                .getContentAsString());

        assertThat(main).contains("Ingresá la fecha de la sesión");
        WIRE_MOCK_SERVER.verify(0, postRequestedFor(urlPathEqualTo(SESSIONS_API)));
    }

    @Test
    @DisplayName("a 400 of the API is a general error above the form, with a 400")
    void apiInvalidRequest() throws Exception {
        stubPage(FIRST);
        stubProblem("POST", SESSIONS_API, 400, "INVALID_REQUEST");

        MvcResult result = mockMvc.perform(submit(post(SESSIONS), logIn("ADMIN"),
                "localDate", "2026-11-17", "localTime", "22:00")).andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        assertThat(mainOf(result.getResponse().getContentAsString()))
                .containsPattern("<[^>]*role=\"alert\"[^>]*>\\s*<p>Revisá los datos e intentá de nuevo.</p>")
                .doesNotContain("secret-detail");
    }

    @ParameterizedTest(name = "API status {0}")
    @ValueSource(ints = {401, 429, 500, 503})
    @DisplayName("a failing API on creation is a 503 with a general error above the form, and no detail")
    void createUnavailable(int apiStatus, CapturedOutput output) throws Exception {
        stubPage(FIRST);
        stubProblem("POST", SESSIONS_API, apiStatus, "SOMETHING_ELSE");

        MvcResult result = mockMvc.perform(submit(post(SESSIONS), logIn("ADMIN"),
                "localDate", "2026-11-17", "localTime", "22:00")).andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(503);
        assertThat(mainOf(result.getResponse().getContentAsString()))
                .containsPattern("<[^>]*role=\"alert\"[^>]*>\\s*<p>No pudimos crear la sesión. Probá de nuevo en "
                        + "unos minutos.</p>")
                .doesNotContain("secret-detail").doesNotContain("SOMETHING_ELSE");
        assertThat(output.getAll()).doesNotContain("secret-detail").doesNotContain(TestAccessTokens.withRole("ADMIN"));
    }

    @Test
    @DisplayName("creating a session on a course that is not the instructor's gets the 403 notice")
    void createOnForeignCourse() throws Exception {
        stubPage();
        stubProblem("POST", SESSIONS_API, 403, "COURSE_NOT_OWNED");

        MvcResult result = mockMvc.perform(submit(post(SESSIONS), logIn("INSTRUCTOR"),
                "localDate", "2026-11-17", "localTime", "22:00")).andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(403);
        assertThat(mainOf(result.getResponse().getContentAsString())).contains("No podés gestionar este curso.");
    }

    @Test
    @DisplayName("creating a session on a malformed course id is a 404, and the API is not asked")
    void createOnMalformedCourse() throws Exception {
        MvcResult result = mockMvc.perform(submit(post(BASE + "/xyz/sesiones"), logIn("ADMIN"),
                "localDate", "2026-11-17", "localTime", "22:00")).andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(404);
        WIRE_MOCK_SERVER.verify(0, postRequestedFor(urlPathEqualTo(COURSES_API + "/xyz/sessions")));
    }
}
