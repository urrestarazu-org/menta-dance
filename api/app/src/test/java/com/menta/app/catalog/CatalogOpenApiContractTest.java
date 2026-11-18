package com.menta.app.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.menta.physical.application.dto.PhysicalCourseSummary;
import com.menta.physical.application.dto.PhysicalSessionAvailability;
import com.menta.physical.domain.model.PhysicalCourseLevel;
import com.menta.virtual.application.dto.VirtualCourseDetailView;
import com.menta.virtual.application.dto.VirtualCourseStats;
import com.menta.virtual.application.dto.VirtualCourseSummary;
import com.menta.virtual.application.dto.VirtualLessonSummary;
import com.menta.virtual.application.dto.VirtualModuleDetail;
import com.menta.virtual.domain.model.CourseLevel;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.web.bind.annotation.RequestMapping;
import org.yaml.snakeyaml.Yaml;

/**
 * Guards drift between {@link CatalogController} and its responses and {@code api/openapi/catalog-v1.yaml}
 * (#774). The yaml is hand-written, so this is what fails when a route, a response shape, an enum or an error
 * code changes without the contract. The schemas are compared with what Jackson really writes: it writes
 * {@code null} instead of omitting, so a response requires every property and only the ones that can be
 * {@code null} may declare it.
 */
class CatalogOpenApiContractTest {

    private static final String LIST = "GET /api/v1/catalog/courses";
    private static final String DETAIL = "GET /api/v1/catalog/courses/{courseId}";

    private static Map<String, Object> spec;

    // Same defaults as Spring Boot's mapper.
    private final ObjectMapper mapper = Jackson2ObjectMapperBuilder.json()
        .featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS).build();

    @BeforeAll
    static void loadSpec() throws IOException {
        // Gradle runs module tests with the module directory as the working directory.
        try (InputStream in = Files.newInputStream(Path.of("..", "openapi", "catalog-v1.yaml"))) {
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

    private static String refName(String ref) {
        return ref.substring(ref.lastIndexOf('/') + 1);
    }

    /** Follows {@code $ref} and takes the non-null branch of a {@code oneOf}. */
    private static Map<String, Object> resolve(Map<String, Object> node) {
        if (node.get("$ref") instanceof String ref) {
            return resolve(schema(refName(ref)));
        }
        if (node.get("oneOf") != null && list(node.get("oneOf")).stream()
            .anyMatch(option -> "null".equals(asMap(option).get("type")))) {
            return resolve(list(node.get("oneOf")).stream().map(CatalogOpenApiContractTest::asMap)
                .filter(option -> !"null".equals(option.get("type"))).findFirst().orElseThrow());
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

    private static Set<String> propertyNames(Map<String, Object> node) {
        return new TreeSet<>(map(node, "properties").keySet());
    }

    private static Set<String> nullableNames(Map<String, Object> node) {
        Set<String> names = new TreeSet<>();
        map(node, "properties").forEach((name, property) -> {
            Map<String, Object> declared = asMap(property);
            boolean nullBranch = declared.get("oneOf") != null && list(declared.get("oneOf")).stream()
                .anyMatch(option -> "null".equals(asMap(option).get("type")));
            if (nullBranch || declared.get("type") instanceof List<?> types && types.contains("null")) {
                names.add(name);
            }
        });
        return names;
    }

    private static Set<String> requiredNames(Map<String, Object> node) {
        Set<String> names = new TreeSet<>();
        if (node.get("required") != null) {
            list(node.get("required")).forEach(name -> names.add((String) name));
        }
        return names;
    }

    private static Set<String> csv(String values) {
        return new TreeSet<>(Arrays.asList(values.split(",")));
    }

    /** What the schema at {@code path} must look like for a sample the API wrote: same keys, same nulls. */
    private static void assertWire(JsonNode json, String path, String... nullable) {
        Map<String, Object> node = at(path);
        Set<String> keys = new TreeSet<>();
        json.fieldNames().forEachRemaining(keys::add);
        assertThat(propertyNames(node)).as("%s properties", path).isEqualTo(keys);
        assertThat(requiredNames(node)).as("%s required", path).isEqualTo(keys);
        assertThat(nullableNames(node)).as("%s nullable", path).isEqualTo(new TreeSet<>(Arrays.asList(nullable)));
    }

    private static Set<String> codeRoutes() {
        RequestMapping base = AnnotatedElementUtils.findMergedAnnotation(CatalogController.class, RequestMapping.class);
        Set<String> routes = new TreeSet<>();
        for (Method method : CatalogController.class.getDeclaredMethods()) {
            RequestMapping mapping = AnnotatedElementUtils.findMergedAnnotation(method, RequestMapping.class);
            if (mapping != null) {
                String suffix = mapping.path().length == 0 ? "" : mapping.path()[0];
                routes.add(mapping.method()[0].name() + " " + (base.path()[0] + suffix).replaceAll("\\{[^}]+}", "{}"));
            }
        }
        return routes;
    }

    /** The {@code code} of every problem example an operation documents, with {@code $ref} followed. */
    private static Set<String> documentedCodes(Map<String, Object> operation) {
        Set<String> codes = new TreeSet<>();
        map(operation, "responses").values().forEach(node -> {
            Map<String, Object> response = asMap(node);
            if (response.get("$ref") instanceof String ref) {
                response = map(map(map(spec, "components"), "responses"), refName(ref));
            }
            Map<String, Object> content = asMap(response.get("content"));
            if (content != null && content.get("application/problem+json") != null) {
                Map<String, Object> example = asMap(map(content, "application/problem+json").get("example"));
                codes.add(example == null ? "(no example)" : (String) example.get("code"));
            }
        });
        return codes;
    }

    @Test
    void both_production_routes_are_documented_and_nothing_else_is() {
        Set<String> documented = new TreeSet<>();
        map(spec, "paths").forEach((path, item) -> asMap(item).keySet().forEach(verb ->
            documented.add(verb.toUpperCase(Locale.ROOT) + " " + path.replaceAll("\\{[^}]+}", "{}"))));
        assertThat(documented).containsExactlyInAnyOrderElementsOf(codeRoutes());
    }

    @Test
    void every_route_documents_exactly_the_statuses_and_error_codes_the_service_can_answer() {
        // The listing is all-or-nothing (503); the detail also collapses a missing, inactive or malformed id into 404.
        Map<String, Set<String>> expected = new LinkedHashMap<>();
        expected.put(LIST, csv("200,503"));
        expected.put(DETAIL, csv("200,404,503"));
        Map<String, Set<String>> documented = new LinkedHashMap<>();
        expected.keySet().forEach(route ->
            documented.put(route, new TreeSet<>(map(operation(route), "responses").keySet())));
        assertThat(documented).isEqualTo(expected);

        assertThat(documentedCodes(operation(LIST))).containsExactlyInAnyOrder(codeOf503());
        assertThat(documentedCodes(operation(DETAIL))).containsExactlyInAnyOrder(codeOf404(), codeOf503());
        assertThat(list(at("ProblemDetail/code").get("enum"))).containsExactlyInAnyOrder(codeOf404(), codeOf503());
        // Both routes are public: the contract must not ask for a token.
        assertThat(operation(LIST).get("security")).isEqualTo(List.of());
        assertThat(operation(DETAIL).get("security")).isEqualTo(List.of());
    }

    private static String codeOf404() {
        return new CourseNotFoundException().getErrorCode();
    }

    private static String codeOf503() {
        return new CatalogUpstreamException("physical", new IllegalStateException()).getErrorCode();
    }

    @Test
    void the_problem_schema_matches_what_the_handler_writes() {
        JsonNode json = mapper.valueToTree(ProblemDetails.body(HttpStatus.SERVICE_UNAVAILABLE, "x", codeOf503()));
        assertWire(json, "ProblemDetail");
    }

    @Test
    void the_list_schemas_match_what_jackson_writes_and_the_two_blocks_are_exclusive() {
        JsonNode json = mapper.valueToTree(new CatalogListResponse(List.of(
            CatalogCourseResponse.fromPhysical(physicalCourse()), CatalogCourseResponse.fromVirtual(virtualCourse()))));
        assertWire(json, "CatalogListResponse");
        JsonNode physical = json.get("courses").get(0);
        JsonNode virtual = json.get("courses").get(1);
        assertThat(physical.get("virtual").isNull()).isTrue();
        assertThat(virtual.get("physical").isNull()).isTrue();
        assertWire(physical, "CatalogListResponse/courses/[]", "physical", "virtual");
        assertWire(physical.get("physical"), "PhysicalCatalogBlock");
        assertWire(virtual.get("virtual"), "VirtualCatalogBlock");
    }

    @Test
    void the_detail_schemas_match_what_jackson_writes() {
        JsonNode virtual = mapper.valueToTree(CatalogCourseDetailResponse.fromVirtual(new VirtualCourseDetailView(
            "c1", "Tango", "Larga", "https://cdn/t.jpg", "tango", "BEGINNER", true,
            List.of(new VirtualModuleDetail("m1", "Intro", 1,
                List.of(new VirtualLessonSummary("l1", "Historia", 10, true, 1)))),
            new VirtualCourseStats(1, 1, 10))));
        assertWire(virtual, "CatalogVirtualCourseDetail");
        assertWire(virtual.get("modules").get(0), "CatalogVirtualCourseDetail/modules/[]");
        assertWire(virtual.get("modules").get(0).get("lessons").get(0),
            "CatalogVirtualCourseDetail/modules/[]/lessons/[]");
        assertWire(virtual.get("stats"), "CatalogVirtualCourseDetail/stats");

        JsonNode physical = mapper.valueToTree(CatalogPhysicalCourseDetailResponse.from(physicalCourse(),
            List.of(new PhysicalSessionAvailability("s1", "p1", "2026-11-20T01:00:00Z", 20, 3, 2, 15))));
        assertWire(physical, "CatalogPhysicalCourseDetail");
        assertWire(physical.get("physical"), "CatalogPhysicalCourseDetail/physical");
        // assignedSpots and activeCapacityHolds are internal: only availableSpots is published.
        assertWire(physical.get("physical").get("sessions").get(0), "CatalogPublicSession");
    }

    @Test
    void the_enums_match_the_domain_and_the_time_example_is_the_form_the_response_emits() {
        List<String> levels = Arrays.stream(CourseLevel.values()).map(Enum::name).toList();
        assertThat(levels).isEqualTo(Arrays.stream(PhysicalCourseLevel.values()).map(Enum::name).toList());
        List<String> days = Arrays.stream(DayOfWeek.values()).map(Enum::name).collect(Collectors.toList());
        assertThat(at("CatalogCourseResponse/modality").get("enum"))
            .isEqualTo(Arrays.stream(CourseModality.values()).map(Enum::name).toList());
        for (String path : List.of("CatalogCourseResponse/level", "CatalogVirtualCourseDetail/level",
            "CatalogPhysicalCourseDetail/level")) {
            assertThat(at(path).get("enum")).as(path).isEqualTo(levels);
        }
        for (String path : List.of(
            "PhysicalCatalogBlock/dayOfWeek", "CatalogPhysicalCourseDetail/physical/dayOfWeek")) {
            assertThat(at(path).get("enum")).as(path).isEqualTo(days);
        }
        // PhysicalCourseAvailabilityPortImpl writes LocalTime.toString(): HH:mm, with seconds only when not zero.
        for (String path : List.of(
            "PhysicalCatalogBlock/startTime", "CatalogPhysicalCourseDetail/physical/startTime")) {
            Map<String, Object> startTime = at(path);
            Object example = startTime.get("example");
            assertThat(LocalTime.parse((String) example).toString()).as(path).isEqualTo(example);
            Pattern pattern = Pattern.compile((String) startTime.get("pattern"));
            assertThat(List.of(LocalTime.of(1, 0), LocalTime.of(1, 0, 30), LocalTime.of(19, 15, 0, 500_000_000)))
                .allSatisfy(time -> assertThat(pattern.matcher(time.toString()).matches())
                    .as(time.toString()).isTrue());
        }
    }

    private static PhysicalCourseSummary physicalCourse() {
        return new PhysicalCourseSummary("p1", "Salsa inicial", "María García", "TUESDAY", "19:00", "BEGINNER", 20);
    }

    private static VirtualCourseSummary virtualCourse() {
        return new VirtualCourseSummary(
            "v1", "Tango", "Corta", "https://cdn/t.jpg", "tango", "BEGINNER", true, 5, 20, 150);
    }
}
