package com.menta.bff.infrastructure.integration;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.anyRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.patchRequestedFor;
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
 * The reschedule and cancel pages of a session (#368): who may open them, how the session is looked up in the
 * sessions the API lists for the course in the URL, what they warn, what the reschedule sends (the new local date
 * and time converted to UTC, always together) and how each answer and each API error code comes back, without
 * ever showing the API's body. Each case logs in for real against the stubbed Auth API and reuses the session
 * cookie.
 */
@ExtendWith(OutputCaptureExtension.class)
@DisplayName("Management session changes Integration Tests")
class ManagementSessionChangesIntegrationTest extends BaseIntegrationTest {

    private static final String COURSE_ID = "5b6f7c8e-1d2a-4e3b-9c4d-0a1b2c3d4e5f";
    private static final String SESSION_A = "a1b2c3d4-1d2a-4e3b-9c4d-0a1b2c3d4e01";
    private static final String SESSION_B = "a1b2c3d4-1d2a-4e3b-9c4d-0a1b2c3d4e02";
    private static final String SESSION_FULL = "a1b2c3d4-1d2a-4e3b-9c4d-0a1b2c3d4e03";
    private static final String UNKNOWN = "a1b2c3d4-1d2a-4e3b-9c4d-0a1b2c3d4e99";
    private static final String BASE = "/gestion/cursos-presenciales";
    private static final String SESSIONS = BASE + "/" + COURSE_ID + "/sesiones";
    private static final String COURSES_API = "/api/v1/admin/physical/courses";
    private static final String SESSIONS_API = COURSES_API + "/" + COURSE_ID + "/sessions";
    private static final String SESSION_API = "/api/v1/admin/physical/sessions/";
    private static final String NO_STORE = "no-store";

    private static String sessionJson(String id, String scheduledAt, int capacity, int assigned, int holds,
            String status) {
        return ("{\"sessionId\":\"%s\",\"courseId\":\"%s\",\"scheduledAt\":\"%s\",\"capacity\":%d,"
                + "\"assignedSpots\":%d,\"activeCapacityHolds\":%d,\"availableSpots\":%d,\"status\":\"%s\","
                + "\"notes\":null}").formatted(id, COURSE_ID, scheduledAt, capacity, assigned, holds,
                capacity - assigned - holds, status);
    }

    /** Tuesday 2026-11-17 22:00 local, nobody in it. */
    private static final String EMPTY = sessionJson(SESSION_A, "2026-11-18T01:00:00Z", 10, 0, 0, "SCHEDULED");
    /** Tuesday 2026-11-24 22:00 local, cancelled. */
    private static final String CANCELLED = sessionJson(SESSION_B, "2026-11-25T01:00:00Z", 10, 0, 0, "CANCELLED");
    /** Tuesday 2026-12-01 22:00 local, 2 confirmed and 1 held of 10. */
    private static final String TAKEN = sessionJson(SESSION_FULL, "2026-12-02T01:00:00Z", 10, 2, 1, "SCHEDULED");

    private static String reschedulePath(String sessionId) {
        return SESSIONS + "/" + sessionId + "/reprogramar";
    }

    private static String cancelPath(String sessionId) {
        return SESSIONS + "/" + sessionId + "/cancelar";
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

    private static void stubProblem(String method, String path, int status, String code) {
        WIRE_MOCK_SERVER.stubFor(WireMock.request(method, urlPathEqualTo(path)).willReturn(aResponse()
                .withStatus(status).withHeader("Content-Type", "application/problem+json")
                .withBody(("{\"type\":\"https://menta.dance/problems/x\",\"status\":%d,\"detail\":\"secret-detail\","
                        + "\"code\":\"%s\"}").formatted(status, code))));
    }

    private static void stubUpdated(String sessionId, String body) {
        WIRE_MOCK_SERVER.stubFor(WireMock.patch(urlPathEqualTo(SESSION_API + sessionId)).willReturn(aResponse()
                .withStatus(200).withHeader("Content-Type", "application/json").withBody(body)));
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

    @ParameterizedTest(name = "{0} is refused")
    @ValueSource(strings = {"RECEPTIONIST", "STUDENT"})
    @DisplayName("every other role gets a 403 on the four routes, and the API is never asked")
    void otherRolesAreRefused(String role) throws Exception {
        Cookie session = logIn(role);

        mockMvc.perform(get(reschedulePath(SESSION_A)).cookie(session)).andExpect(status().isForbidden());
        mockMvc.perform(get(cancelPath(SESSION_A)).cookie(session)).andExpect(status().isForbidden());
        mockMvc.perform(submit(post(reschedulePath(SESSION_A)), session, "localDate", "2026-11-24",
                "localTime", "22:00")).andExpect(status().isForbidden());
        mockMvc.perform(submit(post(cancelPath(SESSION_A)), session)).andExpect(status().isForbidden());

        WIRE_MOCK_SERVER.verify(0, anyRequestedFor(urlPathEqualTo(SESSIONS_API)));
        WIRE_MOCK_SERVER.verify(0, patchRequestedFor(urlPathEqualTo(SESSION_API + SESSION_A)));
    }

    @Test
    @DisplayName("an anonymous visitor is sent to the login page on the four routes")
    void anonymousGoesToLogin() throws Exception {
        mockMvc.perform(get(reschedulePath(SESSION_A))).andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));
        mockMvc.perform(get(cancelPath(SESSION_A))).andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));
        mockMvc.perform(post(reschedulePath(SESSION_A)).with(csrf())).andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));
        mockMvc.perform(post(cancelPath(SESSION_A)).with(csrf())).andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));
    }

    @Test
    @DisplayName("a submission without a CSRF token is refused for an administrator, and the API is not asked")
    void submissionsNeedCsrf() throws Exception {
        stubSessions(EMPTY);
        stubUpdated(SESSION_A, EMPTY);
        Cookie session = logIn("ADMIN");

        mockMvc.perform(post(reschedulePath(SESSION_A)).cookie(session).param("localDate", "2026-11-24")
                .param("localTime", "22:00")).andExpect(status().isForbidden());
        mockMvc.perform(post(cancelPath(SESSION_A)).cookie(session)).andExpect(status().isForbidden());

        WIRE_MOCK_SERVER.verify(0, patchRequestedFor(urlPathEqualTo(SESSION_API + SESSION_A)));
    }

    // ---- reschedule page ----

    @ParameterizedTest(name = "{0} opens the reschedule page")
    @ValueSource(strings = {"ADMIN", "INSTRUCTOR"})
    @DisplayName("an administrator and an instructor open the reschedule page: the current moment, a labelled "
            + "date and time, CSRF, and a way back, never cached")
    void rescheduleForm(String role) throws Exception {
        stubSessions(CANCELLED, EMPTY);

        MvcResult result = mockMvc.perform(get(reschedulePath(SESSION_A)).cookie(logIn(role)))
                .andExpect(status().isOk()).andReturn();
        String html = result.getResponse().getContentAsString();
        String main = mainOf(html);

        assertThat(result.getResponse().getHeader("Cache-Control")).contains(NO_STORE);
        assertThat(html).contains("<title>Reprogramar sesión - Menta Dance</title>");
        assertThat(main).containsPattern("<h1[^>]*>\\s*Reprogramar sesión\\s*</h1>")
                .containsPattern("<a[^>]*aria-current=\"page\"[^>]*>\\s*Cursos presenciales\\s*</a>")
                .contains("<strong>martes 17 de noviembre a las 22:00</strong>")
                .containsPattern("<form[^>]*action=\"" + reschedulePath(SESSION_A) + "\"[^>]*method=\"post\"")
                .containsPattern("<input[^>]*type=\"hidden\"[^>]*name=\"_csrf\"")
                .containsPattern("<label[^>]*for=\"localDate\"[^>]*>Nueva fecha</label>")
                .containsPattern("<input[^>]*id=\"localDate\"[^>]*type=\"date\"[^>]*name=\"localDate\"")
                .containsPattern("<label[^>]*for=\"localTime\"[^>]*>Nueva hora de inicio</label>")
                .containsPattern("<input[^>]*id=\"localTime\"[^>]*type=\"time\"[^>]*value=\"22:00\"")
                .containsPattern("<button[^>]*type=\"submit\"[^>]*>\\s*Reprogramar sesión\\s*</button>")
                .containsPattern("<a href=\"" + SESSIONS + "\">Volver a las sesiones</a>")
                .doesNotContain("alumnos asignados").doesNotContain("data-error-summary");
    }

    @Test
    @DisplayName("a session with spots taken carries a visible warning that says no more than what is known")
    void rescheduleWarnsWhenSpotsAreTaken() throws Exception {
        stubSessions(TAKEN);

        String main = mainOfGet(reschedulePath(SESSION_FULL), logIn("ADMIN"));

        assertThat(main).containsPattern("<[^>]*role=\"status\"[^>]*>\\s*<p>Esta sesión ya tiene 3 alumnos asignados."
                        + "</p>")
                .doesNotContain("avis").doesNotContain("notific");
    }

    @Test
    @DisplayName("a single spot is worded in the singular")
    void rescheduleWarnsInTheSingular() throws Exception {
        stubSessions(sessionJson(SESSION_FULL, "2026-12-02T01:00:00Z", 10, 1, 0, "SCHEDULED"));

        assertThat(mainOfGet(reschedulePath(SESSION_FULL), logIn("ADMIN")))
                .contains("Esta sesión ya tiene 1 alumno asignado.");
    }

    @Test
    @DisplayName("a cancelled session shows a notice instead of the form")
    void rescheduleOfACancelledSession() throws Exception {
        stubSessions(CANCELLED);

        String main = mainOfGet(reschedulePath(SESSION_B), logIn("ADMIN"));

        assertThat(main).contains("La sesión está cancelada: no se puede reprogramar.")
                .doesNotContain("<form").contains("Volver a las sesiones");
    }

    @Test
    @DisplayName("reschedules to the new local date and time converted to UTC, with nothing else, and redirects to "
            + "the sessions with the confirmation once")
    void reschedules() throws Exception {
        stubSessions(EMPTY);
        stubUpdated(SESSION_A, EMPTY);
        Cookie session = logIn("ADMIN");

        MvcResult result = mockMvc.perform(submit(post(reschedulePath(SESSION_A)), session,
                        "localDate", "2026-11-19", "localTime", "22:00"))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl(SESSIONS)).andReturn();

        assertThat(result.getResponse().getHeader("Cache-Control")).contains(NO_STORE);
        WIRE_MOCK_SERVER.verify(1, patchRequestedFor(urlPathEqualTo(SESSION_API + SESSION_A))
                .withHeader("Authorization", equalTo("Bearer " + TestAccessTokens.withRole("ADMIN")))
                .withRequestBody(equalToJson("{\"date\":\"2026-11-20\",\"startTime\":\"01:00:00\"}")));
        stubPageForList();
        String first = mainOfGet(SESSIONS, session);
        String second = mainOfGet(SESSIONS, session);
        assertThat(first).containsPattern("<[^>]*role=\"status\"[^>]*>\\s*<p>Reprogramamos la sesión.</p>");
        assertThat(second).doesNotContain("Reprogramamos la sesión");
    }

    private static void stubPageForList() {
        stubSessions(EMPTY);
        WIRE_MOCK_SERVER.stubFor(WireMock.get(urlPathEqualTo(COURSES_API)).willReturn(aResponse()
                .withStatus(200).withHeader("Content-Type", "application/json").withBody(
                        ("{\"courses\":[{\"courseId\":\"%s\",\"title\":\"Salsa\",\"description\":\"d\","
                                + "\"professorId\":\"0a1b2c3d-1d2a-4e3b-9c4d-5b6f7c8e9f00\",\"professorName\":\"Ada\","
                                + "\"dayOfWeek\":\"WEDNESDAY\",\"startTime\":\"01:00:00\",\"durationMinutes\":60,"
                                + "\"level\":\"BEGINNER\",\"capacity\":15,\"status\":\"ACTIVE\"}]}")
                                .formatted(COURSE_ID))));
    }

    @Test
    @DisplayName("an instructor reschedules too, and the time before midnight UTC keeps the same UTC date")
    void instructorReschedules() throws Exception {
        stubSessions(EMPTY);
        stubUpdated(SESSION_A, EMPTY);

        mockMvc.perform(submit(post(reschedulePath(SESSION_A)), logIn("INSTRUCTOR"),
                "localDate", "2026-11-18", "localTime", "20:30")).andExpect(status().is3xxRedirection());

        WIRE_MOCK_SERVER.verify(1, patchRequestedFor(urlPathEqualTo(SESSION_API + SESSION_A))
                .withRequestBody(equalToJson("{\"date\":\"2026-11-18\",\"startTime\":\"23:30:00\"}")));
    }

    @Test
    @DisplayName("a session that already took place fails next to the date, with a 409, and the page keeps what "
            + "was typed")
    void rescheduleAlreadyOccurred() throws Exception {
        stubSessions(EMPTY);
        stubProblem("PATCH", SESSION_API + SESSION_A, 409, "SESSION_ALREADY_OCCURRED");

        MvcResult result = mockMvc.perform(submit(post(reschedulePath(SESSION_A)), logIn("ADMIN"),
                "localDate", "2026-11-24", "localTime", "21:00")).andReturn();
        String main = mainOf(result.getResponse().getContentAsString());

        assertThat(result.getResponse().getStatus()).isEqualTo(409);
        assertThat(main).contains("data-error-summary")
                .containsPattern("<a href=\"#localDate\">No se puede reprogramar una sesión que ya ocurrió.</a>")
                .containsPattern("<p[^>]*class=\"field-error\"[^>]*>No se puede reprogramar una sesión que ya "
                        + "ocurrió.</p>")
                .containsPattern("<input[^>]*id=\"localDate\"[^>]*aria-invalid=\"true\"")
                .containsPattern("<input[^>]*id=\"localDate\"[^>]*value=\"2026-11-24\"")
                .containsPattern("<input[^>]*id=\"localTime\"[^>]*value=\"21:00\"")
                .doesNotContain("secret-detail");
    }

    @Test
    @DisplayName("a capacity refusal, which a reschedule should never provoke, is a general error with a 409")
    void rescheduleCapacityRefusal() throws Exception {
        stubSessions(EMPTY);
        stubProblem("PATCH", SESSION_API + SESSION_A, 409, "CAPACITY_BELOW_ASSIGNED");

        MvcResult result = mockMvc.perform(submit(post(reschedulePath(SESSION_A)), logIn("ADMIN"),
                "localDate", "2026-11-24", "localTime", "21:00")).andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(409);
        assertThat(mainOf(result.getResponse().getContentAsString()))
                .containsPattern("<[^>]*role=\"alert\"[^>]*>\\s*<p>No pudimos reprogramar la sesión. Revisá los datos "
                        + "e intentá de nuevo.</p>")
                .doesNotContain("CAPACITY_BELOW_ASSIGNED").doesNotContain("secret-detail");
    }

    @Test
    @DisplayName("a 400 of the API is a general error with a 400")
    void rescheduleInvalidRequest() throws Exception {
        stubSessions(EMPTY);
        stubProblem("PATCH", SESSION_API + SESSION_A, 400, "INVALID_REQUEST");

        MvcResult result = mockMvc.perform(submit(post(reschedulePath(SESSION_A)), logIn("ADMIN"),
                "localDate", "2026-11-24", "localTime", "21:00")).andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        assertThat(mainOf(result.getResponse().getContentAsString())).contains("Revisá los datos e intentá de nuevo.");
    }

    @ParameterizedTest(name = "API status {0}")
    @ValueSource(ints = {401, 429, 500, 503})
    @DisplayName("a failing API on the change is a 503 with a general error, and no detail")
    void rescheduleUnavailable(int apiStatus, CapturedOutput output) throws Exception {
        stubSessions(EMPTY);
        stubProblem("PATCH", SESSION_API + SESSION_A, apiStatus, "SOMETHING_ELSE");

        MvcResult result = mockMvc.perform(submit(post(reschedulePath(SESSION_A)), logIn("ADMIN"),
                "localDate", "2026-11-24", "localTime", "21:00")).andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(503);
        assertThat(mainOf(result.getResponse().getContentAsString()))
                .contains("No pudimos reprogramar la sesión. Probá de nuevo en unos minutos.")
                .doesNotContain("secret-detail").doesNotContain("SOMETHING_ELSE");
        assertThat(output.getAll()).doesNotContain("secret-detail").doesNotContain(TestAccessTokens.withRole("ADMIN"));
    }

    @Test
    @DisplayName("an invalid form is checked locally, next to the fields, and the API is not asked to change")
    void rescheduleInvalidForm() throws Exception {
        stubSessions(EMPTY);
        stubUpdated(SESSION_A, EMPTY);

        MvcResult result = mockMvc.perform(submit(post(reschedulePath(SESSION_A)), logIn("ADMIN"),
                "localDate", "2026-02-30", "localTime", "")).andReturn();
        String main = mainOf(result.getResponse().getContentAsString());

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(main).contains("Hay 2 errores en el formulario")
                .containsPattern("<a href=\"#localDate\">Ingresá una fecha válida, con el formato aaaa-mm-dd</a>")
                .containsPattern("<a href=\"#localTime\">Ingresá la hora con el formato HH:mm</a>");
        WIRE_MOCK_SERVER.verify(0, patchRequestedFor(urlPathEqualTo(SESSION_API + SESSION_A)));
    }

    @Test
    @DisplayName("a date without a time is not sent: they always travel together")
    void rescheduleNeedsBoth() throws Exception {
        stubSessions(EMPTY);
        stubUpdated(SESSION_A, EMPTY);

        mockMvc.perform(submit(post(reschedulePath(SESSION_A)), logIn("ADMIN"), "localDate", "2026-11-24"));

        WIRE_MOCK_SERVER.verify(0, patchRequestedFor(urlPathEqualTo(SESSION_API + SESSION_A)));
    }

    @Test
    @DisplayName("posting a reschedule of a cancelled session shows the notice with a 409, and sends nothing")
    void reschedulePostOfACancelledSession() throws Exception {
        stubSessions(CANCELLED);
        stubUpdated(SESSION_B, CANCELLED);

        MvcResult result = mockMvc.perform(submit(post(reschedulePath(SESSION_B)), logIn("ADMIN"),
                "localDate", "2026-11-24", "localTime", "22:00")).andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(409);
        assertThat(mainOf(result.getResponse().getContentAsString()))
                .contains("La sesión está cancelada: no se puede reprogramar.");
        WIRE_MOCK_SERVER.verify(0, patchRequestedFor(urlPathEqualTo(SESSION_API + SESSION_B)));
    }

    // ---- lookup of the session ----

    @Test
    @DisplayName("a session that the course does not list is a 404 notice with a way back to its sessions")
    void unknownSession() throws Exception {
        stubSessions(EMPTY);
        Cookie session = logIn("ADMIN");

        for (String path : new String[] {reschedulePath(UNKNOWN), cancelPath(UNKNOWN)}) {
            MvcResult result = mockMvc.perform(get(path).cookie(session)).andReturn();

            assertThat(result.getResponse().getStatus()).isEqualTo(404);
            assertThat(mainOf(result.getResponse().getContentAsString()))
                    .contains("No encontramos esa sesión.")
                    .containsPattern("<a href=\"" + SESSIONS + "\">Volver a las sesiones</a>");
        }
    }

    @Test
    @DisplayName("a session of another course is not found through this course's URL, and nothing is sent")
    void sessionOfAnotherCourse() throws Exception {
        stubSessions(EMPTY);
        stubUpdated(UNKNOWN, EMPTY);

        MvcResult result = mockMvc.perform(submit(post(cancelPath(UNKNOWN)), logIn("ADMIN"))).andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(404);
        WIRE_MOCK_SERVER.verify(0, patchRequestedFor(urlPathEqualTo(SESSION_API + UNKNOWN)));
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"no-es-un-uuid", "123", "a1b2c3d4-1d2a-4e3b"})
    @DisplayName("a session id that is not a UUID is a 404, and the API is never asked")
    void malformedSessionId(String id) throws Exception {
        Cookie session = logIn("ADMIN");

        for (MockHttpServletRequestBuilder request : new MockHttpServletRequestBuilder[] {
            get(reschedulePath(id)).cookie(session), get(cancelPath(id)).cookie(session),
            submit(post(reschedulePath(id)), session, "localDate", "2026-11-24", "localTime", "22:00"),
            submit(post(cancelPath(id)), session)}) {
            assertThat(mockMvc.perform(request).andReturn().getResponse().getStatus()).isEqualTo(404);
        }

        WIRE_MOCK_SERVER.verify(0, anyRequestedFor(urlPathEqualTo(SESSIONS_API)));
        WIRE_MOCK_SERVER.verify(0, anyRequestedFor(urlPathEqualTo(COURSES_API)));
    }

    @Test
    @DisplayName("a course id that is not a UUID is a 404 about the course, and the API is never asked")
    void malformedCourseId() throws Exception {
        MvcResult result = mockMvc.perform(get(BASE + "/xyz/sesiones/" + SESSION_A + "/cancelar")
                .cookie(logIn("ADMIN"))).andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(404);
        assertThat(mainOf(result.getResponse().getContentAsString())).contains("No encontramos ese curso.")
                .contains("Volver a los cursos");
        WIRE_MOCK_SERVER.verify(0, anyRequestedFor(urlPathEqualTo(COURSES_API + "/xyz/sessions")));
    }

    @Test
    @DisplayName("an instructor on a course that is not theirs gets the 403 notice on both pages")
    void courseNotOwned() throws Exception {
        stubProblem("GET", SESSIONS_API, 403, "COURSE_NOT_OWNED");
        Cookie session = logIn("INSTRUCTOR");

        for (String path : new String[] {reschedulePath(SESSION_A), cancelPath(SESSION_A)}) {
            MvcResult result = mockMvc.perform(get(path).cookie(session)).andReturn();

            assertThat(result.getResponse().getStatus()).isEqualTo(403);
            assertThat(mainOf(result.getResponse().getContentAsString())).contains("No podés gestionar este curso.")
                    .doesNotContain("secret-detail").doesNotContain("<form");
        }
    }

    @Test
    @DisplayName("a COURSE_NOT_OWNED on the change itself is the 403 notice too")
    void changeRefusedAsNotOwned() throws Exception {
        stubSessions(EMPTY);
        stubProblem("PATCH", SESSION_API + SESSION_A, 403, "COURSE_NOT_OWNED");
        Cookie session = logIn("INSTRUCTOR");

        MvcResult reschedule = mockMvc.perform(submit(post(reschedulePath(SESSION_A)), session,
                "localDate", "2026-11-24", "localTime", "22:00")).andReturn();
        MvcResult cancel = mockMvc.perform(submit(post(cancelPath(SESSION_A)), session)).andReturn();

        for (MvcResult result : new MvcResult[] {reschedule, cancel}) {
            assertThat(result.getResponse().getStatus()).isEqualTo(403);
            assertThat(mainOf(result.getResponse().getContentAsString())).contains("No podés gestionar este curso.");
        }
    }

    @Test
    @DisplayName("a SESSION_NOT_FOUND on the change itself is the 404 notice")
    void changeRefusedAsNotFound() throws Exception {
        stubSessions(EMPTY);
        stubProblem("PATCH", SESSION_API + SESSION_A, 404, "SESSION_NOT_FOUND");
        Cookie session = logIn("ADMIN");

        MvcResult reschedule = mockMvc.perform(submit(post(reschedulePath(SESSION_A)), session,
                "localDate", "2026-11-24", "localTime", "22:00")).andReturn();
        MvcResult cancel = mockMvc.perform(submit(post(cancelPath(SESSION_A)), session)).andReturn();

        for (MvcResult result : new MvcResult[] {reschedule, cancel}) {
            assertThat(result.getResponse().getStatus()).isEqualTo(404);
            assertThat(mainOf(result.getResponse().getContentAsString())).contains("No encontramos esa sesión.");
        }
    }

    @ParameterizedTest(name = "API status {0}")
    @ValueSource(ints = {401, 429, 500, 503})
    @DisplayName("a failing API on the lookup is a 503 notice with no API detail")
    void lookupUnavailable(int apiStatus, CapturedOutput output) throws Exception {
        stubProblem("GET", SESSIONS_API, apiStatus, "SOMETHING_ELSE");
        Cookie session = logIn("ADMIN");

        for (String path : new String[] {reschedulePath(SESSION_A), cancelPath(SESSION_A)}) {
            MvcResult result = mockMvc.perform(get(path).cookie(session)).andReturn();

            assertThat(result.getResponse().getStatus()).isEqualTo(503);
            assertThat(mainOf(result.getResponse().getContentAsString()))
                    .contains("No pudimos cargar la sesión en este momento. Intentá nuevamente en unos minutos.")
                    .doesNotContain("secret-detail").doesNotContain("SOMETHING_ELSE");
        }
        assertThat(output.getAll()).doesNotContain("secret-detail").doesNotContain(TestAccessTokens.withRole("ADMIN"));
    }

    // ---- cancel page ----

    @ParameterizedTest(name = "{0} opens the cancel page")
    @ValueSource(strings = {"ADMIN", "INSTRUCTOR"})
    @DisplayName("the cancel page is a confirmation page, not a modal: the session, a POST form with CSRF and the "
            + "button, and a way back; never cached")
    void cancelForm(String role) throws Exception {
        stubSessions(EMPTY);

        MvcResult result = mockMvc.perform(get(cancelPath(SESSION_A)).cookie(logIn(role)))
                .andExpect(status().isOk()).andReturn();
        String html = result.getResponse().getContentAsString();
        String main = mainOf(html);

        assertThat(result.getResponse().getHeader("Cache-Control")).contains(NO_STORE);
        assertThat(html).contains("<title>Cancelar sesión - Menta Dance</title>");
        assertThat(main).containsPattern("<h1[^>]*>\\s*Cancelar sesión\\s*</h1>")
                .contains("<strong>martes 17 de noviembre a las 22:00</strong>")
                .containsPattern("<form[^>]*action=\"" + cancelPath(SESSION_A) + "\"[^>]*method=\"post\"")
                .containsPattern("<input[^>]*type=\"hidden\"[^>]*name=\"_csrf\"")
                .containsPattern("<button[^>]*type=\"submit\"[^>]*>\\s*Sí, cancelar la sesión\\s*</button>")
                .containsPattern("<a href=\"" + SESSIONS + "\">Volver</a>")
                .doesNotContain("alumnos asignados").doesNotContain("<script").doesNotContain("dialog");
    }

    @Test
    @DisplayName("a session with spots taken warns on the cancel page too")
    void cancelWarnsWhenSpotsAreTaken() throws Exception {
        stubSessions(TAKEN);

        String main = mainOfGet(cancelPath(SESSION_FULL), logIn("ADMIN"));

        assertThat(main).containsPattern("<[^>]*role=\"status\"[^>]*>\\s*<p>Esta sesión ya tiene 3 alumnos asignados."
                + "</p>").contains("Sí, cancelar la sesión");
    }

    @Test
    @DisplayName("a cancelled session shows a notice instead of the form")
    void cancelOfACancelledSession() throws Exception {
        stubSessions(CANCELLED);

        String main = mainOfGet(cancelPath(SESSION_B), logIn("ADMIN"));

        assertThat(main).contains("Esta sesión ya está cancelada.").doesNotContain("<form")
                .doesNotContain("Sí, cancelar la sesión").contains("Volver a las sesiones");
    }

    @Test
    @DisplayName("cancels with only the status in the request, and redirects to the sessions with the "
            + "confirmation once")
    void cancels() throws Exception {
        stubSessions(EMPTY);
        stubUpdated(SESSION_A, CANCELLED);
        Cookie session = logIn("ADMIN");

        MvcResult result = mockMvc.perform(submit(post(cancelPath(SESSION_A)), session))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl(SESSIONS)).andReturn();

        assertThat(result.getResponse().getHeader("Cache-Control")).contains(NO_STORE);
        WIRE_MOCK_SERVER.verify(1, patchRequestedFor(urlPathEqualTo(SESSION_API + SESSION_A))
                .withHeader("Authorization", equalTo("Bearer " + TestAccessTokens.withRole("ADMIN")))
                .withRequestBody(equalToJson("{\"status\":\"CANCELLED\"}")));
        stubPageForList();
        String first = mainOfGet(SESSIONS, session);
        String second = mainOfGet(SESSIONS, session);
        assertThat(first).containsPattern("<[^>]*role=\"status\"[^>]*>\\s*<p>Cancelamos la sesión.</p>");
        assertThat(second).doesNotContain("Cancelamos la sesión");
    }

    @Test
    @DisplayName("posting the cancellation of a cancelled session shows the notice with a 409, and sends nothing")
    void cancelPostOfACancelledSession() throws Exception {
        stubSessions(CANCELLED);
        stubUpdated(SESSION_B, CANCELLED);

        MvcResult result = mockMvc.perform(submit(post(cancelPath(SESSION_B)), logIn("ADMIN"))).andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(409);
        assertThat(mainOf(result.getResponse().getContentAsString())).contains("Esta sesión ya está cancelada.");
        WIRE_MOCK_SERVER.verify(0, patchRequestedFor(urlPathEqualTo(SESSION_API + SESSION_B)));
    }

    @Test
    @DisplayName("a session that already took place says so on the confirmation page, with a 409, and keeps the form")
    void cancelAlreadyOccurred() throws Exception {
        stubSessions(EMPTY);
        stubProblem("PATCH", SESSION_API + SESSION_A, 409, "SESSION_ALREADY_OCCURRED");

        MvcResult result = mockMvc.perform(submit(post(cancelPath(SESSION_A)), logIn("ADMIN"))).andReturn();
        String main = mainOf(result.getResponse().getContentAsString());

        assertThat(result.getResponse().getStatus()).isEqualTo(409);
        assertThat(main).containsPattern("<[^>]*role=\"alert\"[^>]*>\\s*<p>No se puede cancelar una sesión que ya "
                        + "ocurrió.</p>")
                .contains("martes 17 de noviembre a las 22:00").doesNotContain("secret-detail");
    }

    @Test
    @DisplayName("any other refusal of the API on a cancellation is a general error on the page")
    void cancelRefused() throws Exception {
        stubSessions(EMPTY);
        stubProblem("PATCH", SESSION_API + SESSION_A, 400, "INVALID_REQUEST");

        MvcResult result = mockMvc.perform(submit(post(cancelPath(SESSION_A)), logIn("ADMIN"))).andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        assertThat(mainOf(result.getResponse().getContentAsString())).contains("No pudimos cancelar la sesión. "
                + "Intentá de nuevo.");
    }

    @ParameterizedTest(name = "API status {0}")
    @ValueSource(ints = {401, 429, 500, 503})
    @DisplayName("a failing API on the cancellation is a 503 with a general error, and no detail")
    void cancelUnavailable(int apiStatus, CapturedOutput output) throws Exception {
        stubSessions(EMPTY);
        stubProblem("PATCH", SESSION_API + SESSION_A, apiStatus, "SOMETHING_ELSE");

        MvcResult result = mockMvc.perform(submit(post(cancelPath(SESSION_A)), logIn("ADMIN"))).andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(503);
        assertThat(mainOf(result.getResponse().getContentAsString()))
                .contains("No pudimos cancelar la sesión. Probá de nuevo en unos minutos.")
                .doesNotContain("secret-detail").doesNotContain("SOMETHING_ELSE");
        assertThat(output.getAll()).doesNotContain("secret-detail").doesNotContain(TestAccessTokens.withRole("ADMIN"));
    }

    // ---- the sessions page links here ----

    @Test
    @DisplayName("each scheduled row of the sessions page links to its reschedule and cancel pages; a cancelled "
            + "row has neither")
    void rowActions() throws Exception {
        stubPageForList();
        stubSessions(EMPTY, CANCELLED);

        String main = mainOfGet(SESSIONS, logIn("ADMIN"));

        assertThat(main).containsPattern("<th scope=\"col\"[^>]*>Acciones</th>")
                .contains("<a href=\"" + reschedulePath(SESSION_A) + "\">Reprogramar<span class=\"sr-only\"> la "
                        + "sesión del martes 17 de noviembre a las 22:00</span></a>")
                .contains("<a href=\"" + cancelPath(SESSION_A) + "\">Cancelar<span class=\"sr-only\"> la sesión del "
                        + "martes 17 de noviembre a las 22:00</span></a>")
                .doesNotContain(reschedulePath(SESSION_B)).doesNotContain(cancelPath(SESSION_B))
                .doesNotContain("Sin acciones disponibles");
    }
}
