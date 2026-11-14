package com.menta.bff.infrastructure.adapter;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.anyRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.matching;
import static com.github.tomakehurst.wiremock.client.WireMock.notMatching;
import static com.github.tomakehurst.wiremock.client.WireMock.patchRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.request;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.tomakehurst.wiremock.http.Fault;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import com.menta.bff.application.dto.CourseChanges;
import com.menta.bff.application.dto.CourseCreation;
import com.menta.bff.application.dto.ManagedCourse;
import com.menta.bff.application.dto.ManagedSession;
import com.menta.bff.application.dto.SessionChanges;
import com.menta.bff.application.dto.SessionCreation;
import com.menta.bff.application.port.out.PhysicalCourseManagementApiClient.CourseManagementException;
import com.menta.bff.application.port.out.PhysicalCourseManagementApiClient.CourseManagementException.Code;
import com.menta.bff.application.port.out.PhysicalCourseManagementApiClient.ServiceUnavailableException;
import com.menta.bff.infrastructure.config.VirtualApiProperties;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.web.reactive.function.client.WebClient;

@WireMockTest
@DisplayName("PhysicalCourseManagementApiAdapter")
class PhysicalCourseManagementApiAdapterTest {

    private static final String COURSE_ID = "5b6f7c8e-1d2a-4e3b-9c4d-0a1b2c3d4e5f";
    private static final String SESSION_ID = "1b6f7c8e-1d2a-4e3b-9c4d-0a1b2c3d4e5f";
    private static final String PROFESSOR_ID = "0a1b2c3d-1d2a-4e3b-9c4d-5b6f7c8e9f00";
    private static final String BASE = "/api/v1/admin/physical";
    private static final String COURSES_PATH = BASE + "/courses";
    private static final String COURSE_PATH = COURSES_PATH + "/" + COURSE_ID;
    private static final String SESSIONS_PATH = COURSE_PATH + "/sessions";
    private static final String BATCH_PATH = SESSIONS_PATH + "/batch";
    private static final String SESSION_PATH = BASE + "/sessions/" + SESSION_ID;
    private static final String TOKEN = "access-token-abc";

    private static final String COURSE_JSON =
            """
            {"courseId": "5b6f7c8e-1d2a-4e3b-9c4d-0a1b2c3d4e5f", "title": "Salsa", "description": "Nivel inicial",
             "professorId": "0a1b2c3d-1d2a-4e3b-9c4d-5b6f7c8e9f00", "professorName": "Ada Lovelace",
             "dayOfWeek": "WEDNESDAY", "startTime": "01:00:00", "durationMinutes": 60, "level": "BEGINNER",
             "capacity": 15, "status": "ACTIVE"}
            """;
    private static final String SESSION_JSON =
            """
            {"sessionId": "1b6f7c8e-1d2a-4e3b-9c4d-0a1b2c3d4e5f", "courseId": "5b6f7c8e-1d2a-4e3b-9c4d-0a1b2c3d4e5f",
             "scheduledAt": "2026-11-18T01:00:00Z", "capacity": 15, "assignedSpots": 10, "activeCapacityHolds": 2,
             "availableSpots": 3, "status": "SCHEDULED", "notes": "Traer zapatos"}
            """;
    private static final ManagedCourse COURSE = new ManagedCourse(COURSE_ID, "Salsa", "Nivel inicial",
            PROFESSOR_ID, "Ada Lovelace", DayOfWeek.WEDNESDAY, LocalTime.of(1, 0), 60, "BEGINNER", 15, "ACTIVE");
    private static final ManagedSession SESSION = new ManagedSession(SESSION_ID, COURSE_ID,
            Instant.parse("2026-11-18T01:00:00Z"), 15, 10, 2, 3, "SCHEDULED", "Traer zapatos");

    private static final Duration GENEROUS_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration SHORT_TIMEOUT = Duration.ofMillis(300);

    private PhysicalCourseManagementApiAdapter adapter;

    @BeforeEach
    void setUp(WireMockRuntimeInfo wireMock) {
        adapter = adapterWith(wireMock, GENEROUS_TIMEOUT);
    }

    private static PhysicalCourseManagementApiAdapter adapterWith(WireMockRuntimeInfo wireMock, Duration timeout) {
        VirtualApiProperties properties = new VirtualApiProperties();
        properties.setBaseUrl(wireMock.getHttpBaseUrl());
        properties.setTimeout(timeout);
        return new PhysicalCourseManagementApiAdapter(
                WebClient.builder().baseUrl(wireMock.getHttpBaseUrl()).build(), properties);
    }

    private static void stub(String method, String path, int status, String body) {
        stubFor(request(method, urlPathEqualTo(path)).willReturn(aResponse().withStatus(status)
                .withHeader("Content-Type", "application/json").withBody(body)));
    }

    private static String problem(String code) {
        return ("{\"type\":\"https://menta.dance/problems/x\",\"status\":409,\"detail\":\"secret detail\","
                + "\"code\":\"%s\"}").formatted(code);
    }

    private static CourseCreation creation() {
        return new CourseCreation("Salsa", "Nivel inicial", PROFESSOR_ID, "Ada Lovelace", DayOfWeek.WEDNESDAY,
                LocalTime.of(1, 0), 60, "BEGINNER", 15);
    }

    // ---- every call: one operation per row ----

    private record Operation(String name, String method, String path, int okStatus, String okBody,
            Consumer<PhysicalCourseManagementApiAdapter> call) {
        @Override
        public String toString() {
            return name;
        }
    }

    private static List<Operation> operations() {
        return List.of(
                new Operation("listCourses", "GET", COURSES_PATH, 200, "{\"courses\": [" + COURSE_JSON + "]}",
                        a -> a.listCourses(TOKEN)),
                new Operation("createCourse", "POST", COURSES_PATH, 201, COURSE_JSON,
                        a -> a.createCourse(TOKEN, creation())),
                new Operation("updateCourse", "PATCH", COURSE_PATH, 200, COURSE_JSON,
                        a -> a.updateCourse(TOKEN, COURSE_ID, new CourseChanges(null, null, null, null, null, null,
                                null, "INACTIVE"))),
                new Operation("listCourseSessions", "GET", SESSIONS_PATH, 200, "{\"sessions\": [" + SESSION_JSON + "]}",
                        a -> a.listCourseSessions(TOKEN, COURSE_ID, null, null)),
                new Operation("createSession", "POST", SESSIONS_PATH, 201, SESSION_JSON,
                        a -> a.createSession(TOKEN, COURSE_ID,
                                new SessionCreation(LocalDate.of(2026, 11, 18), LocalTime.of(1, 0), null, null))),
                new Operation("createSessionsInBatch", "POST", BATCH_PATH, 201,
                        "{\"sessions\": [" + SESSION_JSON + "]}",
                        a -> a.createSessionsInBatch(TOKEN, COURSE_ID, LocalDate.of(2026, 11, 2),
                                LocalDate.of(2026, 12, 1))),
                new Operation("updateSession", "PATCH", SESSION_PATH, 200, SESSION_JSON,
                        a -> a.updateSession(TOKEN, SESSION_ID,
                                new SessionChanges(null, null, null, null, "CANCELLED"))));
    }

    static Stream<Arguments> everyOperation() {
        return operations().stream().map(Arguments::of);
    }

    static Stream<Arguments> everyOperationAndCode() {
        return operations().stream().flatMap(operation -> Stream.of(Code.values())
                .map(code -> Arguments.of(operation, code)));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("everyOperation")
    @DisplayName("every call sends the bearer token, and a call with the expected status succeeds")
    void sendsTheBearerToken(Operation operation) {
        stub(operation.method(), operation.path(), operation.okStatus(), operation.okBody());

        operation.call().accept(adapter);

        verify(anyRequestedFor(urlPathEqualTo(operation.path()))
                .withHeader("Authorization", equalTo("Bearer " + TOKEN)));
    }

    @ParameterizedTest(name = "{0} refused with {1}")
    @MethodSource("everyOperationAndCode")
    @DisplayName("an expected code in a 400, 403, 404 or 409 is a typed failure, with nothing of the reply in it")
    void typedRefusals(Operation operation, Code code) {
        int status = switch (code) {
            case PROFESSOR_REQUIRED, PROFESSOR_MISMATCH, INVALID_REQUEST -> 400;
            case COURSE_NOT_OWNED -> 403;
            case COURSE_NOT_FOUND, SESSION_NOT_FOUND -> 404;
            default -> 409;
        };
        stub(operation.method(), operation.path(), status, problem(code.name()));

        assertThatThrownBy(() -> operation.call().accept(adapter))
                .isInstanceOfSatisfying(CourseManagementException.class, e -> {
                    assertThat(e.getCode()).isEqualTo(code);
                    assertThat(e.getConflictingDates()).isEmpty();
                })
                .hasMessageNotContaining("secret")
                .hasMessageNotContaining(TOKEN)
                .hasMessageNotContaining(COURSE_ID);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("everyOperation")
    @DisplayName("the conflicting dates of a SESSION_ALREADY_EXISTS are kept, in order")
    void conflictingDates(Operation operation) {
        stub(operation.method(), operation.path(), 409,
                "{\"code\":\"SESSION_ALREADY_EXISTS\",\"detail\":\"secret detail\","
                        + "\"conflictingDates\":[\"2026-12-02T20:00:00Z\",\"2026-12-09T20:00:00Z\"]}");

        assertThatThrownBy(() -> operation.call().accept(adapter))
                .isInstanceOfSatisfying(CourseManagementException.class, e -> {
                    assertThat(e.getCode()).isEqualTo(Code.SESSION_ALREADY_EXISTS);
                    assertThat(e.getConflictingDates()).containsExactly(
                            Instant.parse("2026-12-02T20:00:00Z"), Instant.parse("2026-12-09T20:00:00Z"));
                });
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("everyOperation")
    @DisplayName("a SESSION_ALREADY_EXISTS without dates is still a conflict, with no dates")
    void conflictWithoutDates(Operation operation) {
        stub(operation.method(), operation.path(), 409, problem("SESSION_ALREADY_EXISTS"));

        assertThatThrownBy(() -> operation.call().accept(adapter))
                .isInstanceOfSatisfying(CourseManagementException.class,
                        e -> assertThat(e.getConflictingDates()).isEmpty());
    }

    static Stream<Arguments> unexpectedAnswers() {
        return operations().stream().flatMap(operation -> Stream.of(
                Arguments.of(operation, 202, "{}"),
                Arguments.of(operation, 204, ""),
                Arguments.of(operation, 401, problem("UNAUTHENTICATED")),
                Arguments.of(operation, 429, problem("RATE_LIMITED")),
                Arguments.of(operation, 500, problem("INTERNAL")),
                Arguments.of(operation, 503, "<html>down</html>"),
                // a refusal whose code is not one of the expected ones, or that has none, or no readable body
                Arguments.of(operation, 400, problem("SOMETHING_NEW")),
                Arguments.of(operation, 403, "{\"detail\":\"secret detail\"}"),
                Arguments.of(operation, 404, problem("")),
                Arguments.of(operation, 409, "<html>conflict</html>"),
                Arguments.of(operation, 409, "")));
    }

    @ParameterizedTest(name = "[{index}] {0} answered {1}")
    @MethodSource("unexpectedAnswers")
    @DisplayName("everything unexpected is unavailable, with nothing of the reply or the token in the failure")
    void unexpectedIsUnavailable(Operation operation, int status, String body) {
        stub(operation.method(), operation.path(), status, body);

        assertThatThrownBy(() -> operation.call().accept(adapter))
                .isInstanceOf(ServiceUnavailableException.class)
                .hasMessageNotContaining("secret")
                .hasMessageNotContaining(TOKEN);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("everyOperation")
    @DisplayName("a success without a usable body is unavailable")
    void unusableSuccessIsUnavailable(Operation operation) {
        for (String body : List.of("{}", "{\"courses\": [{}], \"sessions\": [{}]}", "<html>not json</html>", "")) {
            stub(operation.method(), operation.path(), operation.okStatus(), body);

            assertThatThrownBy(() -> operation.call().accept(adapter))
                    .as(body).isInstanceOf(ServiceUnavailableException.class);
        }
    }

    @Test
    @DisplayName("a course with a weekday or a time it cannot read is unavailable")
    void unreadableRecurrenceIsUnavailable() {
        stub("GET", COURSES_PATH, 200,
                "{\"courses\": [" + COURSE_JSON.replace("WEDNESDAY", "FUNDAY") + "]}");
        assertThatThrownBy(() -> adapter.listCourses(TOKEN)).isInstanceOf(ServiceUnavailableException.class);

        stub("GET", COURSES_PATH, 200, "{\"courses\": [" + COURSE_JSON.replace("01:00:00", "tarde") + "]}");
        assertThatThrownBy(() -> adapter.listCourses(TOKEN)).isInstanceOf(ServiceUnavailableException.class);
    }

    @Test
    @DisplayName("a dropped connection and a response slower than the timeout are unavailable")
    void transportErrorsAndTimeout(WireMockRuntimeInfo wireMock) {
        stubFor(request("GET", urlPathEqualTo(COURSES_PATH))
                .willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)));
        assertThatThrownBy(() -> adapter.listCourses(TOKEN))
                .isInstanceOf(ServiceUnavailableException.class).hasMessageNotContaining(TOKEN);

        stubFor(request("PATCH", urlPathEqualTo(SESSION_PATH)).willReturn(aResponse().withStatus(200)
                .withFixedDelay(2_000).withHeader("Content-Type", "application/json").withBody(SESSION_JSON)));
        assertThatThrownBy(() -> adapterWith(wireMock, SHORT_TIMEOUT)
                .updateSession(TOKEN, SESSION_ID, new SessionChanges(null, null, null, null, "CANCELLED")))
                .isInstanceOf(ServiceUnavailableException.class).hasMessageNotContaining(TOKEN);
    }

    // ---- what each call sends and returns ----

    @Test
    @DisplayName("listCourses maps every field, with the recurrence as typed UTC values")
    void listCourses() {
        stub("GET", COURSES_PATH, 200, "{\"courses\": [" + COURSE_JSON + "]}");

        assertThat(adapter.listCourses(TOKEN)).containsExactly(COURSE);
    }

    @Test
    @DisplayName("an empty list of courses is an empty list")
    void emptyCourses() {
        stub("GET", COURSES_PATH, 200, "{\"courses\": []}");

        assertThat(adapter.listCourses(TOKEN)).isEmpty();
    }

    @Test
    @DisplayName("createCourse sends every field, with the explicit professorId and the UTC recurrence")
    void createCourse() {
        stub("POST", COURSES_PATH, 201, COURSE_JSON);

        ManagedCourse created = adapter.createCourse(TOKEN, creation());

        assertThat(created).isEqualTo(COURSE);
        verify(postRequestedFor(urlPathEqualTo(COURSES_PATH)).withRequestBody(equalToJson(
                """
                {"title": "Salsa", "description": "Nivel inicial", "professorId": "%s",
                 "professorName": "Ada Lovelace", "dayOfWeek": "WEDNESDAY", "startTime": "01:00:00",
                 "durationMinutes": 60, "level": "BEGINNER", "capacity": 15}
                """.formatted(PROFESSOR_ID), false, false)));
    }

    @Test
    @DisplayName("updateCourse sends only the fields that change, and the id as one path segment")
    void updateCourse() {
        stub("PATCH", COURSE_PATH, 200, COURSE_JSON);

        ManagedCourse updated = adapter.updateCourse(TOKEN, COURSE_ID,
                new CourseChanges("Salsa", null, DayOfWeek.TUESDAY, LocalTime.of(23, 30), null, null, 20, "ACTIVE"));

        assertThat(updated).isEqualTo(COURSE);
        verify(patchRequestedFor(urlPathEqualTo(COURSE_PATH)).withRequestBody(equalToJson(
                "{\"title\": \"Salsa\", \"dayOfWeek\": \"TUESDAY\", \"startTime\": \"23:30:00\","
                        + " \"capacity\": 20, \"status\": \"ACTIVE\"}", false, false)));
    }

    @Test
    @DisplayName("listCourseSessions sends the UTC range as ISO-8601 with Z, and omits the ends it does not have")
    void listCourseSessions() {
        stub("GET", SESSIONS_PATH, 200, "{\"sessions\": [" + SESSION_JSON + "]}");

        List<ManagedSession> all = adapter.listCourseSessions(TOKEN, COURSE_ID, null, null);
        List<ManagedSession> ranged = adapter.listCourseSessions(TOKEN, COURSE_ID,
                Instant.parse("2026-11-01T03:00:00Z"), Instant.parse("2026-12-01T03:00:00Z"));

        assertThat(all).containsExactly(SESSION);
        assertThat(ranged).containsExactly(SESSION);
        verify(getRequestedFor(urlPathEqualTo(SESSIONS_PATH))
                .withQueryParam("from", equalTo("2026-11-01T03:00:00Z"))
                .withQueryParam("to", equalTo("2026-12-01T03:00:00Z")));
        verify(getRequestedFor(urlPathEqualTo(SESSIONS_PATH))
                .withQueryParam("from", notMatching(".*")).withQueryParam("to", notMatching(".*")));
    }

    @Test
    @DisplayName("createSession sends the UTC date and time, and leaves out the capacity and notes it has none of")
    void createSession() {
        stub("POST", SESSIONS_PATH, 201, SESSION_JSON);

        ManagedSession created = adapter.createSession(TOKEN, COURSE_ID,
                new SessionCreation(LocalDate.of(2026, 11, 18), LocalTime.of(1, 0), null, null));

        assertThat(created).isEqualTo(SESSION);
        verify(postRequestedFor(urlPathEqualTo(SESSIONS_PATH)).withRequestBody(
                equalToJson("{\"date\": \"2026-11-18\", \"startTime\": \"01:00:00\"}", false, false)));

        adapter.createSession(TOKEN, COURSE_ID,
                new SessionCreation(LocalDate.of(2026, 11, 18), LocalTime.of(1, 0), 12, "Con invitado"));
        verify(postRequestedFor(urlPathEqualTo(SESSIONS_PATH)).withRequestBody(equalToJson(
                "{\"date\": \"2026-11-18\", \"startTime\": \"01:00:00\", \"capacity\": 12,"
                        + " \"notes\": \"Con invitado\"}", false, false)));
    }

    @Test
    @DisplayName("createSessionsInBatch sends the UTC range and returns the sessions created")
    void createSessionsInBatch() {
        stub("POST", BATCH_PATH, 201, "{\"sessions\": [" + SESSION_JSON + "," + SESSION_JSON + "]}");

        List<ManagedSession> created = adapter.createSessionsInBatch(
                TOKEN, COURSE_ID, LocalDate.of(2026, 11, 2), LocalDate.of(2026, 12, 1));

        assertThat(created).containsExactly(SESSION, SESSION);
        verify(postRequestedFor(urlPathEqualTo(BATCH_PATH)).withRequestBody(
                equalToJson("{\"fromDate\": \"2026-11-02\", \"toDate\": \"2026-12-01\"}", false, false)));
    }

    @Test
    @DisplayName("updateSession sends only the fields that change")
    void updateSession() {
        stub("PATCH", SESSION_PATH, 200, SESSION_JSON);

        ManagedSession updated = adapter.updateSession(TOKEN, SESSION_ID,
                new SessionChanges(LocalDate.of(2026, 11, 19), LocalTime.of(0, 30), null, null, null));

        assertThat(updated).isEqualTo(SESSION);
        verify(patchRequestedFor(urlPathEqualTo(SESSION_PATH)).withRequestBody(equalToJson(
                "{\"date\": \"2026-11-19\", \"startTime\": \"00:30:00\"}", false, false)));
    }

    @Test
    @DisplayName("a session without notes maps with null notes")
    void sessionWithoutNotes() {
        stub("PATCH", SESSION_PATH, 200, SESSION_JSON.replace("\"Traer zapatos\"", "null"));

        ManagedSession updated = adapter.updateSession(TOKEN, SESSION_ID,
                new SessionChanges(null, null, null, null, "CANCELLED"));

        assertThat(updated.notes()).isNull();
        verify(patchRequestedFor(urlPathEqualTo(SESSION_PATH)).withRequestBody(matching(".*CANCELLED.*")));
    }
}
