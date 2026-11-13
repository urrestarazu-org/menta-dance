package com.menta.bff.infrastructure.adapter;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.tomakehurst.wiremock.http.Fault;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import com.menta.bff.application.dto.CheckInReceipt;
import com.menta.bff.application.dto.RosterStudent;
import com.menta.bff.application.dto.SessionRoster;
import com.menta.bff.application.dto.StaffSession;
import com.menta.bff.application.port.out.PhysicalReceptionApiClient;
import com.menta.bff.application.port.out.PhysicalReceptionApiClient.CheckInRejectedException;
import com.menta.bff.application.port.out.PhysicalReceptionApiClient.CheckInRejectedException.Reason;
import com.menta.bff.infrastructure.config.VirtualApiProperties;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.web.reactive.function.client.WebClient;

@WireMockTest
@DisplayName("PhysicalReceptionApiAdapter")
class PhysicalReceptionApiAdapterTest {

    private static final String SESSION_ID = "5b6f7c8e-1d2a-4e3b-9c4d-0a1b2c3d4e5f";
    private static final String STUDENT_ID = "0a1b2c3d-1d2a-4e3b-9c4d-5b6f7c8e9f00";
    private static final String SESSIONS_PATH = "/api/v1/admin/physical/sessions";
    private static final String ROSTER_PATH = SESSIONS_PATH + "/" + SESSION_ID + "/roster";
    private static final String CHECK_INS_PATH = "/api/v1/physical/sessions/" + SESSION_ID + "/check-ins";
    private static final String TOKEN = "access-token-abc";
    private static final Instant FROM = Instant.parse("2026-11-13T03:00:00Z");
    private static final Instant TO = Instant.parse("2026-11-14T03:00:00Z");

    private static final String SESSIONS_BODY =
            """
            {"sessions": [
              {"sessionId": "5b6f7c8e-1d2a-4e3b-9c4d-0a1b2c3d4e5f", "courseId": "c-1", "courseName": "Salsa",
               "scheduledAt": "2026-11-13T21:00:00Z", "endsAt": "2026-11-13T22:00:00Z", "status": "SCHEDULED",
               "capacity": 15, "occupied": 12},
              {"sessionId": "1b6f7c8e-1d2a-4e3b-9c4d-0a1b2c3d4e5f", "courseId": "c-2", "courseName": "Bachata",
               "scheduledAt": "2026-11-13T23:00:00Z", "endsAt": "2026-11-14T00:00:00Z", "status": "CANCELLED",
               "capacity": 10, "occupied": 0}]}
            """;

    private static final String ROSTER_BODY =
            """
            {"session": {"sessionId": "5b6f7c8e-1d2a-4e3b-9c4d-0a1b2c3d4e5f", "courseId": "c-1",
               "courseName": "Salsa", "scheduledAt": "2026-11-13T21:00:00Z", "status": "SCHEDULED",
               "capacity": 15, "studentCount": 2},
             "students": [
              {"studentId": "0a1b2c3d-1d2a-4e3b-9c4d-5b6f7c8e9f00", "displayName": "Ada Lovelace",
               "assignmentType": "MONTHLY", "attendanceStatus": "PRESENT",
               "checkedInAt": "2026-11-13T21:02:00Z", "checkInKind": "QR"},
              {"studentId": "9a1b2c3d-1d2a-4e3b-9c4d-5b6f7c8e9f00", "displayName": "Grace Hopper",
               "assignmentType": "INDIVIDUAL", "attendanceStatus": "PENDING",
               "checkedInAt": null, "checkInKind": null}]}
            """;

    private static final String CHECK_IN_BODY =
            """
            {"attendanceId": "7a1b2c3d-1d2a-4e3b-9c4d-5b6f7c8e9f00", "recordedAt": "2026-11-13T21:05:00Z",
             "kind": "MANUAL"}
            """;

    /** Generous on purpose: the first request of a cold JVM on a slow CI runner can take over a second. */
    private static final Duration GENEROUS_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration SHORT_TIMEOUT = Duration.ofMillis(300);

    private PhysicalReceptionApiAdapter adapter;

    @BeforeEach
    void setUp(WireMockRuntimeInfo wireMock) {
        adapter = adapterWith(wireMock, GENEROUS_TIMEOUT);
    }

    private static PhysicalReceptionApiAdapter adapterWith(WireMockRuntimeInfo wireMock, Duration timeout) {
        VirtualApiProperties properties = new VirtualApiProperties();
        properties.setBaseUrl(wireMock.getHttpBaseUrl());
        properties.setTimeout(timeout);
        return new PhysicalReceptionApiAdapter(
                WebClient.builder().baseUrl(wireMock.getHttpBaseUrl()).build(), properties);
    }

    private static void stubGet(String path, int status, String contentType, String body) {
        stubFor(get(urlPathEqualTo(path)).willReturn(aResponse().withStatus(status)
                .withHeader("Content-Type", contentType).withBody(body)));
    }

    private static void stubCheckIn(int status, String contentType, String body) {
        stubFor(post(urlEqualTo(CHECK_INS_PATH)).willReturn(aResponse().withStatus(status)
                .withHeader("Content-Type", contentType).withBody(body)));
    }

    private static String problem(int status, String code) {
        return ("{\"type\":\"https://menta.dance/problems/x\",\"status\":%d,\"detail\":\"secret detail\","
                + "\"code\":\"%s\"}").formatted(status, code);
    }

    // ---- listSessions ----

    @Test
    @DisplayName("listSessions sends the UTC range as ISO-8601 with Z and the bearer token")
    void listSendsTheRange() {
        stubGet(SESSIONS_PATH, 200, "application/json", SESSIONS_BODY);

        adapter.listSessions(TOKEN, FROM, TO);

        verify(getRequestedFor(urlPathEqualTo(SESSIONS_PATH))
                .withQueryParam("from", equalTo("2026-11-13T03:00:00Z"))
                .withQueryParam("to", equalTo("2026-11-14T03:00:00Z"))
                .withHeader("Authorization", equalTo("Bearer " + TOKEN)));
    }

    @Test
    @DisplayName("listSessions maps id, course, start, cancelled flag, capacity and occupied spots")
    void listMapsTheSessions() {
        stubGet(SESSIONS_PATH, 200, "application/json", SESSIONS_BODY);

        List<StaffSession> sessions = adapter.listSessions(TOKEN, FROM, TO);

        assertThat(sessions).containsExactly(
                new StaffSession(SESSION_ID, "Salsa", Instant.parse("2026-11-13T21:00:00Z"), false, 15, 12),
                new StaffSession("1b6f7c8e-1d2a-4e3b-9c4d-0a1b2c3d4e5f", "Bachata",
                        Instant.parse("2026-11-13T23:00:00Z"), true, 10, 0));
    }

    @Test
    @DisplayName("an empty agenda is an empty list")
    void emptyAgenda() {
        stubGet(SESSIONS_PATH, 200, "application/json", "{\"sessions\": []}");

        assertThat(adapter.listSessions(TOKEN, FROM, TO)).isEmpty();
    }

    static Stream<Arguments> unusableAgendas() {
        return Stream.of(
                Arguments.of("{}"),
                Arguments.of("{\"sessions\": [{\"courseName\": \"Salsa\"}]}"),
                Arguments.of("{\"sessions\": [{\"sessionId\": \"s\", \"courseName\": \"Salsa\","
                        + " \"scheduledAt\": \"2026-11-13T21:00:00Z\", \"status\": \"SCHEDULED\", \"capacity\": 5}]}"),
                Arguments.of("<html>not json</html>"),
                Arguments.of(""));
    }

    @ParameterizedTest(name = "[{index}] {0} is unavailable")
    @MethodSource("unusableAgendas")
    @DisplayName("listSessions is unavailable on an incomplete or unreadable body")
    void unusableAgendaIsUnavailable(String body) {
        stubGet(SESSIONS_PATH, 200, "application/json", body);

        assertThatThrownBy(() -> adapter.listSessions(TOKEN, FROM, TO))
                .isInstanceOf(PhysicalReceptionApiClient.ServiceUnavailableException.class)
                .hasMessageNotContaining(TOKEN);
    }

    @ParameterizedTest(name = "status {0} is unavailable")
    @ValueSource(ints = {201, 204, 400, 401, 403, 404, 429, 500, 503})
    @DisplayName("listSessions is unavailable on any status but 200, with nothing of the reply in the failure")
    void listStatusesAreUnavailable(int status) {
        stubGet(SESSIONS_PATH, status, "application/problem+json", problem(status, "INVALID_REQUEST"));

        assertThatThrownBy(() -> adapter.listSessions(TOKEN, FROM, TO))
                .isInstanceOf(PhysicalReceptionApiClient.ServiceUnavailableException.class)
                .hasMessageNotContaining("secret")
                .hasMessageNotContaining(TOKEN);
    }

    // ---- getRoster ----

    @Test
    @DisplayName("getRoster gets the session's roster with the bearer token and maps header and students")
    void rosterMaps() {
        stubGet(ROSTER_PATH, 200, "application/json", ROSTER_BODY);

        SessionRoster roster = adapter.getRoster(TOKEN, SESSION_ID);

        assertThat(roster.sessionId()).isEqualTo(SESSION_ID);
        assertThat(roster.courseName()).isEqualTo("Salsa");
        assertThat(roster.scheduledAt()).isEqualTo(Instant.parse("2026-11-13T21:00:00Z"));
        assertThat(roster.cancelled()).isFalse();
        assertThat(roster.capacity()).isEqualTo(15);
        assertThat(roster.students()).containsExactly(
                new RosterStudent(STUDENT_ID, "Ada Lovelace", "MONTHLY", true,
                        Instant.parse("2026-11-13T21:02:00Z"), "QR"),
                new RosterStudent("9a1b2c3d-1d2a-4e3b-9c4d-5b6f7c8e9f00", "Grace Hopper", "INDIVIDUAL", false,
                        null, null));
        verify(getRequestedFor(urlEqualTo(ROSTER_PATH)).withHeader("Authorization", equalTo("Bearer " + TOKEN)));
    }

    @Test
    @DisplayName("a cancelled session keeps its roster and is flagged")
    void cancelledRoster() {
        stubGet(ROSTER_PATH, 200, "application/json", ROSTER_BODY.replace("\"SCHEDULED\"", "\"CANCELLED\""));

        assertThat(adapter.getRoster(TOKEN, SESSION_ID).cancelled()).isTrue();
    }

    @Test
    @DisplayName("the session id travels as an encoded path segment: it cannot add segments or a query")
    void sessionIdIsEncodedAsOneSegment() {
        stubGet("/api/v1/admin/physical/sessions/a%2F..%2Fb%3Fx%3D1/roster", 200, "application/json", ROSTER_BODY);

        adapter.getRoster(TOKEN, "a/../b?x=1");

        verify(getRequestedFor(urlEqualTo("/api/v1/admin/physical/sessions/a%2F..%2Fb%3Fx%3D1/roster")));
    }

    @Test
    @DisplayName("a 404 SESSION_NOT_FOUND is the typed not-found failure, with nothing of the reply in it")
    void rosterNotFound() {
        stubGet(ROSTER_PATH, 404, "application/problem+json", problem(404, "SESSION_NOT_FOUND"));

        assertThatThrownBy(() -> adapter.getRoster(TOKEN, SESSION_ID))
                .isInstanceOf(PhysicalReceptionApiClient.SessionNotFoundException.class)
                .hasMessageNotContaining("secret")
                .hasMessageNotContaining(TOKEN);
    }

    static Stream<Arguments> otherRosterNotFounds() {
        return Stream.of(
                Arguments.of("{\"code\":\"SOMETHING_ELSE\"}"),
                Arguments.of("{\"detail\":\"no code\"}"),
                Arguments.of("<html>not json</html>"),
                Arguments.of(""));
    }

    @ParameterizedTest(name = "[{index}] 404 with body {0} is unavailable")
    @MethodSource("otherRosterNotFounds")
    @DisplayName("a 404 with another code, without a code or with an unreadable body is unavailable")
    void otherNotFoundsAreUnavailable(String body) {
        stubGet(ROSTER_PATH, 404, "application/problem+json", body);

        assertThatThrownBy(() -> adapter.getRoster(TOKEN, SESSION_ID))
                .isInstanceOf(PhysicalReceptionApiClient.ServiceUnavailableException.class);
    }

    @ParameterizedTest(name = "status {0} is unavailable")
    @ValueSource(ints = {400, 401, 403, 429, 500, 503})
    @DisplayName("getRoster is unavailable on any other error status")
    void rosterStatusesAreUnavailable(int status) {
        stubGet(ROSTER_PATH, status, "application/problem+json", problem(status, "SESSION_NOT_FOUND"));

        assertThatThrownBy(() -> adapter.getRoster(TOKEN, SESSION_ID))
                .isInstanceOf(PhysicalReceptionApiClient.ServiceUnavailableException.class)
                .hasMessageNotContaining("secret");
    }

    static Stream<Arguments> unusableRosters() {
        String header = "\"session\": {\"sessionId\": \"s\", \"courseName\": \"Salsa\","
                + " \"scheduledAt\": \"2026-11-13T21:00:00Z\", \"status\": \"SCHEDULED\", \"capacity\": 5}";
        return Stream.of(
                Arguments.of("{}"),
                Arguments.of("{\"session\": {\"sessionId\": \"s\"}, \"students\": []}"),
                Arguments.of("{" + header + "}"),
                Arguments.of("{" + header + ", \"students\": [{\"displayName\": \"Ada\"}]}"),
                Arguments.of("<html>not json</html>"));
    }

    @ParameterizedTest(name = "[{index}] {0} is unavailable")
    @MethodSource("unusableRosters")
    @DisplayName("getRoster is unavailable on an incomplete or unreadable body")
    void unusableRosterIsUnavailable(String body) {
        stubGet(ROSTER_PATH, 200, "application/json", body);

        assertThatThrownBy(() -> adapter.getRoster(TOKEN, SESSION_ID))
                .isInstanceOf(PhysicalReceptionApiClient.ServiceUnavailableException.class);
    }

    // ---- registerManualCheckIn ----

    @Test
    @DisplayName("a 201 is a new attendance: posts the MANUAL body with the bearer token and flags it as new")
    void newCheckIn() {
        stubCheckIn(201, "application/json", CHECK_IN_BODY);

        CheckInReceipt receipt = adapter.registerManualCheckIn(TOKEN, SESSION_ID, STUDENT_ID);

        assertThat(receipt).isEqualTo(new CheckInReceipt(false, Instant.parse("2026-11-13T21:05:00Z"), "MANUAL"));
        verify(postRequestedFor(urlEqualTo(CHECK_INS_PATH))
                .withHeader("Authorization", equalTo("Bearer " + TOKEN))
                .withHeader("Content-Type", equalTo("application/json"))
                .withRequestBody(equalToJson(
                        "{\"type\": \"MANUAL\", \"studentId\": \"" + STUDENT_ID + "\"}", false, false)));
    }

    @Test
    @DisplayName("a 200 is the idempotent replay: the existing attendance, flagged as already registered")
    void repeatedCheckIn() {
        stubCheckIn(200, "application/json",
                "{\"attendanceId\": \"7a1b2c3d-1d2a-4e3b-9c4d-5b6f7c8e9f00\", \"recordedAt\": \"2026-11-13T21:02:00Z\","
                        + " \"kind\": \"QR\"}");

        CheckInReceipt receipt = adapter.registerManualCheckIn(TOKEN, SESSION_ID, STUDENT_ID);

        assertThat(receipt).isEqualTo(new CheckInReceipt(true, Instant.parse("2026-11-13T21:02:00Z"), "QR"));
    }

    @Test
    @DisplayName("the session id of a check-in travels as an encoded path segment")
    void checkInSessionIdIsEncoded() {
        stubFor(post(urlPathMatching("/api/v1/physical/sessions/.*/check-ins")).willReturn(aResponse()
                .withStatus(201).withHeader("Content-Type", "application/json").withBody(CHECK_IN_BODY)));

        adapter.registerManualCheckIn(TOKEN, "a/../b?x=1", STUDENT_ID);

        verify(postRequestedFor(urlEqualTo("/api/v1/physical/sessions/a%2F..%2Fb%3Fx%3D1/check-ins")));
    }

    static Stream<Arguments> rejections() {
        return Stream.of(
                Arguments.of(403, "CAPACITY_ASSIGNMENT_REQUIRED", Reason.CAPACITY_ASSIGNMENT_REQUIRED),
                Arguments.of(403, "OUTSIDE_CHECK_IN_WINDOW", Reason.OUTSIDE_CHECK_IN_WINDOW),
                Arguments.of(409, "SESSION_NOT_ACTIVE", Reason.SESSION_NOT_ACTIVE));
    }

    @ParameterizedTest(name = "{0} {1} is rejected as {2}")
    @MethodSource("rejections")
    @DisplayName("the refusals the screen words are typed failures, with nothing of the reply in them")
    void typedRejections(int status, String code, Reason reason) {
        stubCheckIn(status, "application/problem+json", problem(status, code));

        assertThatThrownBy(() -> adapter.registerManualCheckIn(TOKEN, SESSION_ID, STUDENT_ID))
                .isInstanceOfSatisfying(CheckInRejectedException.class,
                        e -> assertThat(e.getReason()).isEqualTo(reason))
                .hasMessageNotContaining("secret")
                .hasMessageNotContaining(TOKEN)
                .hasMessageNotContaining(STUDENT_ID);
    }

    static Stream<Arguments> otherProblems() {
        return Stream.of(
                Arguments.of(403, "INSUFFICIENT_ROLE"),
                Arguments.of(403, "SESSION_NOT_ACTIVE"),
                Arguments.of(409, "ALREADY_PROCESSING"),
                Arguments.of(409, "CAPACITY_ASSIGNMENT_REQUIRED"),
                Arguments.of(403, ""),
                Arguments.of(409, ""));
    }

    @ParameterizedTest(name = "{0} {1} is unavailable")
    @MethodSource("otherProblems")
    @DisplayName("a 403 or 409 with another code, or without a code, is unavailable")
    void otherProblemsAreUnavailable(int status, String code) {
        stubCheckIn(status, "application/problem+json",
                code.isEmpty() ? "{\"detail\":\"secret\"}" : problem(status, code));

        assertThatThrownBy(() -> adapter.registerManualCheckIn(TOKEN, SESSION_ID, STUDENT_ID))
                .isInstanceOf(PhysicalReceptionApiClient.ServiceUnavailableException.class)
                .hasMessageNotContaining("secret");
    }

    @Test
    @DisplayName("a 403 or 409 with an unreadable or empty body is unavailable")
    void unreadableProblemIsUnavailable() {
        stubCheckIn(403, "text/html", "<html>forbidden</html>");
        assertThatThrownBy(() -> adapter.registerManualCheckIn(TOKEN, SESSION_ID, STUDENT_ID))
                .isInstanceOf(PhysicalReceptionApiClient.ServiceUnavailableException.class);

        stubCheckIn(409, "application/problem+json", "");
        assertThatThrownBy(() -> adapter.registerManualCheckIn(TOKEN, SESSION_ID, STUDENT_ID))
                .isInstanceOf(PhysicalReceptionApiClient.ServiceUnavailableException.class);
    }

    @ParameterizedTest(name = "status {0} is unavailable")
    @ValueSource(ints = {202, 204, 400, 401, 404, 410, 429, 500, 503})
    @DisplayName("any other status of the check-in is unavailable, with nothing of the reply in the failure")
    void checkInStatusesAreUnavailable(int status) {
        stubCheckIn(status, "application/problem+json", problem(status, "CHECK_IN_DEGRADED"));

        assertThatThrownBy(() -> adapter.registerManualCheckIn(TOKEN, SESSION_ID, STUDENT_ID))
                .isInstanceOf(PhysicalReceptionApiClient.ServiceUnavailableException.class)
                .hasMessageNotContaining("secret")
                .hasMessageNotContaining(TOKEN);
    }

    @ParameterizedTest(name = "[{index}] {0} is unavailable")
    @ValueSource(strings = {"{}", "{\"recordedAt\": \"2026-11-13T21:05:00Z\"}", "<html>not json</html>", ""})
    @DisplayName("a 2xx without a usable attendance is unavailable")
    void unusableReceiptIsUnavailable(String body) {
        stubCheckIn(201, "application/json", body);

        assertThatThrownBy(() -> adapter.registerManualCheckIn(TOKEN, SESSION_ID, STUDENT_ID))
                .isInstanceOf(PhysicalReceptionApiClient.ServiceUnavailableException.class);
    }

    // ---- transport ----

    @Test
    @DisplayName("a dropped connection and a response slower than the timeout are unavailable, on every call")
    void transportErrorsAndTimeout(WireMockRuntimeInfo wireMock) {
        stubFor(get(urlPathEqualTo(SESSIONS_PATH)).willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)));
        assertThatThrownBy(() -> adapter.listSessions(TOKEN, FROM, TO))
                .isInstanceOf(PhysicalReceptionApiClient.ServiceUnavailableException.class)
                .hasMessageNotContaining(TOKEN);

        PhysicalReceptionApiAdapter impatient = adapterWith(wireMock, SHORT_TIMEOUT);
        stubGet(ROSTER_PATH, 200, "application/json", ROSTER_BODY);
        stubFor(get(urlPathEqualTo(ROSTER_PATH)).willReturn(aResponse().withStatus(200).withFixedDelay(2_000)
                .withHeader("Content-Type", "application/json").withBody(ROSTER_BODY)));
        assertThatThrownBy(() -> impatient.getRoster(TOKEN, SESSION_ID))
                .isInstanceOf(PhysicalReceptionApiClient.ServiceUnavailableException.class);

        stubFor(post(urlEqualTo(CHECK_INS_PATH)).willReturn(aResponse().withStatus(201).withFixedDelay(2_000)
                .withHeader("Content-Type", "application/json").withBody(CHECK_IN_BODY)));
        assertThatThrownBy(() -> impatient.registerManualCheckIn(TOKEN, SESSION_ID, STUDENT_ID))
                .isInstanceOf(PhysicalReceptionApiClient.ServiceUnavailableException.class);
    }
}
