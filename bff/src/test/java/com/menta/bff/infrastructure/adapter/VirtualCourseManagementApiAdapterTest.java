package com.menta.bff.infrastructure.adapter;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.anyRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.deleteRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.putRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.request;
import static com.github.tomakehurst.wiremock.client.WireMock.requestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.tomakehurst.wiremock.http.Fault;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import com.menta.bff.application.dto.CreateVirtualCourseCommand;
import com.menta.bff.application.dto.CreateVirtualLessonCommand;
import com.menta.bff.application.dto.CreateVirtualModuleCommand;
import com.menta.bff.application.dto.ManagedVirtualCourse;
import com.menta.bff.application.dto.ManagedVirtualLesson;
import com.menta.bff.application.dto.ManagedVirtualModule;
import com.menta.bff.application.dto.VirtualCourseChanges;
import com.menta.bff.application.dto.VirtualCourseDetail;
import com.menta.bff.application.dto.VirtualCourseDetail.VirtualCourseStats;
import com.menta.bff.application.dto.VirtualCourseDetail.VirtualLessonDetail;
import com.menta.bff.application.dto.VirtualCourseDetail.VirtualModuleDetail;
import com.menta.bff.application.dto.VirtualCourseLevel;
import com.menta.bff.application.dto.VirtualCourseStatus;
import com.menta.bff.application.dto.VirtualLessonChanges;
import com.menta.bff.application.dto.VirtualModuleChanges;
import com.menta.bff.application.port.out.VirtualCourseManagementApiClient.ServiceUnavailableException;
import com.menta.bff.application.port.out.VirtualCourseManagementApiClient.VirtualCourseManagementException;
import com.menta.bff.application.port.out.VirtualCourseManagementApiClient.VirtualCourseManagementException.Code;
import com.menta.bff.infrastructure.config.VirtualApiProperties;
import java.time.Duration;
import java.util.List;
import java.util.function.Consumer;
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
@DisplayName("VirtualCourseManagementApiAdapter")
class VirtualCourseManagementApiAdapterTest {

    private static final String COURSE_ID = "5b6f7c8e-1d2a-4e3b-9c4d-0a1b2c3d4e5f";
    private static final String PROFESSOR_ID = "0a1b2c3d-1d2a-4e3b-9c4d-5b6f7c8e9f00";
    private static final String COURSES_PATH = "/api/v1/admin/virtual/courses";
    private static final String COURSE_PATH = COURSES_PATH + "/" + COURSE_ID;
    private static final String MODULE_ID = "7c8e9f00-1d2a-4e3b-9c4d-5b6f0a1b2c3d";
    private static final String MODULE_2_ID = "8e9f0011-1d2a-4e3b-9c4d-5b6f0a1b2c3d";
    private static final String LESSON_ID = "9f001122-1d2a-4e3b-9c4d-5b6f0a1b2c3d";
    private static final String MODULES_PATH = COURSE_PATH + "/modules";
    private static final String REORDER_PATH = MODULES_PATH + "/reorder";
    private static final String MODULE_PATH = "/api/v1/admin/virtual/modules/" + MODULE_ID;
    private static final String LESSONS_PATH = MODULE_PATH + "/lessons";
    private static final String LESSON_PATH = "/api/v1/admin/virtual/lessons/" + LESSON_ID;
    private static final String TOKEN = "access-token-abc";
    private static final String DETAIL_TEXT = "Detalle del API";
    private static final ObjectMapper JSON = new ObjectMapper();

    private static final String COURSE_JSON =
            """
            {"courseId": "5b6f7c8e-1d2a-4e3b-9c4d-0a1b2c3d4e5f", "title": "Tango", "shortDescription": "Pasos base",
             "description": "Curso completo", "professorId": "0a1b2c3d-1d2a-4e3b-9c4d-5b6f7c8e9f00",
             "imageUrl": "https://img.example/t.png", "category": "Tango", "level": "BEGINNER", "premium": true,
             "status": "DRAFT"}
            """;
    private static final String DETAIL_JSON =
            """
            {"courseId": "5b6f7c8e-1d2a-4e3b-9c4d-0a1b2c3d4e5f", "title": "Tango", "shortDescription": "Pasos base",
             "description": "Curso completo", "professorId": "0a1b2c3d-1d2a-4e3b-9c4d-5b6f7c8e9f00",
             "thumbnailUrl": "https://img.example/t.png", "category": "Tango", "level": "ADVANCED",
             "isPremium": false, "status": "PUBLISHED",
             "modules": [{"moduleId": "m-1", "title": "Base", "preview": true, "order": 1, "lessons": [
               {"lessonId": "l-1", "title": "Abrazo", "description": "El abrazo", "durationMinutes": 12,
                "isFree": true, "order": 1, "videoId": "v-1"},
               {"lessonId": "l-2", "title": "Caminata", "description": "Caminar", "durationMinutes": 8,
                "isFree": false, "order": 2, "videoId": null}]}],
             "stats": {"moduleCount": 1, "lessonCount": 2, "totalDuration": "20m"}}
            """;
    private static final String MODULE_JSON =
            "{\"moduleId\": \"" + MODULE_ID + "\", \"courseId\": \"" + COURSE_ID
                    + "\", \"title\": \"Base\", \"preview\": true, \"order\": 2}";
    private static final String MODULE_2_JSON = MODULE_JSON.replace(MODULE_ID, MODULE_2_ID).replace("2}", "3}");
    private static final String LESSON_JSON =
            "{\"lessonId\": \"" + LESSON_ID + "\", \"moduleId\": \"" + MODULE_ID + "\", \"courseId\": \""
                    + COURSE_ID + "\", \"title\": \"Abrazo\", \"description\": \"El abrazo\", "
                    + "\"videoId\": \"v-1\", \"durationMinutes\": 12, \"free\": true, \"order\": 1}";
    private static final ManagedVirtualModule MODULE = new ManagedVirtualModule(MODULE_ID, COURSE_ID, "Base", true, 2);
    private static final ManagedVirtualLesson LESSON =
            new ManagedVirtualLesson(LESSON_ID, MODULE_ID, COURSE_ID, "Abrazo", "El abrazo", "v-1", 12, true, 1);
    private static final ManagedVirtualCourse COURSE = new ManagedVirtualCourse(COURSE_ID, "Tango", "Pasos base",
            "Curso completo", PROFESSOR_ID, "https://img.example/t.png", "Tango", VirtualCourseLevel.BEGINNER, true,
            VirtualCourseStatus.DRAFT);
    private static final VirtualCourseDetail DETAIL = new VirtualCourseDetail(COURSE_ID, "Tango", "Pasos base",
            "Curso completo", PROFESSOR_ID, "https://img.example/t.png", "Tango", VirtualCourseLevel.ADVANCED, false,
            VirtualCourseStatus.PUBLISHED,
            List.of(new VirtualModuleDetail("m-1", "Base", true, 1, List.of(
                    new VirtualLessonDetail("l-1", "Abrazo", "El abrazo", 12, true, 1, "v-1"),
                    new VirtualLessonDetail("l-2", "Caminata", "Caminar", 8, false, 2, null)))),
            new VirtualCourseStats(1, 2, "20m"));

    private static final Duration GENEROUS_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration SHORT_TIMEOUT = Duration.ofMillis(300);

    private VirtualCourseManagementApiAdapter adapter;

    @BeforeEach
    void setUp(WireMockRuntimeInfo wireMock) {
        adapter = adapterWith(wireMock, GENEROUS_TIMEOUT);
    }

    private static VirtualCourseManagementApiAdapter adapterWith(WireMockRuntimeInfo wireMock, Duration timeout) {
        VirtualApiProperties properties = new VirtualApiProperties();
        properties.setBaseUrl(wireMock.getHttpBaseUrl());
        properties.setTimeout(timeout);
        return new VirtualCourseManagementApiAdapter(
                WebClient.builder().baseUrl(wireMock.getHttpBaseUrl()).build(), properties);
    }

    private static void stub(String method, String path, int status, String body) {
        stubFor(request(method, urlPathEqualTo(path)).willReturn(aResponse().withStatus(status)
                .withHeader("Content-Type", "application/json").withBody(body)));
    }

    private static String problem(String code, String detail) {
        return "{\"status\":400,\"code\":\"%s\",\"detail\":%s}"
                .formatted(code, detail == null ? "null" : "\"" + detail + "\"");
    }

    private static CreateVirtualCourseCommand creation(String professorId) {
        return new CreateVirtualCourseCommand("Tango", "Pasos base", "Curso completo", "https://img.example/t.png",
                "Tango", VirtualCourseLevel.BEGINNER, professorId);
    }

    // ---- every call: one operation per row ----

    private record Operation(String name, String method, String path, int okStatus, String okBody,
            Consumer<VirtualCourseManagementApiAdapter> call) {
        @Override
        public String toString() {
            return name;
        }
    }

    private static List<Operation> operations() {
        return List.of(
                new Operation("listManagedCourses", "GET", COURSES_PATH, 200, "{\"courses\": [" + COURSE_JSON + "]}",
                        a -> a.listManagedCourses(TOKEN)),
                new Operation("getManagedCourse", "GET", COURSE_PATH, 200, DETAIL_JSON,
                        a -> a.getManagedCourse(TOKEN, COURSE_ID)),
                new Operation("createCourse", "POST", COURSES_PATH, 201, COURSE_JSON,
                        a -> a.createCourse(TOKEN, creation(null))),
                new Operation("updateCourse", "PUT", COURSE_PATH, 200, COURSE_JSON,
                        a -> a.updateCourse(TOKEN, COURSE_ID,
                                new VirtualCourseChanges("Tango", null, null, null, null, null, null))),
                new Operation("publishCourse", "PUT", COURSE_PATH + "/publish", 200, COURSE_JSON,
                        a -> a.publishCourse(TOKEN, COURSE_ID)),
                new Operation("unpublishCourse", "PUT", COURSE_PATH + "/unpublish", 200, COURSE_JSON,
                        a -> a.unpublishCourse(TOKEN, COURSE_ID)),
                new Operation("deleteCourse", "DELETE", COURSE_PATH, 204, "",
                        a -> a.deleteCourse(TOKEN, COURSE_ID)),
                new Operation("createModule", "POST", MODULES_PATH, 201, MODULE_JSON,
                        a -> a.createModule(TOKEN, COURSE_ID, new CreateVirtualModuleCommand("Base", null, null))),
                new Operation("updateModule", "PUT", MODULE_PATH, 200, MODULE_JSON,
                        a -> a.updateModule(TOKEN, MODULE_ID, new VirtualModuleChanges("Base", null, null))),
                new Operation("reorderModules", "PUT", REORDER_PATH, 200,
                        "{\"modules\": [" + MODULE_2_JSON + ", " + MODULE_JSON + "]}",
                        a -> a.reorderModules(TOKEN, COURSE_ID, List.of(MODULE_2_ID, MODULE_ID))),
                new Operation("createLesson", "POST", LESSONS_PATH, 201, LESSON_JSON,
                        a -> a.createLesson(TOKEN, MODULE_ID,
                                new CreateVirtualLessonCommand("Abrazo", "El abrazo", null, null, null, null))),
                new Operation("updateLesson", "PUT", LESSON_PATH, 200, LESSON_JSON,
                        a -> a.updateLesson(TOKEN, LESSON_ID,
                                new VirtualLessonChanges("Abrazo", null, null, null, null, null))));
    }

    static Stream<Arguments> everyOperation() {
        return operations().stream().map(Arguments::of);
    }

    static Stream<Arguments> everyOperationWithBody() {
        return operations().stream().filter(operation -> !operation.okBody().isEmpty()).map(Arguments::of);
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
            case INVALID_REQUEST, PROFESSOR_MISMATCH, COURSE_NOT_PUBLISHABLE, MODULE_REORDER_MISMATCH -> 400;
            case COURSE_NOT_OWNED -> 403;
            case COURSE_NOT_FOUND, MODULE_NOT_FOUND, LESSON_NOT_FOUND -> 404;
            case COURSE_NOT_DRAFT -> 409;
        };
        stub(operation.method(), operation.path(), status, problem(code.name(), DETAIL_TEXT));

        assertThatThrownBy(() -> operation.call().accept(adapter))
                .isInstanceOfSatisfying(VirtualCourseManagementException.class, e -> {
                    assertThat(e.getCode()).isEqualTo(code);
                    // Only the publish refusal carries the API's explanation; no other detail is kept.
                    assertThat(e.getPublishDetail())
                            .isEqualTo(code == Code.COURSE_NOT_PUBLISHABLE ? DETAIL_TEXT : null);
                })
                .hasMessageNotContaining(DETAIL_TEXT)
                .hasMessageNotContaining(TOKEN)
                .hasMessageNotContaining(COURSE_ID);
    }

    @Test
    @DisplayName("the detail of a COURSE_NOT_PUBLISHABLE is kept as the API words it, and bounded to 300 characters")
    void publishDetail() {
        String detail = "El curso no tiene ningún módulo.";
        stub("PUT", COURSE_PATH + "/publish", 400, problem("COURSE_NOT_PUBLISHABLE", detail));
        assertThatThrownBy(() -> adapter.publishCourse(TOKEN, COURSE_ID))
                .isInstanceOfSatisfying(VirtualCourseManagementException.class,
                        e -> assertThat(e.getPublishDetail()).isEqualTo(detail));

        stub("PUT", COURSE_PATH + "/publish", 400, problem("COURSE_NOT_PUBLISHABLE", "á".repeat(500)));
        assertThatThrownBy(() -> adapter.publishCourse(TOKEN, COURSE_ID))
                .isInstanceOfSatisfying(VirtualCourseManagementException.class,
                        e -> assertThat(e.getPublishDetail()).isEqualTo("á".repeat(300)));
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @ValueSource(strings = {
        "{\"code\":\"COURSE_NOT_PUBLISHABLE\"}",
        "{\"code\":\"COURSE_NOT_PUBLISHABLE\",\"detail\":null}",
        "{\"code\":\"COURSE_NOT_PUBLISHABLE\",\"detail\":\"   \"}"})
    @DisplayName("a COURSE_NOT_PUBLISHABLE with no usable detail has none, so the screen words it generically")
    void publishWithoutDetail(String body) {
        stub("PUT", COURSE_PATH + "/publish", 400, body);

        assertThatThrownBy(() -> adapter.publishCourse(TOKEN, COURSE_ID))
                .isInstanceOfSatisfying(VirtualCourseManagementException.class, e -> {
                    assertThat(e.getCode()).isEqualTo(Code.COURSE_NOT_PUBLISHABLE);
                    assertThat(e.getPublishDetail()).isNull();
                });
    }

    static Stream<Arguments> unexpectedAnswers() {
        return operations().stream().flatMap(operation -> Stream.of(
                // a success status other than the one the call expects
                Arguments.of(operation, operation.okStatus() == 200 ? 201 : 200, operation.okBody()),
                Arguments.of(operation, 202, "{}"),
                Arguments.of(operation, 401, problem("UNAUTHENTICATED", DETAIL_TEXT)),
                Arguments.of(operation, 429, problem("RATE_LIMITED", DETAIL_TEXT)),
                Arguments.of(operation, 500, problem("INTERNAL", DETAIL_TEXT)),
                Arguments.of(operation, 503, "<html>down</html>"),
                // a refusal whose code is not one of the expected ones, or that has none, or no readable body
                Arguments.of(operation, 400, problem("SOMETHING_NEW", DETAIL_TEXT)),
                Arguments.of(operation, 403, "{\"detail\":\"" + DETAIL_TEXT + "\"}"),
                Arguments.of(operation, 404, problem("", DETAIL_TEXT)),
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
                .hasMessageNotContaining(DETAIL_TEXT)
                .hasMessageNotContaining(TOKEN);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("everyOperationWithBody")
    @DisplayName("a success without a usable body is unavailable")
    void unusableSuccessIsUnavailable(Operation operation) {
        for (String body : List.of("{}", "{\"courses\": [{}]}", "<html>not json</html>", "")) {
            stub(operation.method(), operation.path(), operation.okStatus(), body);

            assertThatThrownBy(() -> operation.call().accept(adapter))
                    .as(body).isInstanceOf(ServiceUnavailableException.class);
        }
    }

    @Test
    @DisplayName("a dropped connection and a response slower than the timeout are unavailable")
    void transportErrorsAndTimeout(WireMockRuntimeInfo wireMock) {
        stubFor(request("GET", urlPathEqualTo(COURSES_PATH))
                .willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)));
        assertThatThrownBy(() -> adapter.listManagedCourses(TOKEN))
                .isInstanceOf(ServiceUnavailableException.class).hasMessageNotContaining(TOKEN);

        stubFor(request("DELETE", urlPathEqualTo(COURSE_PATH)).willReturn(aResponse().withStatus(204)
                .withFixedDelay(2_000)));
        assertThatThrownBy(() -> adapterWith(wireMock, SHORT_TIMEOUT).deleteCourse(TOKEN, COURSE_ID))
                .isInstanceOf(ServiceUnavailableException.class).hasMessageNotContaining(TOKEN);
    }

    // ---- what each call sends and returns ----

    @Test
    @DisplayName("listManagedCourses maps every field, and an empty list is an empty list")
    void listManagedCourses() {
        stub("GET", COURSES_PATH, 200, "{\"courses\": [" + COURSE_JSON + "]}");
        assertThat(adapter.listManagedCourses(TOKEN)).containsExactly(COURSE);

        stub("GET", COURSES_PATH, 200, "{\"courses\": []}");
        assertThat(adapter.listManagedCourses(TOKEN)).isEmpty();
    }

    @Test
    @DisplayName("getManagedCourse maps the course, its modules and lessons, a lesson without video and the stats")
    void getManagedCourse() {
        stub("GET", COURSE_PATH, 200, DETAIL_JSON);

        assertThat(adapter.getManagedCourse(TOKEN, COURSE_ID)).isEqualTo(DETAIL);
    }

    @Test
    @DisplayName("a level the BFF does not know maps to UNKNOWN in the list, and so does a status in the detail")
    void unknownEnumValues() {
        stub("GET", COURSES_PATH, 200, "{\"courses\": [" + COURSE_JSON.replace("BEGINNER", "EXPERT") + "]}");
        stub("GET", COURSE_PATH, 200, DETAIL_JSON.replace("PUBLISHED", "SUSPENDED"));

        assertThat(adapter.listManagedCourses(TOKEN)).singleElement()
                .satisfies(course -> assertThat(course.level()).isEqualTo(VirtualCourseLevel.UNKNOWN));
        assertThat(adapter.getManagedCourse(TOKEN, COURSE_ID).status()).isEqualTo(VirtualCourseStatus.UNKNOWN);
    }

    static Stream<Arguments> missingFields() {
        return Stream.of(
                Arguments.of("list", COURSE_JSON, "", "title"),
                Arguments.of("detail", DETAIL_JSON, "", "title"),
                Arguments.of("detail", DETAIL_JSON, "modules/0", "title"),
                Arguments.of("detail", DETAIL_JSON, "modules/0/lessons/0", "title"));
    }

    @ParameterizedTest(name = "{0}: {2}/{3} missing")
    @MethodSource("missingFields")
    @DisplayName("a list item, a course, a module or a lesson missing a required field is unavailable")
    void missingFieldIsUnavailable(String kind, String json, String at, String field) throws Exception {
        String body = without(json, at, field);
        if (kind.equals("list")) {
            stub("GET", COURSES_PATH, 200, "{\"courses\": [" + body + "]}");
            assertThatThrownBy(() -> adapter.listManagedCourses(TOKEN)).isInstanceOf(ServiceUnavailableException.class);
        } else {
            stub("GET", COURSE_PATH, 200, body);
            assertThatThrownBy(() -> adapter.getManagedCourse(TOKEN, COURSE_ID))
                    .isInstanceOf(ServiceUnavailableException.class);
        }
    }

    @Test
    @DisplayName("a lesson whose videoId is absent has no video, as when it is null")
    void absentVideoIdIsNull() throws Exception {
        stub("GET", COURSE_PATH, 200, without(DETAIL_JSON, "modules/0/lessons/1", "videoId"));

        assertThat(adapter.getManagedCourse(TOKEN, COURSE_ID)).isEqualTo(DETAIL);
    }

    private static String without(String json, String at, String field) throws Exception {
        var node = JSON.readTree(json);
        ((ObjectNode) (at.isEmpty() ? node : node.at("/" + at))).remove(field);
        return node.toString();
    }

    @Test
    @DisplayName("createCourse sends the six fields, plus professorId only when there is one, and maps the 201")
    void createCourse() {
        stub("POST", COURSES_PATH, 201, COURSE_JSON);

        assertThat(adapter.createCourse(TOKEN, creation(PROFESSOR_ID))).isEqualTo(COURSE);
        adapter.createCourse(TOKEN, creation(null));

        String base =
                """
                {"title": "Tango", "shortDescription": "Pasos base", "description": "Curso completo",
                 "imageUrl": "https://img.example/t.png", "category": "Tango", "level": "BEGINNER"%s}
                """;
        verify(postRequestedFor(urlPathEqualTo(COURSES_PATH)).withRequestBody(
                equalToJson(base.formatted(", \"professorId\": \"" + PROFESSOR_ID + "\""), false, false)));
        verify(postRequestedFor(urlPathEqualTo(COURSES_PATH)).withRequestBody(
                equalToJson(base.formatted(""), false, false)));
    }

    @Test
    @DisplayName("updateCourse is a partial PUT: it sends only the fields that change, and an empty body for none")
    void updateCourse() {
        stub("PUT", COURSE_PATH, 200, COURSE_JSON);

        assertThat(adapter.updateCourse(TOKEN, COURSE_ID, new VirtualCourseChanges("Tango", null, "Nueva", null,
                null, VirtualCourseLevel.ADVANCED, false))).isEqualTo(COURSE);
        adapter.updateCourse(TOKEN, COURSE_ID, new VirtualCourseChanges(null, null, null, null, null, null, null));

        verify(putRequestedFor(urlPathEqualTo(COURSE_PATH)).withRequestBody(equalToJson(
                "{\"title\": \"Tango\", \"description\": \"Nueva\", \"level\": \"ADVANCED\", \"premium\": false}",
                false, false)));
        verify(putRequestedFor(urlPathEqualTo(COURSE_PATH)).withRequestBody(equalToJson("{}", false, false)));
    }

    @Test
    @DisplayName("publish and unpublish map the course they answer with, and delete answers 204 without a body")
    void lifecycle() {
        stub("PUT", COURSE_PATH + "/publish", 200, COURSE_JSON.replace("DRAFT", "PUBLISHED"));
        stub("PUT", COURSE_PATH + "/unpublish", 200, COURSE_JSON);
        stub("DELETE", COURSE_PATH, 204, "");

        assertThat(adapter.publishCourse(TOKEN, COURSE_ID).status()).isEqualTo(VirtualCourseStatus.PUBLISHED);
        assertThat(adapter.unpublishCourse(TOKEN, COURSE_ID).status()).isEqualTo(VirtualCourseStatus.DRAFT);
        adapter.deleteCourse(TOKEN, COURSE_ID);

        verify(deleteRequestedFor(urlPathEqualTo(COURSE_PATH)));
    }

    // ---- the structure: modules and lessons ----

    private static void verifyBody(String method, String path, String json) {
        verify(requestedFor(method, urlPathEqualTo(path)).withRequestBody(equalToJson(json, false, false)));
    }

    @Test
    @DisplayName("createModule sends only the fields it has, so the API's defaults apply, and maps the 201")
    void createModule() {
        stub("POST", MODULES_PATH, 201, MODULE_JSON);

        assertThat(adapter.createModule(TOKEN, COURSE_ID, new CreateVirtualModuleCommand("Base", true, 0)))
                .isEqualTo(MODULE);
        adapter.createModule(TOKEN, COURSE_ID, new CreateVirtualModuleCommand("Base", null, null));

        verifyBody("POST", MODULES_PATH, "{\"title\": \"Base\", \"preview\": true, \"order\": 0}");
        verifyBody("POST", MODULES_PATH, "{\"title\": \"Base\"}");
    }

    @Test
    @DisplayName("updateModule is a partial PUT that sends only what changes, and maps the 200")
    void updateModule() {
        stub("PUT", MODULE_PATH, 200, MODULE_JSON);

        assertThat(adapter.updateModule(TOKEN, MODULE_ID, new VirtualModuleChanges(null, false, 4)))
                .isEqualTo(MODULE);
        adapter.updateModule(TOKEN, MODULE_ID, new VirtualModuleChanges("Nuevo", null, null));

        verifyBody("PUT", MODULE_PATH, "{\"preview\": false, \"order\": 4}");
        verifyBody("PUT", MODULE_PATH, "{\"title\": \"Nuevo\"}");
    }

    @Test
    @DisplayName("reorderModules sends the ids in order and returns the modules as the API answers them")
    void reorderModules() {
        stub("PUT", REORDER_PATH, 200, "{\"modules\": [" + MODULE_2_JSON + ", " + MODULE_JSON + "]}");

        assertThat(adapter.reorderModules(TOKEN, COURSE_ID, List.of(MODULE_2_ID, MODULE_ID)))
                .containsExactly(new ManagedVirtualModule(MODULE_2_ID, COURSE_ID, "Base", true, 3), MODULE);

        verifyBody("PUT", REORDER_PATH, "{\"moduleIds\": [\"" + MODULE_2_ID + "\", \"" + MODULE_ID + "\"]}");
    }

    @Test
    @DisplayName("createLesson sends only the fields it has, so the API's defaults apply, and maps the 201")
    void createLesson() {
        stub("POST", LESSONS_PATH, 201, LESSON_JSON);

        assertThat(adapter.createLesson(TOKEN, MODULE_ID,
                new CreateVirtualLessonCommand("Abrazo", "El abrazo", "v-1", 12, true, 1))).isEqualTo(LESSON);
        adapter.createLesson(TOKEN, MODULE_ID,
                new CreateVirtualLessonCommand("Abrazo", "El abrazo", null, null, null, null));

        verifyBody("POST", LESSONS_PATH,
                "{\"title\": \"Abrazo\", \"description\": \"El abrazo\", \"videoId\": \"v-1\", "
                        + "\"durationMinutes\": 12, \"free\": true, \"order\": 1}");
        verifyBody("POST", LESSONS_PATH, "{\"title\": \"Abrazo\", \"description\": \"El abrazo\"}");
    }

    @Test
    @DisplayName("updateLesson is a partial PUT that sends only what changes, and maps the 200")
    void updateLesson() {
        stub("PUT", LESSON_PATH, 200, LESSON_JSON);

        assertThat(adapter.updateLesson(TOKEN, LESSON_ID,
                new VirtualLessonChanges(null, "Nueva", "v-2", 0, false, null))).isEqualTo(LESSON);
        adapter.updateLesson(TOKEN, LESSON_ID, new VirtualLessonChanges(null, null, null, null, null, 3));

        verifyBody("PUT", LESSON_PATH,
                "{\"description\": \"Nueva\", \"videoId\": \"v-2\", \"durationMinutes\": 0, \"free\": false}");
        verifyBody("PUT", LESSON_PATH, "{\"order\": 3}");
    }

    @Test
    @DisplayName("a lesson without videoId, absent or null, has no video; the list of modules may be empty")
    void absentVideoAndEmptyModules() throws Exception {
        stub("POST", LESSONS_PATH, 201, without(LESSON_JSON, "", "videoId"));
        stub("PUT", LESSON_PATH, 200, LESSON_JSON.replace("\"v-1\"", "null"));
        stub("PUT", REORDER_PATH, 200, "{\"modules\": []}");

        assertThat(adapter.createLesson(TOKEN, MODULE_ID,
                new CreateVirtualLessonCommand("Abrazo", "El abrazo", null, null, null, null)).videoId()).isNull();
        assertThat(adapter.updateLesson(TOKEN, LESSON_ID,
                new VirtualLessonChanges("Abrazo", null, null, null, null, null)).videoId()).isNull();
        assertThat(adapter.reorderModules(TOKEN, COURSE_ID, List.of(MODULE_ID))).isEmpty();
    }

    static Stream<Arguments> missingStructureFields() {
        return Stream.of(
                Arguments.of("POST", MODULES_PATH, 201, MODULE_JSON, "preview"),
                Arguments.of("PUT", MODULE_PATH, 200, MODULE_JSON, "order"),
                Arguments.of("POST", LESSONS_PATH, 201, LESSON_JSON, "description"),
                Arguments.of("PUT", LESSON_PATH, 200, LESSON_JSON, "free"));
    }

    @ParameterizedTest(name = "{0} {1} without {4}")
    @MethodSource("missingStructureFields")
    @DisplayName("a module or a lesson that lacks a required field is unavailable")
    void missingStructureFieldIsUnavailable(String method, String path, int status, String json, String field)
            throws Exception {
        stub(method, path, status, without(json, "", field));

        assertThatThrownBy(() -> operations().stream().filter(o -> o.path().equals(path) && o.method().equals(method))
                .findFirst().orElseThrow().call().accept(adapter)).isInstanceOf(ServiceUnavailableException.class);
    }

    @Test
    @DisplayName("a reordered module that lacks a required field is unavailable")
    void reorderedModuleMissingFieldIsUnavailable() throws Exception {
        stub("PUT", REORDER_PATH, 200, "{\"modules\": [" + without(MODULE_JSON, "", "title") + "]}");

        assertThatThrownBy(() -> adapter.reorderModules(TOKEN, COURSE_ID, List.of(MODULE_ID)))
                .isInstanceOf(ServiceUnavailableException.class);
    }
}
