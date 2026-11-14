package com.menta.bff.infrastructure.integration;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.anyRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static com.menta.bff.infrastructure.integration.HtmlSections.mainOf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.github.tomakehurst.wiremock.client.WireMock;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.test.web.servlet.MvcResult;

/**
 * The page of a session with its students (#368): who may open it, how the session is looked up in the course of
 * the URL before the roster is read, what it renders (header, students, attendance as text, local times) and how
 * each failure comes back, without ever showing the API's body. Each case logs in for real against the stubbed
 * Auth API and reuses the session cookie.
 */
@ExtendWith(OutputCaptureExtension.class)
@DisplayName("Management session roster Integration Tests")
class ManagementSessionRosterIntegrationTest extends BaseIntegrationTest {

    private static final String COURSE_ID = "5b6f7c8e-1d2a-4e3b-9c4d-0a1b2c3d4e5f";
    private static final String OTHER_COURSE_ID = "9b6f7c8e-1d2a-4e3b-9c4d-0a1b2c3d4e5f";
    private static final String SESSION_A = "a1b2c3d4-1d2a-4e3b-9c4d-0a1b2c3d4e01";
    private static final String SESSION_B = "a1b2c3d4-1d2a-4e3b-9c4d-0a1b2c3d4e02";
    private static final String UNKNOWN = "a1b2c3d4-1d2a-4e3b-9c4d-0a1b2c3d4e99";
    private static final String ADA_ID = "0a1b2c3d-1d2a-4e3b-9c4d-5b6f7c8e9f00";
    private static final String JOSE_ID = "9a1b2c3d-1d2a-4e3b-9c4d-5b6f7c8e9f00";
    private static final String MARIA_ID = "8a1b2c3d-1d2a-4e3b-9c4d-5b6f7c8e9f00";
    private static final String BASE = "/gestion/cursos-presenciales";
    private static final String SESSIONS = BASE + "/" + COURSE_ID + "/sesiones";
    private static final String COURSES_API = "/api/v1/admin/physical/courses";
    private static final String SESSIONS_API = COURSES_API + "/" + COURSE_ID + "/sessions";
    private static final String ROSTER_API = "/api/v1/admin/physical/sessions/";
    private static final String NO_STORE = "no-store";

    private static String sessionJson(String id, String scheduledAt, String status) {
        return ("{\"sessionId\":\"%s\",\"courseId\":\"%s\",\"scheduledAt\":\"%s\",\"capacity\":15,"
                + "\"assignedSpots\":2,\"activeCapacityHolds\":0,\"availableSpots\":13,\"status\":\"%s\","
                + "\"notes\":null}").formatted(id, COURSE_ID, scheduledAt, status);
    }

    /** Tuesday 2026-11-17 22:00 local. */
    private static final String SCHEDULED = sessionJson(SESSION_A, "2026-11-18T01:00:00Z", "SCHEDULED");
    /** Tuesday 2026-11-24 22:00 local, cancelled. */
    private static final String CANCELLED = sessionJson(SESSION_B, "2026-11-25T01:00:00Z", "CANCELLED");

    private static String student(String id, String name, String type, String attendance, String at, String kind) {
        return ("{\"studentId\":\"%s\",\"displayName\":\"%s\",\"assignmentType\":\"%s\",\"attendanceStatus\":\"%s\","
                + "\"checkedInAt\":%s,\"checkInKind\":%s}").formatted(id, name, type, attendance,
                at == null ? "null" : "\"" + at + "\"", kind == null ? "null" : "\"" + kind + "\"");
    }

    /** Checked in by QR at 22:05 local. */
    private static final String ADA_PRESENT =
            student(ADA_ID, "Ada Lovelace", "MONTHLY", "PRESENT", "2026-11-18T01:05:00Z", "QR");
    private static final String JOSE_PENDING = student(JOSE_ID, "José Pérez", "INDIVIDUAL", "PENDING", null, null);
    /** Checked in by reception at 22:10 local. */
    private static final String MARIA_MANUAL =
            student(MARIA_ID, "María Gómez", "MONTHLY", "PRESENT", "2026-11-18T01:10:00Z", "MANUAL");

    private static String rosterBody(String courseId, String sessionId, String status, String... students) {
        return ("{\"session\":{\"sessionId\":\"%s\",\"courseId\":\"%s\",\"courseName\":\"Salsa\","
                + "\"scheduledAt\":\"2026-11-18T01:00:00Z\",\"status\":\"%s\",\"capacity\":15,\"studentCount\":%d},"
                + "\"students\":[%s]}").formatted(sessionId, courseId, status, students.length,
                String.join(",", students));
    }

    private static String rosterPath(String sessionId) {
        return SESSIONS + "/" + sessionId;
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

    private static void stubSessions(String... sessions) {
        WIRE_MOCK_SERVER.stubFor(WireMock.get(urlPathEqualTo(SESSIONS_API)).willReturn(aResponse()
                .withStatus(200).withHeader("Content-Type", "application/json")
                .withBody("{\"sessions\":[%s]}".formatted(String.join(",", sessions)))));
    }

    private static void stubRoster(String sessionId, String body) {
        WIRE_MOCK_SERVER.stubFor(WireMock.get(urlEqualTo(ROSTER_API + sessionId + "/roster")).willReturn(aResponse()
                .withStatus(200).withHeader("Content-Type", "application/json").withBody(body)));
    }

    private static void stubProblem(String path, int status, String code) {
        WIRE_MOCK_SERVER.stubFor(WireMock.get(urlPathEqualTo(path)).willReturn(aResponse()
                .withStatus(status).withHeader("Content-Type", "application/problem+json")
                .withBody(("{\"type\":\"https://menta.dance/problems/x\",\"status\":%d,\"detail\":\"secret-detail\","
                        + "\"code\":\"%s\"}").formatted(status, code))));
    }

    private static void verifyRosterNeverRead() {
        WIRE_MOCK_SERVER.verify(0, anyRequestedFor(urlPathMatching(ROSTER_API + ".+/roster")));
    }

    private MvcResult open(Cookie session, String url) throws Exception {
        return mockMvc.perform(get(url).cookie(session)).andReturn();
    }

    // ---- access ----

    @ParameterizedTest(name = "{0} is refused")
    @ValueSource(strings = {"RECEPTIONIST", "STUDENT"})
    @DisplayName("every other role gets a 403, and the API is never asked")
    void otherRolesAreRefused(String role) throws Exception {
        stubSessions(SCHEDULED);
        stubRoster(SESSION_A, rosterBody(COURSE_ID, SESSION_A, "SCHEDULED", ADA_PRESENT));
        Cookie session = logIn(role);

        mockMvc.perform(get(rosterPath(SESSION_A)).cookie(session)).andExpect(status().isForbidden());

        WIRE_MOCK_SERVER.verify(0, anyRequestedFor(urlPathEqualTo(SESSIONS_API)));
        verifyRosterNeverRead();
    }

    @Test
    @DisplayName("an anonymous visitor is sent to the login page")
    void anonymousGoesToLogin() throws Exception {
        mockMvc.perform(get(rosterPath(SESSION_A))).andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));
    }

    // ---- the page ----

    @ParameterizedTest(name = "{0} opens the roster")
    @ValueSource(strings = {"ADMIN", "INSTRUCTOR"})
    @DisplayName("an administrator and an instructor see the course, the moment in the academy's zone, the status "
            + "as text, the numbers and each student with attendance as text and local check-in times; never cached")
    void rosterPage(String role) throws Exception {
        stubSessions(SCHEDULED);
        stubRoster(SESSION_A, rosterBody(COURSE_ID, SESSION_A, "SCHEDULED", ADA_PRESENT, JOSE_PENDING, MARIA_MANUAL));

        MvcResult result = open(logIn(role), rosterPath(SESSION_A));
        String html = result.getResponse().getContentAsString();
        String main = mainOf(html);

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(result.getResponse().getHeader("Cache-Control")).contains(NO_STORE);
        assertThat(html).contains("<title>Alumnos de la sesión - Menta Dance</title>");
        assertThat(main).containsPattern("<h1[^>]*>\\s*Alumnos de <span>Salsa</span>\\s*</h1>")
                .containsPattern("<a[^>]*aria-current=\"page\"[^>]*>\\s*Cursos presenciales\\s*</a>")
                .containsPattern("<a href=\"" + SESSIONS + "\">Volver a las sesiones</a>")
                .contains("martes 17 de noviembre a las 22:00")
                .containsPattern("<span class=\"badge[^\"]*\">Programada</span>")
                .containsPattern("<dt[^>]*>Cupo total</dt>\\s*<dd[^>]*>15</dd>")
                .containsPattern("<dt[^>]*>Alumnos con cupo</dt>\\s*<dd[^>]*>3</dd>")
                .containsPattern("<dt[^>]*>Con asistencia registrada</dt>\\s*<dd[^>]*>2</dd>")
                .containsPattern("<caption[^>]*>\\s*Alumnos con cupo \\(<span>3</span>\\)")
                .containsPattern("<th scope=\"col\"[^>]*>Alumno</th>")
                .containsPattern("<th scope=\"col\"[^>]*>Tipo de cupo</th>")
                .containsPattern("<th scope=\"col\"[^>]*>Asistencia</th>")
                .containsPattern("<th scope=\"col\"[^>]*>Registro</th>")
                .containsPattern("<th scope=\"row\"[^>]*>Ada Lovelace</th>")
                .containsPattern("<th scope=\"row\"[^>]*>José Pérez</th>")
                .containsPattern("<th scope=\"row\"[^>]*>María Gómez</th>")
                .contains("Plan mensual").contains("Clase individual")
                .containsPattern("<span class=\"badge badge-success\">Presente</span>")
                .containsPattern("<span class=\"badge badge-warning\">Pendiente</span>")
                .containsPattern("<span>22:05, QR</span>")
                .containsPattern("<span>22:10, manual</span>")
                .containsPattern("<span class=\"sr-only\">Sin registro</span>")
                .doesNotContain("01:05").doesNotContain("01:10");
    }

    @Test
    @DisplayName("the page is read only: no form that changes anything, no CSRF token, no check-in button")
    void rosterPageIsReadOnly() throws Exception {
        stubSessions(SCHEDULED);
        stubRoster(SESSION_A, rosterBody(COURSE_ID, SESSION_A, "SCHEDULED", ADA_PRESENT, JOSE_PENDING));

        String main = mainOf(open(logIn("ADMIN"), rosterPath(SESSION_A)).getResponse().getContentAsString());

        assertThat(main).doesNotContainPattern("method=\"post\"").doesNotContain("_csrf")
                .doesNotContain("Registrar asistencia").doesNotContain("/asistencias")
                .doesNotContain("<button type=\"button\"")
                .containsPattern("<form[^>]*method=\"get\"[^>]*role=\"search\"")
                .containsPattern("<label[^>]*for=\"q\"[^>]*>Buscar alumno por nombre</label>");
    }

    @Test
    @DisplayName("the lookup reads the course's sessions first and then the roster of the listed session, with the "
            + "caller's bearer token")
    void callsTheApiInOrderWithTheToken() throws Exception {
        stubSessions(SCHEDULED);
        stubRoster(SESSION_A, rosterBody(COURSE_ID, SESSION_A, "SCHEDULED", ADA_PRESENT));

        open(logIn("INSTRUCTOR"), rosterPath(SESSION_A));

        String bearer = "Bearer " + TestAccessTokens.withRole("INSTRUCTOR");
        WIRE_MOCK_SERVER.verify(1, getRequestedFor(urlPathEqualTo(SESSIONS_API))
                .withHeader("Authorization", equalTo(bearer)));
        WIRE_MOCK_SERVER.verify(1, getRequestedFor(urlEqualTo(ROSTER_API + SESSION_A + "/roster"))
                .withHeader("Authorization", equalTo(bearer)));
    }

    @Test
    @DisplayName("a session without students says so and shows no table")
    void emptyRoster() throws Exception {
        stubSessions(SCHEDULED);
        stubRoster(SESSION_A, rosterBody(COURSE_ID, SESSION_A, "SCHEDULED"));

        String main = mainOf(open(logIn("ADMIN"), rosterPath(SESSION_A)).getResponse().getContentAsString());

        assertThat(main).contains("Todavía no hay alumnos asignados a esta sesión.")
                .containsPattern("<dt[^>]*>Alumnos con cupo</dt>\\s*<dd[^>]*>0</dd>")
                .doesNotContain("<table").doesNotContain("Ningún alumno coincide");
    }

    @Test
    @DisplayName("a cancelled session can still be opened, shows its status as text and keeps its students")
    void cancelledSession() throws Exception {
        stubSessions(CANCELLED);
        stubRoster(SESSION_B, rosterBody(COURSE_ID, SESSION_B, "CANCELLED", ADA_PRESENT, JOSE_PENDING));

        MvcResult result = open(logIn("ADMIN"), rosterPath(SESSION_B));
        String main = mainOf(result.getResponse().getContentAsString());

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(main).containsPattern("<span class=\"badge[^\"]*\">Cancelada</span>")
                .contains("La sesión está cancelada.")
                .containsPattern("<th scope=\"row\"[^>]*>Ada Lovelace</th>")
                .containsPattern("<th scope=\"row\"[^>]*>José Pérez</th>")
                .doesNotContain("Programada").doesNotContain("Registrar asistencia");
    }

    // ---- search ----

    @Test
    @DisplayName("the search by name ignores case and accents, keeps the totals and the text in the box")
    void searchByName() throws Exception {
        stubSessions(SCHEDULED);
        stubRoster(SESSION_A, rosterBody(COURSE_ID, SESSION_A, "SCHEDULED", ADA_PRESENT, JOSE_PENDING, MARIA_MANUAL));

        String main = mainOf(open(logIn("ADMIN"), rosterPath(SESSION_A) + "?q=JOSE")
                .getResponse().getContentAsString());

        assertThat(main).containsPattern("<th scope=\"row\"[^>]*>José Pérez</th>")
                .doesNotContain("Ada Lovelace").doesNotContain("María Gómez")
                .containsPattern("<caption[^>]*>\\s*Alumnos con cupo \\(<span>1</span><span> de 3</span>\\)")
                .containsPattern("<input[^>]*name=\"q\"[^>]*value=\"JOSE\"")
                .containsPattern("<a href=\"" + rosterPath(SESSION_A) + "\">Limpiar</a>");
    }

    @Test
    @DisplayName("a search that matches nobody says so")
    void searchWithoutMatches() throws Exception {
        stubSessions(SCHEDULED);
        stubRoster(SESSION_A, rosterBody(COURSE_ID, SESSION_A, "SCHEDULED", ADA_PRESENT));

        String main = mainOf(open(logIn("ADMIN"), rosterPath(SESSION_A) + "?q=zzz")
                .getResponse().getContentAsString());

        assertThat(main).contains("Ningún alumno coincide con la búsqueda.").doesNotContain("<table")
                .doesNotContain("Todavía no hay alumnos");
    }

    @Test
    @DisplayName("a hostile display name and a hostile search text are escaped, never markup")
    void hostileTextIsEscaped() throws Exception {
        String hostile = "<script>alert(1)</script>";
        stubSessions(SCHEDULED);
        stubRoster(SESSION_A, rosterBody(COURSE_ID, SESSION_A, "SCHEDULED",
                student(ADA_ID, "Ada " + hostile, "MONTHLY", "PENDING", null, null)));

        String main = mainOf(mockMvc.perform(get(rosterPath(SESSION_A)).param("q", "\"><img src=x>")
                .cookie(logIn("ADMIN"))).andReturn().getResponse().getContentAsString());

        assertThat(main).doesNotContain(hostile).doesNotContain("<img src=x>")
                .contains("value=\"&quot;&gt;&lt;img src=x&gt;\"");
        String listed = mainOf(open(logIn("ADMIN"), rosterPath(SESSION_A)).getResponse().getContentAsString());
        assertThat(listed).doesNotContain(hostile).contains("Ada &lt;script&gt;alert(1)&lt;/script&gt;");
    }

    // ---- ownership and lookup ----

    @Test
    @DisplayName("an instructor on a course that is not theirs gets the 403 notice, and the roster is never read")
    void courseNotOwned() throws Exception {
        stubProblem(SESSIONS_API, 403, "COURSE_NOT_OWNED");
        stubRoster(SESSION_A, rosterBody(COURSE_ID, SESSION_A, "SCHEDULED", ADA_PRESENT));

        MvcResult result = open(logIn("INSTRUCTOR"), rosterPath(SESSION_A));
        String main = mainOf(result.getResponse().getContentAsString());

        assertThat(result.getResponse().getStatus()).isEqualTo(403);
        assertThat(main).contains("No podés gestionar este curso.").doesNotContain("secret-detail")
                .doesNotContain("Ada Lovelace").doesNotContain("<table");
        verifyRosterNeverRead();
    }

    @Test
    @DisplayName("a session that the course does not list is a 404 notice with a way back, and the roster is "
            + "never read: an instructor cannot reach a foreign roster with their own course id")
    void sessionOfAnotherCourse() throws Exception {
        stubSessions(SCHEDULED);
        stubRoster(UNKNOWN, rosterBody(OTHER_COURSE_ID, UNKNOWN, "SCHEDULED", ADA_PRESENT));

        MvcResult result = open(logIn("INSTRUCTOR"), rosterPath(UNKNOWN));
        String main = mainOf(result.getResponse().getContentAsString());

        assertThat(result.getResponse().getStatus()).isEqualTo(404);
        assertThat(main).contains("No encontramos esa sesión.").doesNotContain("Ada Lovelace")
                .containsPattern("<a href=\"" + SESSIONS + "\">Volver a las sesiones</a>");
        verifyRosterNeverRead();
    }

    @Test
    @DisplayName("a roster that says it belongs to another course is a 404 and none of its students is shown")
    void rosterOfAnotherCourse() throws Exception {
        stubSessions(SCHEDULED);
        stubRoster(SESSION_A, rosterBody(OTHER_COURSE_ID, SESSION_A, "SCHEDULED", ADA_PRESENT));

        MvcResult result = open(logIn("INSTRUCTOR"), rosterPath(SESSION_A));
        String main = mainOf(result.getResponse().getContentAsString());

        assertThat(result.getResponse().getStatus()).isEqualTo(404);
        assertThat(main).contains("No encontramos esa sesión.").doesNotContain("Ada Lovelace")
                .doesNotContain("<table");
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"no-es-un-uuid", "123", "a1b2c3d4-1d2a-4e3b"})
    @DisplayName("a session id that is not a UUID is a 404, and the API is never asked")
    void malformedSessionId(String id) throws Exception {
        MvcResult result = open(logIn("ADMIN"), rosterPath(id));

        assertThat(result.getResponse().getStatus()).isEqualTo(404);
        assertThat(mainOf(result.getResponse().getContentAsString())).contains("No encontramos esa sesión.");
        WIRE_MOCK_SERVER.verify(0, anyRequestedFor(urlPathEqualTo(SESSIONS_API)));
        verifyRosterNeverRead();
    }

    @Test
    @DisplayName("a course id that is not a UUID is a 404 about the course, and the API is never asked")
    void malformedCourseId() throws Exception {
        MvcResult result = open(logIn("ADMIN"), BASE + "/xyz/sesiones/" + SESSION_A);

        assertThat(result.getResponse().getStatus()).isEqualTo(404);
        assertThat(mainOf(result.getResponse().getContentAsString())).contains("No encontramos ese curso.")
                .contains("Volver a los cursos");
        WIRE_MOCK_SERVER.verify(0, anyRequestedFor(urlPathMatching(COURSES_API + "/.*")));
        verifyRosterNeverRead();
    }

    @Test
    @DisplayName("a roster the API answers as SESSION_NOT_FOUND is the 404 notice, with no API detail")
    void rosterNotFound() throws Exception {
        stubSessions(SCHEDULED);
        stubProblem(ROSTER_API + SESSION_A + "/roster", 404, "SESSION_NOT_FOUND");

        MvcResult result = open(logIn("ADMIN"), rosterPath(SESSION_A));

        assertThat(result.getResponse().getStatus()).isEqualTo(404);
        assertThat(mainOf(result.getResponse().getContentAsString())).contains("No encontramos esa sesión.")
                .doesNotContain("secret-detail").doesNotContain("SESSION_NOT_FOUND");
    }

    // ---- failures ----

    @ParameterizedTest(name = "API status {0}")
    @ValueSource(ints = {401, 429, 500, 503})
    @DisplayName("a failing API on the lookup is a 503 notice with no API detail")
    void lookupUnavailable(int apiStatus, CapturedOutput output) throws Exception {
        stubProblem(SESSIONS_API, apiStatus, "SOMETHING_ELSE");

        MvcResult result = open(logIn("ADMIN"), rosterPath(SESSION_A));

        assertThat(result.getResponse().getStatus()).isEqualTo(503);
        assertThat(mainOf(result.getResponse().getContentAsString()))
                .contains("No pudimos cargar los alumnos de la sesión en este momento. Intentá nuevamente en unos "
                        + "minutos.")
                .doesNotContain("secret-detail").doesNotContain("SOMETHING_ELSE");
        assertThat(output.getAll()).doesNotContain("secret-detail").doesNotContain(TestAccessTokens.withRole("ADMIN"));
    }

    @ParameterizedTest(name = "API status {0}")
    @ValueSource(ints = {400, 401, 403, 429, 500, 503})
    @DisplayName("a failing API on the roster is a 503 notice with no API detail and no student")
    void rosterUnavailable(int apiStatus, CapturedOutput output) throws Exception {
        stubSessions(SCHEDULED);
        stubProblem(ROSTER_API + SESSION_A + "/roster", apiStatus, "SOMETHING_ELSE");

        MvcResult result = open(logIn("ADMIN"), rosterPath(SESSION_A));

        assertThat(result.getResponse().getStatus()).isEqualTo(503);
        assertThat(mainOf(result.getResponse().getContentAsString()))
                .contains("No pudimos cargar los alumnos de la sesión en este momento.")
                .doesNotContain("secret-detail").doesNotContain("SOMETHING_ELSE").doesNotContain("<table");
        assertThat(output.getAll()).doesNotContain("secret-detail").doesNotContain(TestAccessTokens.withRole("ADMIN"));
    }

    // ---- the sessions page links here ----

    @Test
    @DisplayName("every row of the sessions page, cancelled ones included, links to its students")
    void sessionsPageLinksToTheRoster() throws Exception {
        stubSessions(SCHEDULED, CANCELLED);
        WIRE_MOCK_SERVER.stubFor(WireMock.get(urlPathEqualTo(COURSES_API)).willReturn(aResponse()
                .withStatus(200).withHeader("Content-Type", "application/json").withBody(
                        ("{\"courses\":[{\"courseId\":\"%s\",\"title\":\"Salsa\",\"description\":\"d\","
                                + "\"professorId\":\"0a1b2c3d-1d2a-4e3b-9c4d-5b6f7c8e9f00\",\"professorName\":\"Ada\","
                                + "\"dayOfWeek\":\"WEDNESDAY\",\"startTime\":\"01:00:00\",\"durationMinutes\":60,"
                                + "\"level\":\"BEGINNER\",\"capacity\":15,\"status\":\"ACTIVE\"}]}")
                                .formatted(COURSE_ID))));

        String main = mainOf(open(logIn("INSTRUCTOR"), SESSIONS).getResponse().getContentAsString());

        assertThat(main)
                .contains("<a href=\"" + rosterPath(SESSION_A) + "\">Ver alumnos<span class=\"sr-only\"> de la "
                        + "sesión del martes 17 de noviembre a las 22:00</span></a>")
                .contains("<a href=\"" + rosterPath(SESSION_B) + "\">Ver alumnos<span class=\"sr-only\"> de la "
                        + "sesión del martes 24 de noviembre a las 22:00</span></a>");
    }
}
