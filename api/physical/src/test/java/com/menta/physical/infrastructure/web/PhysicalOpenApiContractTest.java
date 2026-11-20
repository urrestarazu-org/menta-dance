package com.menta.physical.infrastructure.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.menta.physical.application.dto.AttendanceStatus;
import com.menta.physical.application.dto.RosterAttendanceStatus;
import com.menta.physical.domain.model.AttendanceKind;
import com.menta.physical.domain.model.CourseStatus;
import com.menta.physical.domain.model.DeviceStatus;
import com.menta.physical.domain.model.PhysicalCourseLevel;
import com.menta.physical.domain.model.SessionStatus;
import com.menta.physical.infrastructure.web.dto.AccessQrResponse;
import com.menta.physical.infrastructure.web.dto.AttendanceHistoryResponse;
import com.menta.physical.infrastructure.web.dto.AttendanceSessionResponse;
import com.menta.physical.infrastructure.web.dto.BatchCreatePhysicalSessionsRequest;
import com.menta.physical.infrastructure.web.dto.CheckInRequest;
import com.menta.physical.infrastructure.web.dto.CheckInResponse;
import com.menta.physical.infrastructure.web.dto.CreatePhysicalCourseRequest;
import com.menta.physical.infrastructure.web.dto.CreatePhysicalSessionRequest;
import com.menta.physical.infrastructure.web.dto.MySessionsResponse;
import com.menta.physical.infrastructure.web.dto.PhysicalCourseManagementListResponse;
import com.menta.physical.infrastructure.web.dto.PhysicalCourseManagementResponse;
import com.menta.physical.infrastructure.web.dto.PhysicalDeviceListResponse;
import com.menta.physical.infrastructure.web.dto.PhysicalDeviceResponse;
import com.menta.physical.infrastructure.web.dto.PhysicalDeviceSecretResponse;
import com.menta.physical.infrastructure.web.dto.PhysicalRangeSessionsResponse;
import com.menta.physical.infrastructure.web.dto.PhysicalSessionManagementListResponse;
import com.menta.physical.infrastructure.web.dto.PhysicalSessionManagementResponse;
import com.menta.physical.infrastructure.web.dto.PhysicalSessionRosterResponse;
import com.menta.physical.infrastructure.web.dto.RegisterPhysicalDeviceRequest;
import com.menta.physical.infrastructure.web.dto.UpdatePhysicalCourseRequest;
import com.menta.physical.infrastructure.web.dto.UpdatePhysicalSessionRequest;
import com.menta.shared.physical.AssignmentType;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Predicate;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.yaml.snakeyaml.Yaml;

/**
 * Guards drift between the physical controllers/DTOs and {@code api/openapi/physical-v1.yaml} (#774).
 * The yaml is hand-written, so this is what fails when a route, a record or a domain enum changes
 * without the contract: the route set is read from the real controllers, and the schemas from the
 * real records (Jackson writes {@code null} instead of omitting, so a response requires every
 * component and only the components that can really be {@code null} may declare it).
 */
class PhysicalOpenApiContractTest {

    private static final String COURSES = "/api/v1/admin/physical/courses";
    private static final String COURSE = COURSES + "/{courseId}";
    private static final String ADMIN_SESSIONS = "/api/v1/admin/physical/sessions";
    private static final String DEVICES = "/api/v1/admin/physical/devices";
    private static final String DEVICE = DEVICES + "/{deviceId}";
    private static final String STUDENT_SESSION = "/api/v1/physical/sessions/{sessionId}";
    private static final Set<String> VALIDATION_REQUIRED = Set.of("NotNull", "NotBlank", "NotEmpty");

    private static Map<String, Object> spec;

    @BeforeAll
    static void loadSpec() throws IOException {
        // Gradle runs module tests with the module directory as the working directory.
        try (InputStream in = Files.newInputStream(Path.of("..", "openapi", "physical-v1.yaml"))) {
            spec = new Yaml().load(in);
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Object parent, String key) {
        Object value = ((Map<String, Object>) parent).get(key);
        assertThat(value).as("yaml key '%s'", key).isInstanceOf(Map.class);
        return (Map<String, Object>) value;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object value) {
        return (Map<String, Object>) value;
    }

    @SuppressWarnings("unchecked")
    private static List<Object> list(Object value) {
        return (List<Object>) value;
    }

    private static Map<String, Object> schema(String name) {
        return map(map(map(spec, "components"), "schemas"), name);
    }

    private static Map<String, Object> operation(String route) {
        String[] parts = route.split(" ", 2);
        return map(map(map(spec, "paths"), parts[1]), parts[0].toLowerCase(Locale.ROOT));
    }

    /** Follows {@code $ref}, takes the non-null branch of a {@code oneOf} and merges an {@code allOf}. */
    private static Map<String, Object> resolve(Map<String, Object> node) {
        if (node.get("$ref") instanceof String ref) {
            return resolve(schema(ref.substring(ref.lastIndexOf('/') + 1)));
        }
        if (node.get("oneOf") != null) {
            return resolve(list(node.get("oneOf")).stream()
                .map(PhysicalOpenApiContractTest::asMap)
                .filter(option -> !"null".equals(option.get("type")))
                .findFirst().orElseThrow());
        }
        if (node.get("allOf") != null) {
            Map<String, Object> properties = new LinkedHashMap<>();
            List<Object> required = new ArrayList<>();
            for (Object part : list(node.get("allOf"))) {
                Map<String, Object> resolved = resolve(asMap(part));
                if (resolved.get("properties") != null) {
                    properties.putAll(map(resolved, "properties"));
                }
                if (resolved.get("required") != null) {
                    required.addAll(list(resolved.get("required")));
                }
            }
            return Map.of("properties", properties, "required", required);
        }
        return node;
    }

    /** A schema addressed as {@code Name/property/[]/property}, where {@code []} steps into array items. */
    private static Map<String, Object> at(String path) {
        String[] steps = path.split("/");
        Map<String, Object> node = schema(steps[0]);
        for (int i = 1; i < steps.length; i++) {
            Map<String, Object> resolved = resolve(node);
            node = "[]".equals(steps[i]) ? map(resolved, "items") : map(map(resolved, "properties"), steps[i]);
        }
        return resolve(node);
    }

    private static boolean nullable(Map<String, Object> property) {
        Object type = property.get("type");
        return "null".equals(type) || type instanceof List<?> types && types.contains("null");
    }

    private static Set<String> propertyNames(Map<String, Object> node) {
        return new TreeSet<>(map(node, "properties").keySet());
    }

    private static Set<String> nullableNames(Map<String, Object> node) {
        Set<String> names = new TreeSet<>();
        map(node, "properties").forEach((name, property) -> {
            if (nullable(asMap(property))) {
                names.add(name);
            }
        });
        return names;
    }

    private static Set<String> requiredNames(Map<String, Object> node) {
        Object required = node.get("required");
        Set<String> names = new TreeSet<>();
        if (required != null) {
            list(required).forEach(name -> names.add((String) name));
        }
        return names;
    }

    private static Set<String> components(Class<? extends Record> type) {
        return new TreeSet<>(Arrays.stream(type.getRecordComponents()).map(RecordComponent::getName).toList());
    }

    /** What a request must carry: a validated-present value, or a primitive that cannot be absent. */
    private static Set<String> mandatoryComponents(Class<? extends Record> type) {
        return new TreeSet<>(Arrays.stream(type.getRecordComponents())
            .filter(component -> component.getType().isPrimitive()
                || Arrays.stream(component.getAccessor().getAnnotations())
                    .anyMatch(annotation -> VALIDATION_REQUIRED.contains(annotation.annotationType().getSimpleName())))
            .map(RecordComponent::getName)
            .toList());
    }

    private static List<String> names(Class<? extends Enum<?>> type) {
        return Arrays.stream(type.getEnumConstants()).map(Enum::name).toList();
    }

    private static String normalized(String path) {
        return path.replaceAll("\\{[^}]+}", "{}");
    }

    private static Set<String> codeRoutes() {
        return routes(method -> true);
    }

    /** The routes whose handler reads a {@code @RequestBody}. */
    private static Set<String> bodyRoutes() {
        return routes(method -> Arrays.stream(method.getParameters())
            .anyMatch(parameter -> parameter.isAnnotationPresent(RequestBody.class)));
    }

    private static Set<String> routes(Predicate<Method> filter) {
        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(RestController.class));
        Set<String> routes = new TreeSet<>();
        for (BeanDefinition candidate : scanner.findCandidateComponents("com.menta.physical")) {
            Class<?> controller = load(candidate.getBeanClassName());
            // Some controllers have no class-level mapping: each method then declares its full path.
            RequestMapping base = AnnotatedElementUtils.findMergedAnnotation(controller, RequestMapping.class);
            String prefix = base == null || base.path().length == 0 ? "" : base.path()[0];
            for (Method method : controller.getDeclaredMethods()) {
                RequestMapping mapping = AnnotatedElementUtils.findMergedAnnotation(method, RequestMapping.class);
                if (mapping != null && filter.test(method)) {
                    String suffix = mapping.path().length == 0 ? "" : mapping.path()[0];
                    routes.add(mapping.method()[0].name() + " " + normalized(prefix + suffix));
                }
            }
        }
        return routes;
    }

    private static Class<?> load(String className) {
        try {
            return Class.forName(className);
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException(e);
        }
    }

    private static Set<String> documentedRoutes() {
        Set<String> routes = new TreeSet<>();
        map(spec, "paths").forEach((path, item) -> {
            for (Object verb : map(map(spec, "paths"), path).keySet()) {
                routes.add(verb.toString().toUpperCase(Locale.ROOT) + " " + normalized(path));
            }
        });
        return routes;
    }

    private static Set<String> statuses(String... codes) {
        return new TreeSet<>(Set.of(codes));
    }

    @Test
    void every_production_route_is_documented_and_nothing_else_is() {
        assertThat(documentedRoutes()).containsExactlyInAnyOrderElementsOf(codeRoutes());
    }

    @Test
    void every_route_documents_exactly_the_statuses_its_advice_and_security_rules_can_answer() {
        // Management (ADMIN or INSTRUCTOR by prefix): 401/403 come from SecurityConfig, 400 INVALID_REQUEST from a
        // malformed id or body, 404/403 from the course ownership guard.
        Map<String, Set<String>> expected = new LinkedHashMap<>();
        expected.put("POST " + COURSES, statuses("201", "400", "401", "403"));
        expected.put("GET " + COURSES, statuses("200", "401", "403"));
        expected.put("PATCH " + COURSE, statuses("200", "400", "401", "403", "404", "409"));
        expected.put("POST " + COURSE + "/sessions", statuses("201", "400", "401", "403", "404", "409"));
        expected.put("GET " + COURSE + "/sessions", statuses("200", "400", "401", "403", "404"));
        expected.put("POST " + COURSE + "/sessions/batch", statuses("201", "400", "401", "403", "404", "409"));
        expected.put("PATCH " + ADMIN_SESSIONS + "/{sessionId}", statuses("200", "400", "401", "403", "404", "409"));
        // Staff reads that also admit RECEPTIONIST.
        expected.put("GET " + ADMIN_SESSIONS, statuses("200", "400", "401", "403"));
        expected.put("GET " + ADMIN_SESSIONS + "/{sessionId}/roster", statuses("200", "400", "401", "403", "404"));
        // Student routes: sessions/me is STUDENT only, access-qr and attendance/me take any authenticated role.
        expected.put("GET /api/v1/physical/sessions/me", statuses("200", "400", "401", "403"));
        expected.put("POST " + STUDENT_SESSION + "/access-qr", statuses("200", "400", "401", "403", "404"));
        expected.put("GET /api/v1/physical/attendance/me", statuses("200", "400", "401"));
        expected.put("GET /api/v1/admin/physical/attendance/{studentId}", statuses("200", "400", "401", "403"));
        // The check-in is permitAll: its 401 is the device authentication of the QR variant, not the filter.
        expected.put("POST " + STUDENT_SESSION + "/check-ins",
            statuses("200", "201", "400", "401", "403", "404", "409", "410", "429", "503"));
        // Devices: ADMIN only.
        expected.put("POST " + DEVICES, statuses("201", "400", "401", "403"));
        expected.put("GET " + DEVICES, statuses("200", "401", "403"));
        expected.put("GET " + DEVICE, statuses("200", "400", "401", "403", "404"));
        expected.put("POST " + DEVICE + "/rotate-secret", statuses("200", "400", "401", "403", "404", "409"));
        expected.put("POST " + DEVICE + "/revoke", statuses("200", "400", "401", "403", "404", "409"));

        Map<String, Set<String>> documented = new LinkedHashMap<>();
        expected.keySet().forEach(route ->
            documented.put(route, new TreeSet<>(map(operation(route), "responses").keySet())));
        assertThat(documented).isEqualTo(expected);
    }

    @Test
    void every_error_response_carries_a_problem_json_body() {
        List<String> bare = new ArrayList<>();
        map(spec, "paths").forEach((path, item) -> asMap(item).forEach((verb, operation) ->
            map(operation, "responses").forEach((status, response) -> {
                Map<String, Object> resolved = asMap(response);
                if (resolved.get("$ref") instanceof String ref) {
                    resolved = map(map(spec, "components"), "responses");
                    resolved = map(resolved, ref.substring(ref.lastIndexOf('/') + 1));
                }
                boolean error = Integer.parseInt(status.toString()) >= 400;
                if (error && (resolved.get("content") == null
                    || !map(resolved, "content").containsKey("application/problem+json"))) {
                    bare.add(verb + " " + path + " " + status);
                }
            })));
        assertThat(bare).isEmpty();
    }

    @Test
    void every_route_with_a_body_documents_its_400_for_an_unreadable_or_empty_body() {
        // An unreadable (malformed JSON), empty or undecodable body (an unknown enum constant, for instance)
        // answers 400 INVALID_REQUEST on every route that reads one (#792). Each route must show that problem
        // among its 400 examples and say so in the response description.
        Set<String> routes = bodyRoutes();
        assertThat(routes).hasSize(7);

        List<String> undocumented = new ArrayList<>();
        for (String route : routes) {
            Map<String, Object> response = resolveResponse(map(map(documentedOperation(route), "responses"), "400"));
            Map<String, Object> examples = map(map(map(response, "content"), "application/problem+json"), "examples");
            boolean documented = examples.values().stream()
                .map(example -> map(example, "value"))
                .anyMatch(value -> "INVALID_REQUEST".equals(value.get("code"))
                    && "La solicitud contiene datos inválidos.".equals(value.get("detail")));
            if (!documented || !((String) response.get("description")).contains("ilegible")) {
                undocumented.add(route);
            }
        }
        assertThat(undocumented).isEmpty();
    }

    /** The operation of a route whose path parameters are normalized to {@code {}}. */
    private static Map<String, Object> documentedOperation(String route) {
        String[] parts = route.split(" ", 2);
        String path = map(spec, "paths").keySet().stream()
            .filter(candidate -> normalized(candidate).equals(parts[1]))
            .findFirst().orElseThrow();
        return operation(parts[0] + " " + path);
    }

    /** Follows a {@code $ref} into {@code components/responses}. */
    private static Map<String, Object> resolveResponse(Map<String, Object> response) {
        if (response.get("$ref") instanceof String ref) {
            return map(map(map(spec, "components"), "responses"), ref.substring(ref.lastIndexOf('/') + 1));
        }
        return response;
    }

    @Test
    void only_the_check_in_makes_the_token_optional() {
        assertThat(list(spec.get("security"))).isEqualTo(List.of(Map.of("bearerAuth", List.of())));
        assertThat(operation("POST " + STUDENT_SESSION + "/check-ins").get("security"))
            .isEqualTo(List.of(Map.of(), Map.of("bearerAuth", List.of())));
        long overridden = map(spec, "paths").values().stream()
            .flatMap(item -> asMap(item).values().stream())
            .filter(operation -> asMap(operation).get("security") != null)
            .count();
        assertThat(overridden).isEqualTo(1);
    }

    @Test
    void the_response_schemas_match_the_records_and_only_nullable_components_declare_null() {
        Map<String, Object[]> responses = new LinkedHashMap<>();
        responses.put("PhysicalCourseManagementResponse", new Object[] {PhysicalCourseManagementResponse.class});
        responses.put("PhysicalCourseManagementListResponse",
            new Object[] {PhysicalCourseManagementListResponse.class});
        responses.put("PhysicalSessionManagementResponse",
            new Object[] {PhysicalSessionManagementResponse.class, "notes"});
        responses.put("PhysicalSessionManagementListResponse",
            new Object[] {PhysicalSessionManagementListResponse.class});
        responses.put("PhysicalRangeSessionsResponse", new Object[] {PhysicalRangeSessionsResponse.class});
        responses.put("PhysicalRangeSessionResponse", new Object[] {PhysicalRangeSessionsResponse.Item.class});
        responses.put("PhysicalSessionRosterResponse", new Object[] {PhysicalSessionRosterResponse.class});
        responses.put("PhysicalSessionRosterResponse/session",
            new Object[] {PhysicalSessionRosterResponse.Session.class});
        responses.put("PhysicalRosterStudentResponse",
            new Object[] {PhysicalSessionRosterResponse.Student.class, "checkedInAt", "checkInKind"});
        responses.put("AccessQrResponse", new Object[] {AccessQrResponse.class});
        responses.put("CheckInResponse", new Object[] {CheckInResponse.class});
        responses.put("MySessionsResponse", new Object[] {MySessionsResponse.class});
        responses.put("MySessionResponse", new Object[] {MySessionsResponse.Item.class});
        responses.put("AttendanceHistoryResponse", new Object[] {AttendanceHistoryResponse.class});
        responses.put("AttendanceSessionResponse",
            new Object[] {AttendanceSessionResponse.class, "recordedAt", "kind"});
        responses.put("PhysicalDeviceResponse", new Object[] {PhysicalDeviceResponse.class, "expiresAt"});
        responses.put("PhysicalDeviceSecretResponse", new Object[] {PhysicalDeviceSecretResponse.class});
        responses.put("PhysicalDeviceListResponse", new Object[] {PhysicalDeviceListResponse.class});

        List<String> drift = new ArrayList<>();
        responses.forEach((path, expected) -> {
            @SuppressWarnings("unchecked")
            Class<? extends Record> type = (Class<? extends Record>) expected[0];
            Set<String> nullable = new TreeSet<>();
            for (int i = 1; i < expected.length; i++) {
                nullable.add((String) expected[i]);
            }
            Map<String, Object> node = at(path);
            if (!propertyNames(node).equals(components(type))) {
                drift.add(path + " properties " + propertyNames(node) + " vs " + components(type));
            }
            if (!requiredNames(node).equals(components(type))) {
                drift.add(path + " required " + requiredNames(node) + " vs " + components(type));
            }
            if (!nullableNames(node).equals(nullable)) {
                drift.add(path + " nullable " + nullableNames(node) + " vs " + nullable);
            }
        });
        assertThat(drift).isEmpty();
    }

    @Test
    void the_request_schemas_match_the_records_and_their_validation() {
        Map<String, Class<? extends Record>> requests = new LinkedHashMap<>();
        requests.put("CreatePhysicalCourseRequest", CreatePhysicalCourseRequest.class);
        requests.put("UpdatePhysicalCourseRequest", UpdatePhysicalCourseRequest.class);
        requests.put("CreatePhysicalSessionRequest", CreatePhysicalSessionRequest.class);
        requests.put("BatchCreatePhysicalSessionsRequest", BatchCreatePhysicalSessionsRequest.class);
        requests.put("UpdatePhysicalSessionRequest", UpdatePhysicalSessionRequest.class);
        requests.put("RegisterPhysicalDeviceRequest", RegisterPhysicalDeviceRequest.class);

        List<String> drift = new ArrayList<>();
        requests.forEach((name, type) -> {
            Map<String, Object> node = at(name);
            if (!propertyNames(node).equals(components(type))) {
                drift.add(name + " properties");
            }
            if (!requiredNames(node).equals(mandatoryComponents(type))) {
                drift.add(name + " required " + requiredNames(node) + " vs " + mandatoryComponents(type));
            }
            for (RecordComponent component : type.getRecordComponents()) {
                Map<String, Object> property = map(map(node, "properties"), component.getName());
                if (component.getAccessor().isAnnotationPresent(Positive.class)
                    && !Integer.valueOf(1).equals(property.get("minimum"))) {
                    drift.add(name + "/" + component.getName() + " minimum");
                }
                Size size = component.getAccessor().getAnnotation(Size.class);
                if (size != null && !Integer.valueOf(size.max()).equals(property.get("maxLength"))) {
                    drift.add(name + "/" + component.getName() + " maxLength");
                }
            }
        });
        assertThat(drift).isEmpty();
    }

    @Test
    void the_check_in_variants_split_the_record_and_keep_its_length_caps() {
        Map<String, Object> qr = at("CheckInQrRequest");
        Map<String, Object> manual = at("CheckInManualRequest");
        Set<String> union = new TreeSet<>(propertyNames(qr));
        union.addAll(propertyNames(manual));
        assertThat(union).isEqualTo(components(CheckInRequest.class));

        Pattern pattern = Arrays.stream(CheckInRequest.class.getRecordComponents())
            .filter(component -> component.getName().equals("type"))
            .findFirst().orElseThrow().getAccessor().getAnnotation(Pattern.class);
        assertThat(List.of(at("CheckInQrRequest/type").get("enum"), at("CheckInManualRequest/type").get("enum")))
            .extracting(values -> list(values).get(0))
            .containsExactlyInAnyOrder((Object[]) pattern.regexp().split("\\|"));
        assertThat(at("CheckInQrRequest/qrCredentials").get("maxLength"))
            .isEqualTo(CheckInRequest.QR_CREDENTIALS_MAX_LENGTH);
        assertThat(at("CheckInQrRequest/deviceId").get("maxLength")).isEqualTo(CheckInRequest.DEVICE_ID_MAX_LENGTH);
        assertThat(at("CheckInQrRequest/deviceToken").get("maxLength"))
            .isEqualTo(CheckInRequest.DEVICE_TOKEN_MAX_LENGTH);
    }

    @Test
    void the_enums_match_the_domain() {
        Map<Class<? extends Enum<?>>, List<String>> enums = new LinkedHashMap<>();
        enums.put(DayOfWeek.class, List.of("CreatePhysicalCourseRequest/dayOfWeek",
            "UpdatePhysicalCourseRequest/dayOfWeek", "PhysicalCourseManagementResponse/dayOfWeek"));
        enums.put(PhysicalCourseLevel.class, List.of("CreatePhysicalCourseRequest/level",
            "UpdatePhysicalCourseRequest/level", "PhysicalCourseManagementResponse/level"));
        enums.put(CourseStatus.class,
            List.of("UpdatePhysicalCourseRequest/status", "PhysicalCourseManagementResponse/status"));
        enums.put(SessionStatus.class, List.of("UpdatePhysicalSessionRequest/status",
            "PhysicalSessionManagementResponse/status", "PhysicalRangeSessionResponse/status",
            "PhysicalSessionRosterResponse/session/status"));
        enums.put(DeviceStatus.class, List.of("PhysicalDeviceResponse/status"));
        enums.put(AssignmentType.class,
            List.of("PhysicalRosterStudentResponse/assignmentType", "MySessionResponse/assignmentType"));
        enums.put(AttendanceKind.class, List.of("CheckInResponse/kind", "AttendanceSessionResponse/kind",
            "PhysicalRosterStudentResponse/checkInKind"));
        enums.put(AttendanceStatus.class, List.of("AttendanceSessionResponse/status"));
        enums.put(RosterAttendanceStatus.class, List.of("PhysicalRosterStudentResponse/attendanceStatus"));

        List<String> drift = new ArrayList<>();
        enums.forEach((type, paths) -> paths.forEach(path -> {
            List<Object> documented = new ArrayList<>(list(at(path).get("enum")));
            documented.removeIf(value -> value == null);
            if (!documented.equals(names(type))) {
                drift.add(path + " " + documented);
            }
        }));
        assertThat(drift).isEmpty();
    }

    @Test
    void the_course_start_time_example_is_the_form_the_response_really_emits() {
        // PhysicalCourseResultMapper writes LocalTime.toString(), which drops the seconds when they are zero.
        Object example = at("PhysicalCourseManagementResponse/startTime").get("example");
        assertThat(LocalTime.parse((String) example).toString()).isEqualTo(example);
    }
}
