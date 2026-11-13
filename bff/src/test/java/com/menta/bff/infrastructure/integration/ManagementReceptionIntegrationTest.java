package com.menta.bff.infrastructure.integration;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.matching;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
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
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import jakarta.servlet.http.Cookie;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
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
 * The reception screens (#373): who may open today's sessions, the roster of a session and the manual check-in,
 * what they render, what they send to the Physical API and how each answer comes back. Each case logs in for real
 * against the stubbed Auth API and reuses the session cookie.
 */
@ExtendWith(OutputCaptureExtension.class)
@DisplayName("Management reception Integration Tests")
class ManagementReceptionIntegrationTest extends BaseIntegrationTest {

    private static final String LIST = "/gestion/recepcion";
    private static final String SESSIONS_API = "/api/v1/admin/physical/sessions";
    private static final String SESSION_ID = "5b6f7c8e-1d2a-4e3b-9c4d-0a1b2c3d4e5f";
    private static final String ADA_ID = "0a1b2c3d-1d2a-4e3b-9c4d-5b6f7c8e9f00";
    private static final String JOSE_ID = "9a1b2c3d-1d2a-4e3b-9c4d-5b6f7c8e9f00";
    private static final String ROSTER_URL = LIST + "/sesiones/" + SESSION_ID;
    private static final String CHECK_IN_URL = ROSTER_URL + "/asistencias";
    private static final String ROSTER_API = SESSIONS_API + "/" + SESSION_ID + "/roster";
    private static final String CHECK_IN_API = "/api/v1/physical/sessions/" + SESSION_ID + "/check-ins";
    private static final String NO_STORE = "no-store";
    private static final String UNAVAILABLE_MESSAGE =
            "No pudimos registrar la asistencia. Probá de nuevo en unos minutos.";

    private static String sessionItem(String id, String course, String start, String status, int capacity,
            int occupied) {
        return ("{\"sessionId\":\"%s\",\"courseId\":\"c-1\",\"courseName\":\"%s\",\"scheduledAt\":\"%s\","
                + "\"endsAt\":\"2026-11-13T23:00:00Z\",\"status\":\"%s\",\"capacity\":%d,\"occupied\":%d}")
                .formatted(id, course, start, status, capacity, occupied);
    }

    private static final String SALSA = sessionItem(SESSION_ID, "Salsa", "2026-11-13T21:00:00Z", "SCHEDULED", 15, 12);

    private static String student(String id, String name, String type, String attendance, String at, String kind) {
        return ("{\"studentId\":\"%s\",\"displayName\":\"%s\",\"assignmentType\":\"%s\",\"attendanceStatus\":\"%s\","
                + "\"checkedInAt\":%s,\"checkInKind\":%s}").formatted(id, name, type, attendance,
                at == null ? "null" : "\"" + at + "\"", kind == null ? "null" : "\"" + kind + "\"");
    }

    private static final String ADA_PRESENT =
            student(ADA_ID, "Ada Lovelace", "MONTHLY", "PRESENT", "2026-11-13T21:05:00Z", "QR");
    private static final String JOSE_PENDING = student(JOSE_ID, "José Pérez", "INDIVIDUAL", "PENDING", null, null);

    private static String rosterBody(String status, String... students) {
        return ("{\"session\":{\"sessionId\":\"%s\",\"courseId\":\"c-1\",\"courseName\":\"Salsa\","
                + "\"scheduledAt\":\"2026-11-13T21:00:00Z\",\"status\":\"%s\",\"capacity\":15,\"studentCount\":%d},"
                + "\"students\":[%s]}").formatted(SESSION_ID, status, students.length, String.join(",", students));
    }

    private static void stubAgenda(String... items) {
        WIRE_MOCK_SERVER.stubFor(WireMock.get(urlPathEqualTo(SESSIONS_API)).willReturn(aResponse()
                .withStatus(200).withHeader("Content-Type", "application/json")
                .withBody("{\"sessions\":[%s]}".formatted(String.join(",", items)))));
    }

    private static void stubRoster(String body) {
        WIRE_MOCK_SERVER.stubFor(WireMock.get(urlEqualTo(ROSTER_API)).willReturn(aResponse()
                .withStatus(200).withHeader("Content-Type", "application/json").withBody(body)));
    }

    private static void stubCheckIn(int status, String contentType, String body) {
        WIRE_MOCK_SERVER.stubFor(WireMock.post(urlEqualTo(CHECK_IN_API)).willReturn(aResponse()
                .withStatus(status).withHeader("Content-Type", contentType).withBody(body)));
    }

    private static void stubProblem(int status, String code) {
        stubCheckIn(status, "application/problem+json",
                "{\"type\":\"https://menta.dance/problems/x\",\"status\":%d,\"detail\":\"secret-detail\","
                        .formatted(status) + "\"code\":\"%s\"}".formatted(code));
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

    private static MockHttpServletRequestBuilder checkIn(Cookie session, String url, String studentId) {
        MockHttpServletRequestBuilder request = post(url).cookie(session).with(csrf());
        if (studentId != null) {
            request.param("studentId", studentId);
        }
        return request;
    }

    private String pageOf(Cookie session, String url) throws Exception {
        return mainOf(mockMvc.perform(get(url).cookie(session)).andReturn().getResponse().getContentAsString());
    }

    /** Submits the check-in and then opens the roster the way the browser follows the redirect, flash included. */
    private String rosterAfter(Cookie session, MockHttpServletRequestBuilder submission) throws Exception {
        MvcResult result = mockMvc.perform(submission).andReturn();
        return mainOf(mockMvc.perform(get(result.getResponse().getRedirectedUrl()).cookie(session)
                        .flashAttrs(result.getFlashMap()))
                .andReturn().getResponse().getContentAsString());
    }

    private static void verifyNoApiCall() {
        WIRE_MOCK_SERVER.verify(0, getRequestedFor(urlPathMatching("/api/v1/admin/physical/.*")));
        WIRE_MOCK_SERVER.verify(0, postRequestedFor(urlPathMatching("/api/v1/physical/.*")));
    }

    private static String statusRegionWith(String message) {
        return "<[^>]*role=\"status\"[^>]*>\\s*<p>" + message + "</p>";
    }

    private static String alertWith(String message) {
        return "<[^>]*role=\"alert\"[^>]*>\\s*<p>" + message + "</p>";
    }

    // ---- access ----

    @ParameterizedTest(name = "{0} opens today's sessions")
    @ValueSource(strings = {"RECEPTIONIST", "ADMIN"})
    @DisplayName("the receptionist and the administrator open the three reception screens")
    void receptionAndAdminAreAllowed(String role) throws Exception {
        stubAgenda(SALSA);
        stubRoster(rosterBody("SCHEDULED", JOSE_PENDING));
        Cookie session = logIn(role);

        mockMvc.perform(get(LIST).cookie(session)).andExpect(status().isOk());
        mockMvc.perform(get(ROSTER_URL).cookie(session)).andExpect(status().isOk());
    }

    @ParameterizedTest(name = "{0} is refused")
    @ValueSource(strings = {"INSTRUCTOR", "STUDENT"})
    @DisplayName("every other role gets a 403 on every reception screen, and the API is never asked")
    void otherRolesAreRefused(String role) throws Exception {
        stubAgenda(SALSA);
        stubRoster(rosterBody("SCHEDULED", JOSE_PENDING));
        stubCheckIn(201, "application/json", "{}");
        Cookie session = logIn(role);

        mockMvc.perform(get(LIST).cookie(session)).andExpect(status().isForbidden());
        mockMvc.perform(get(ROSTER_URL).cookie(session)).andExpect(status().isForbidden());
        mockMvc.perform(checkIn(session, CHECK_IN_URL, JOSE_ID)).andExpect(status().isForbidden());

        verifyNoApiCall();
    }

    @Test
    @DisplayName("an anonymous visitor is sent to the login page on every reception route, and the API is not asked")
    void anonymousGoesToLogin() throws Exception {
        mockMvc.perform(get(LIST)).andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));
        mockMvc.perform(get(ROSTER_URL)).andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));
        mockMvc.perform(post(CHECK_IN_URL).with(csrf()).param("studentId", JOSE_ID))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrlPattern("**/login"));

        verifyNoApiCall();
    }

    @ParameterizedTest(name = "{0} without a CSRF token is refused")
    @ValueSource(strings = {"RECEPTIONIST", "ADMIN"})
    @DisplayName("a check-in without a CSRF token is refused even for the roles allowed, and the API is not asked")
    void checkInNeedsCsrf(String role) throws Exception {
        stubCheckIn(201, "application/json", "{}");

        mockMvc.perform(post(CHECK_IN_URL).cookie(logIn(role)).param("studentId", JOSE_ID))
                .andExpect(status().isForbidden());

        verifyNoApiCall();
    }

    // ---- today's sessions ----

    @Test
    @DisplayName("lists today's sessions in a captioned table: course, time in the academy's zone, spots, state")
    void listRendersTheAgenda() throws Exception {
        stubAgenda(SALSA, sessionItem("1b6f7c8e-1d2a-4e3b-9c4d-0a1b2c3d4e5f", "Bachata", "2026-11-13T23:15:00Z",
                "CANCELLED", 10, 0));

        MvcResult result = mockMvc.perform(get(LIST).cookie(logIn("RECEPTIONIST"))).andReturn();
        String main = mainOf(result.getResponse().getContentAsString());

        assertThat(result.getResponse().getHeader("Cache-Control")).contains(NO_STORE);
        assertThat(main).containsPattern("<h1[^>]*>\\s*Recepción\\s*</h1>")
                .containsPattern("<caption[^>]*>\\s*Sesiones de hoy \\(<span>2</span>\\)\\s*</caption>")
                .containsPattern("<th[^>]*scope=\"col\"[^>]*>\\s*Cupo ocupado\\s*</th>")
                .containsPattern("<th[^>]*scope=\"row\"[^>]*>Salsa</th>")
                .containsPattern("<time datetime=\"2026-11-13T21:00:00Z\">18:00</time>")
                .containsPattern("<time datetime=\"2026-11-13T23:15:00Z\">20:15</time>")
                .contains(">12 de 15<")
                .containsPattern("<span class=\"badge badge-success\">Programada</span>")
                .containsPattern("<span class=\"badge badge-danger\">Cancelada</span>")
                .containsPattern("<a href=\"" + ROSTER_URL + "\">Ver alumnos<span class=\"sr-only\"> de Salsa, 18:00"
                        + "</span></a>");
    }

    @Test
    @DisplayName("asks the API for the local day as a UTC range: from 03:00Z to 03:00Z the next day, with the token")
    void listSendsTheLocalDayAsUtc() throws Exception {
        stubAgenda();

        mockMvc.perform(get(LIST).cookie(logIn("RECEPTIONIST"))).andExpect(status().isOk());

        String utcDayStart = "\\d{4}-\\d{2}-\\d{2}T03:00:00Z";
        WIRE_MOCK_SERVER.verify(1, getRequestedFor(urlPathEqualTo(SESSIONS_API))
                .withQueryParam("from", matching(utcDayStart))
                .withQueryParam("to", matching(utcDayStart))
                .withHeader("Authorization", equalTo("Bearer " + TestAccessTokens.withRole("RECEPTIONIST"))));
        List<LoggedRequest> calls = WIRE_MOCK_SERVER.findAll(getRequestedFor(urlPathEqualTo(SESSIONS_API)));
        Instant from = Instant.parse(calls.get(0).queryParameter("from").firstValue());
        Instant to = Instant.parse(calls.get(0).queryParameter("to").firstValue());
        assertThat(Duration.between(from, to)).isEqualTo(Duration.ofHours(24));
    }

    @Test
    @DisplayName("the navigation offers Recepción and marks it as the current page; Usuarios is not offered to it")
    void navigationOffersReception() throws Exception {
        stubAgenda(SALSA);

        String main = pageOf(logIn("RECEPTIONIST"), LIST);

        assertThat(main).containsPattern("<a[^>]*href=\"/gestion/recepcion\"[^>]*aria-current=\"page\"[^>]*>"
                        + "\\s*Recepción\\s*</a>")
                .doesNotContain("/gestion/usuarios");
    }

    @Test
    @DisplayName("a day without sessions says so in a status region")
    void listWithoutSessions() throws Exception {
        stubAgenda();

        String main = pageOf(logIn("ADMIN"), LIST);

        assertThat(main).containsPattern(statusRegionWith("Hoy no hay sesiones programadas\\."))
                .doesNotContain("<table");
    }

    @ParameterizedTest(name = "agenda answering {0} degrades the page")
    @ValueSource(ints = {400, 401, 403, 429, 500, 503})
    @DisplayName("a failing API renders the page's own notice with a 503, and nothing of the reply")
    void listDegradesWhenTheApiFails(int apiStatus) throws Exception {
        WIRE_MOCK_SERVER.stubFor(WireMock.get(urlPathEqualTo(SESSIONS_API)).willReturn(aResponse()
                .withStatus(apiStatus).withHeader("Content-Type", "application/problem+json")
                .withBody("{\"code\":\"X\",\"detail\":\"secret-detail\"}")));

        MvcResult result = mockMvc.perform(get(LIST).cookie(logIn("RECEPTIONIST")))
                .andExpect(status().isServiceUnavailable()).andReturn();
        String html = result.getResponse().getContentAsString();

        assertThat(mainOf(html)).containsPattern(alertWith(
                "No pudimos cargar las sesiones de hoy en este momento\\. Intentá nuevamente en unos minutos\\."));
        assertThat(html).doesNotContain("secret-detail").doesNotContain("/api/v1");
    }

    @Test
    @DisplayName("a dropped connection to the API also degrades the page")
    void listDegradesOnTransportError() throws Exception {
        WIRE_MOCK_SERVER.stubFor(WireMock.get(urlPathEqualTo(SESSIONS_API))
                .willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)));

        mockMvc.perform(get(LIST).cookie(logIn("RECEPTIONIST"))).andExpect(status().isServiceUnavailable());
    }

    // ---- roster ----

    @Test
    @DisplayName("the roster shows the header, who checked in and who is pending as text, and offers the button "
            + "only for the pending ones, with the student in its accessible name")
    void rosterRenders() throws Exception {
        stubRoster(rosterBody("SCHEDULED", ADA_PRESENT, JOSE_PENDING));

        MvcResult result = mockMvc.perform(get(ROSTER_URL).cookie(logIn("RECEPTIONIST"))).andReturn();
        String main = mainOf(result.getResponse().getContentAsString());

        assertThat(result.getResponse().getHeader("Cache-Control")).contains(NO_STORE);
        assertThat(main).containsPattern("<h1[^>]*>Salsa</h1>")
                .contains("viernes 13 de noviembre a las 18:00")
                .containsPattern("<dt[^>]*>Cupo total</dt>\\s*<dd[^>]*>15</dd>")
                .containsPattern("<dt[^>]*>Alumnos con cupo</dt>\\s*<dd[^>]*>2</dd>")
                .containsPattern("<dt[^>]*>Con asistencia registrada</dt>\\s*<dd[^>]*>1</dd>")
                .containsPattern("<caption[^>]*>\\s*Alumnos con cupo \\(<span>2</span>\\)\\s*</caption>")
                .containsPattern("<th[^>]*scope=\"col\"[^>]*>\\s*Asistencia\\s*</th>")
                .containsPattern("<th[^>]*scope=\"row\"[^>]*>Ada Lovelace</th>")
                .containsPattern("<th[^>]*scope=\"row\"[^>]*>José Pérez</th>")
                .contains("Plan mensual").contains("Clase individual")
                .containsPattern("<span class=\"badge badge-success\">Presente</span>\\s*<span>18:05, QR</span>")
                .containsPattern("<span class=\"badge badge-warning\">Pendiente</span>")
                .containsPattern("<a[^>]*href=\"" + LIST + "\"[^>]*>Volver a las sesiones de hoy</a>");
        assertThat(main.split("action=\"" + CHECK_IN_URL + "\"", -1)).as("one form: only the pending student")
                .hasSize(2);
        assertThat(main).containsPattern("<input[^>]*type=\"hidden\"[^>]*name=\"studentId\"[^>]*value=\""
                        + JOSE_ID + "\"")
                .doesNotContain("value=\"" + ADA_ID + "\"")
                .containsPattern("<button[^>]*type=\"submit\"[^>]*>\\s*Registrar asistencia"
                        + "<span class=\"sr-only\"> de José Pérez</span>\\s*</button>");
        assertThat(main.split("name=\"_csrf\"", -1).length - 1).as("CSRF field in the check-in form").isEqualTo(1);
        WIRE_MOCK_SERVER.verify(1, getRequestedFor(urlEqualTo(ROSTER_API))
                .withHeader("Authorization", equalTo("Bearer " + TestAccessTokens.withRole("RECEPTIONIST"))));
    }

    @Test
    @DisplayName("a check-in done by hand is worded as manual in the roster")
    void rosterWordsManualCheckIns() throws Exception {
        stubRoster(rosterBody("SCHEDULED",
                student(ADA_ID, "Ada Lovelace", "MONTHLY", "PRESENT", "2026-11-13T21:10:00Z", "MANUAL")));

        assertThat(pageOf(logIn("ADMIN"), ROSTER_URL)).contains("<span>18:10, manual</span>");
    }

    @Test
    @DisplayName("the search ignores case and accents and keeps the totals; the form is a labelled search")
    void rosterSearchFilters() throws Exception {
        stubRoster(rosterBody("SCHEDULED", ADA_PRESENT, JOSE_PENDING));

        String main = pageOf(logIn("RECEPTIONIST"), ROSTER_URL + "?q=JOSE");

        assertThat(main).contains("José Pérez").doesNotContain("Ada Lovelace")
                .containsPattern("<caption[^>]*>\\s*Alumnos con cupo \\(<span>1</span><span>\\s*de 2</span>\\)")
                .containsPattern("<dd[^>]*>2</dd>")
                .containsPattern("<form[^>]*role=\"search\"[^>]*aria-label=\"Buscar alumnos de la sesión\"")
                .containsPattern("<label[^>]*for=\"q\"[^>]*>Buscar alumno por nombre</label>")
                .containsPattern("<input[^>]*id=\"q\"[^>]*type=\"search\"[^>]*name=\"q\"[^>]*value=\"JOSE\"")
                .containsPattern("<input[^>]*type=\"hidden\"[^>]*name=\"q\"[^>]*value=\"JOSE\"");
    }

    @Test
    @DisplayName("the search text is echoed escaped and the names are escaped too")
    void rosterEscapesEverything() throws Exception {
        stubRoster(rosterBody("SCHEDULED", student(JOSE_ID, "<b>Zoe</b>", "MONTHLY", "PENDING", null, null)));

        Cookie session = logIn("RECEPTIONIST");

        String searched = pageOf(session, ROSTER_URL + "?q=\"><script>x</script>");
        String listed = pageOf(session, ROSTER_URL);

        assertThat(searched).doesNotContain("<script>x</script>")
                .contains("value=\"&quot;&gt;&lt;script&gt;x&lt;/script&gt;\"");
        assertThat(listed).contains("&lt;b&gt;Zoe&lt;/b&gt;").doesNotContain("<b>Zoe</b>");
    }

    @Test
    @DisplayName("a search without matches says so in a status region and shows no table")
    void rosterSearchWithoutMatches() throws Exception {
        stubRoster(rosterBody("SCHEDULED", ADA_PRESENT, JOSE_PENDING));

        String main = pageOf(logIn("RECEPTIONIST"), ROSTER_URL + "?q=zzz");

        assertThat(main).containsPattern(statusRegionWith("Ningún alumno coincide con la búsqueda\\."))
                .doesNotContain("<table");
    }

    @Test
    @DisplayName("a session without students says so")
    void rosterWithoutStudents() throws Exception {
        stubRoster(rosterBody("SCHEDULED"));

        String main = pageOf(logIn("RECEPTIONIST"), ROSTER_URL);

        assertThat(main).containsPattern(statusRegionWith("Esta sesión no tiene alumnos con cupo confirmado\\."))
                .doesNotContain("<table");
    }

    @Test
    @DisplayName("a cancelled session says so and offers no check-in button")
    void cancelledSessionOffersNoCheckIn() throws Exception {
        stubRoster(rosterBody("CANCELLED", JOSE_PENDING));

        String main = pageOf(logIn("RECEPTIONIST"), ROSTER_URL);

        assertThat(main)
                .containsPattern(statusRegionWith("La sesión está cancelada: no se puede registrar asistencia\\."))
                .contains("José Pérez").contains("Pendiente")
                .doesNotContain("Registrar asistencia")
                .contains("Sin acción disponible");
    }

    @Test
    @DisplayName("a session the API does not know is a 404 with the page's own notice")
    void rosterOfUnknownSession() throws Exception {
        WIRE_MOCK_SERVER.stubFor(WireMock.get(urlEqualTo(ROSTER_API)).willReturn(aResponse()
                .withStatus(404).withHeader("Content-Type", "application/problem+json")
                .withBody("{\"code\":\"SESSION_NOT_FOUND\",\"detail\":\"secret-detail\"}")));

        MvcResult result = mockMvc.perform(get(ROSTER_URL).cookie(logIn("RECEPTIONIST")))
                .andExpect(status().isNotFound()).andReturn();
        String html = result.getResponse().getContentAsString();

        assertThat(mainOf(html)).containsPattern(alertWith("No encontramos esa sesión\\."))
                .contains("Volver a las sesiones de hoy");
        assertThat(html).doesNotContain("secret-detail");
    }

    @ParameterizedTest(name = "[{index}] \"{0}\" is not a session")
    @ValueSource(strings = {"not-a-uuid", "5b6f7c8e1d2a4e3b9c4d0a1b2c3d4e5f", "<script>", "a b"})
    @DisplayName("a session id that is not a UUID is a 404 and the API is not asked")
    void malformedSessionIdIsNotFound(String id) throws Exception {
        String html = mockMvc.perform(get(LIST + "/sesiones/{id}", id).cookie(logIn("RECEPTIONIST")))
                .andExpect(status().isNotFound()).andReturn().getResponse().getContentAsString();

        assertThat(mainOf(html)).containsPattern(alertWith("No encontramos esa sesión\\."));
        assertThat(html).doesNotContain("<script>");
        verifyNoApiCall();
    }

    @ParameterizedTest(name = "roster answering {0} degrades the page")
    @ValueSource(ints = {400, 401, 403, 500, 503})
    @DisplayName("a failing API renders the page's own notice with a 503, and nothing of the reply")
    void rosterDegradesWhenTheApiFails(int apiStatus) throws Exception {
        WIRE_MOCK_SERVER.stubFor(WireMock.get(urlEqualTo(ROSTER_API)).willReturn(aResponse()
                .withStatus(apiStatus).withHeader("Content-Type", "application/problem+json")
                .withBody("{\"code\":\"X\",\"detail\":\"secret-detail\"}")));

        String html = mockMvc.perform(get(ROSTER_URL).cookie(logIn("RECEPTIONIST")))
                .andExpect(status().isServiceUnavailable()).andReturn().getResponse().getContentAsString();

        assertThat(mainOf(html)).containsPattern(alertWith(
                "No pudimos cargar los alumnos de la sesión en este momento\\. Intentá nuevamente en unos minutos\\."));
        assertThat(html).doesNotContain("secret-detail").doesNotContain("/api/v1");
    }

    // ---- manual check-in ----

    @Test
    @DisplayName("registers the student with the receptionist's token and redirects to the roster, nothing in the URL")
    void registersTheAttendance() throws Exception {
        stubCheckIn(201, "application/json",
                "{\"attendanceId\":\"7a1b2c3d-1d2a-4e3b-9c4d-5b6f7c8e9f00\","
                        + "\"recordedAt\":\"2026-11-13T21:05:00Z\",\"kind\":\"MANUAL\"}");

        MvcResult result = mockMvc.perform(checkIn(logIn("RECEPTIONIST"), CHECK_IN_URL, JOSE_ID))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl(ROSTER_URL)).andReturn();

        assertThat(result.getResponse().getHeader("Cache-Control")).contains(NO_STORE);
        WIRE_MOCK_SERVER.verify(1, postRequestedFor(urlEqualTo(CHECK_IN_API))
                .withHeader("Authorization", equalTo("Bearer " + TestAccessTokens.withRole("RECEPTIONIST")))
                .withRequestBody(equalToJson("{\"type\":\"MANUAL\",\"studentId\":\"" + JOSE_ID + "\"}")));
    }

    @Test
    @DisplayName("the roster confirms the registration once, in a status region, and not on the next visit")
    void rosterConfirmsOnce() throws Exception {
        stubCheckIn(201, "application/json",
                "{\"attendanceId\":\"a\",\"recordedAt\":\"2026-11-13T21:05:00Z\",\"kind\":\"MANUAL\"}");
        stubRoster(rosterBody("SCHEDULED", ADA_PRESENT, JOSE_PENDING));
        Cookie session = logIn("ADMIN");

        String first = rosterAfter(session, checkIn(session, CHECK_IN_URL, JOSE_ID));
        String second = pageOf(session, ROSTER_URL);

        assertThat(first).containsPattern(statusRegionWith("Registramos la asistencia\\."))
                .doesNotContain("role=\"alert\"");
        assertThat(second).doesNotContain("Registramos la asistencia");
    }

    @Test
    @DisplayName("a repeated registration is not an error: the 200 shows the attendance that existed, not duplicated")
    void repeatedRegistrationShowsTheExistingAttendance() throws Exception {
        stubCheckIn(200, "application/json",
                "{\"attendanceId\":\"a\",\"recordedAt\":\"2026-11-13T21:02:00Z\",\"kind\":\"QR\"}");
        stubRoster(rosterBody("SCHEDULED", ADA_PRESENT));
        Cookie session = logIn("RECEPTIONIST");

        String main = rosterAfter(session, checkIn(session, CHECK_IN_URL, ADA_ID));

        assertThat(main).containsPattern(statusRegionWith(
                        "La asistencia ya estaba registrada \\(QR, viernes 13 de noviembre a las 18:02\\)\\. "
                                + "No se duplicó\\."))
                .doesNotContain("role=\"alert\"");
        WIRE_MOCK_SERVER.verify(1, postRequestedFor(urlEqualTo(CHECK_IN_API)));
    }

    @Test
    @DisplayName("a student without a confirmed spot gets its own message in an alert")
    void studentWithoutSpot() throws Exception {
        stubProblem(403, "CAPACITY_ASSIGNMENT_REQUIRED");
        stubRoster(rosterBody("SCHEDULED", JOSE_PENDING));
        Cookie session = logIn("RECEPTIONIST");

        String main = rosterAfter(session, checkIn(session, CHECK_IN_URL, JOSE_ID));

        assertThat(main).containsPattern(alertWith("El alumno no tiene un cupo confirmado en esta sesión, "
                        + "así que no se puede registrar su asistencia\\."))
                .doesNotContain("secret-detail").doesNotContain("CAPACITY_ASSIGNMENT_REQUIRED");
    }

    @Test
    @DisplayName("a session outside its check-in window gets its own message in an alert")
    void sessionOutsideTheWindow() throws Exception {
        stubProblem(403, "OUTSIDE_CHECK_IN_WINDOW");
        stubRoster(rosterBody("SCHEDULED", JOSE_PENDING));
        Cookie session = logIn("RECEPTIONIST");

        String main = rosterAfter(session, checkIn(session, CHECK_IN_URL, JOSE_ID));

        assertThat(main).containsPattern(alertWith(
                        "La sesión está fuera de la ventana de check-in: no se puede registrar la asistencia\\."))
                .doesNotContain("OUTSIDE_CHECK_IN_WINDOW");
    }

    @Test
    @DisplayName("a cancelled session gets its own message in an alert")
    void cancelledSession() throws Exception {
        stubProblem(409, "SESSION_NOT_ACTIVE");
        stubRoster(rosterBody("CANCELLED", JOSE_PENDING));
        Cookie session = logIn("RECEPTIONIST");

        String main = rosterAfter(session, checkIn(session, CHECK_IN_URL, JOSE_ID));

        assertThat(main).containsPattern(alertWith("La sesión está cancelada: no admite asistencias\\."));
    }

    @ParameterizedTest(name = "{0} {1} gets the generic message")
    @CsvSource({
        "403, INSUFFICIENT_ROLE",
        "409, ALREADY_PROCESSING",
        "404, STUDENT_NOT_FOUND",
        "404, SESSION_NOT_FOUND",
        "400, INVALID_REQUEST",
        "401, INVALID_DEVICE_TOKEN",
        "429, CHECK_IN_RATE_LIMITED",
        "500, INTERNAL_ERROR",
        "503, CHECK_IN_DEGRADED"})
    @DisplayName("any other failure is the generic service-unavailable message, with nothing of the reply")
    void otherFailuresAreGeneric(int apiStatus, String code, CapturedOutput output) throws Exception {
        stubProblem(apiStatus, code);
        stubRoster(rosterBody("SCHEDULED", JOSE_PENDING));
        Cookie session = logIn("RECEPTIONIST");

        String main = rosterAfter(session, checkIn(session, CHECK_IN_URL, JOSE_ID));

        assertThat(main).containsPattern(alertWith(UNAVAILABLE_MESSAGE))
                .doesNotContain("secret-detail").doesNotContain(code);
        assertThat(output.getAll()).doesNotContain("secret-detail").doesNotContain(JOSE_ID)
                .doesNotContain(TestAccessTokens.withRole("RECEPTIONIST"));
    }

    @Test
    @DisplayName("a dropped connection is the generic message too")
    void transportErrorIsGeneric() throws Exception {
        WIRE_MOCK_SERVER.stubFor(WireMock.post(urlEqualTo(CHECK_IN_API))
                .willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)));
        stubRoster(rosterBody("SCHEDULED", JOSE_PENDING));
        Cookie session = logIn("RECEPTIONIST");

        assertThat(rosterAfter(session, checkIn(session, CHECK_IN_URL, JOSE_ID)))
                .containsPattern(alertWith(UNAVAILABLE_MESSAGE));
    }

    @ParameterizedTest(name = "[{index}] student id \"{0}\" is refused")
    @ValueSource(strings = {"not-a-uuid", "0a1b2c3d-1d2a-4e3b-9c4d", "0a1b2c3d-1d2a-4e3b-9c4d-5b6f7c8e9f00;drop"})
    @DisplayName("a student id that is not a UUID is refused before the API, back on the roster with a message")
    void malformedStudentId(String studentId) throws Exception {
        stubRoster(rosterBody("SCHEDULED", JOSE_PENDING));
        Cookie session = logIn("RECEPTIONIST");

        mockMvc.perform(checkIn(session, CHECK_IN_URL, studentId))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl(LIST));

        WIRE_MOCK_SERVER.verify(0, postRequestedFor(urlPathMatching("/api/v1/physical/.*")));
    }

    @Test
    @DisplayName("a missing student id is refused too, and the list shows the message")
    void missingStudentId() throws Exception {
        stubAgenda(SALSA);
        Cookie session = logIn("RECEPTIONIST");

        String main = rosterAfter(session, checkIn(session, CHECK_IN_URL, null));

        assertThat(main).containsPattern(alertWith("Revisá los datos e intentá de nuevo\\."));
        WIRE_MOCK_SERVER.verify(0, postRequestedFor(urlPathMatching("/api/v1/physical/.*")));
    }

    @ParameterizedTest(name = "[{index}] session id \"{0}\" goes back to the list")
    @ValueSource(strings = {"not-a-uuid", "<script>", "a b"})
    @DisplayName("a session id that is not a UUID goes back to today's sessions, never into the redirect")
    void malformedSessionIdGoesBackToTheList(String id) throws Exception {
        mockMvc.perform(post(LIST + "/sesiones/{id}/asistencias", id).cookie(logIn("RECEPTIONIST")).with(csrf())
                        .param("studentId", JOSE_ID))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl(LIST));

        verifyNoApiCall();
    }

    @Test
    @DisplayName("the redirect keeps the search of the roster, encoded strictly: it cannot add parameters or a path")
    void redirectKeepsTheSearch() throws Exception {
        stubCheckIn(201, "application/json",
                "{\"attendanceId\":\"a\",\"recordedAt\":\"2026-11-13T21:05:00Z\",\"kind\":\"MANUAL\"}");
        Cookie session = logIn("RECEPTIONIST");

        mockMvc.perform(checkIn(session, CHECK_IN_URL, JOSE_ID).param("q", "josé"))
                .andExpect(redirectedUrl(ROSTER_URL + "?q=jos%C3%A9"));
        mockMvc.perform(checkIn(session, CHECK_IN_URL, JOSE_ID).param("q", "a&studentId=x#y b+c=d/../"))
                .andExpect(redirectedUrl(ROSTER_URL + "?q=a%26studentId%3Dx%23y%20b%2Bc%3Dd%2F..%2F"));
        mockMvc.perform(checkIn(session, CHECK_IN_URL, JOSE_ID).param("q", "x".repeat(101)))
                .andExpect(redirectedUrl(ROSTER_URL));
        mockMvc.perform(checkIn(session, CHECK_IN_URL, JOSE_ID).param("q", "   "))
                .andExpect(redirectedUrl(ROSTER_URL));
    }

    @Test
    @DisplayName("the upper-case form of the session id is accepted and redirected in its canonical form")
    void sessionIdIsCanonicalInTheRedirect() throws Exception {
        stubCheckIn(201, "application/json",
                "{\"attendanceId\":\"a\",\"recordedAt\":\"2026-11-13T21:05:00Z\",\"kind\":\"MANUAL\"}");

        mockMvc.perform(checkIn(logIn("ADMIN"), LIST + "/sesiones/" + SESSION_ID.toUpperCase() + "/asistencias",
                        JOSE_ID.toUpperCase()))
                .andExpect(redirectedUrl(ROSTER_URL));

        WIRE_MOCK_SERVER.verify(1, postRequestedFor(urlEqualTo(CHECK_IN_API))
                .withRequestBody(equalToJson("{\"type\":\"MANUAL\",\"studentId\":\"" + JOSE_ID + "\"}")));
    }
}
