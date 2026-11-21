package com.menta.virtual.infrastructure.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.menta.virtual.domain.model.CourseLevel;
import com.menta.virtual.domain.model.CourseStatus;
import com.menta.virtual.infrastructure.web.dto.CourseProgressResponse;
import com.menta.virtual.infrastructure.web.dto.CreateVirtualCourseRequest;
import com.menta.virtual.infrastructure.web.dto.CreateVirtualLessonRequest;
import com.menta.virtual.infrastructure.web.dto.CreateVirtualModuleRequest;
import com.menta.virtual.infrastructure.web.dto.InProgressCourseResponse;
import com.menta.virtual.infrastructure.web.dto.LessonProgressResponse;
import com.menta.virtual.infrastructure.web.dto.PublicCourseDto;
import com.menta.virtual.infrastructure.web.dto.PublicLessonAccessMetadataDto;
import com.menta.virtual.infrastructure.web.dto.PublicLessonDetailDto;
import com.menta.virtual.infrastructure.web.dto.PublicLessonFreeResponse;
import com.menta.virtual.infrastructure.web.dto.PublicLessonNavigationDto;
import com.menta.virtual.infrastructure.web.dto.PublicLessonPremiumAccessibleResponse;
import com.menta.virtual.infrastructure.web.dto.PublicLessonStreamResponse;
import com.menta.virtual.infrastructure.web.dto.PublicLessonSubscriptionPromptDto;
import com.menta.virtual.infrastructure.web.dto.PublicLessonSummaryRefDto;
import com.menta.virtual.infrastructure.web.dto.PublicModuleDto;
import com.menta.virtual.infrastructure.web.dto.PublicStreamQualityDto;
import com.menta.virtual.infrastructure.web.dto.ReorderVirtualModulesRequest;
import com.menta.virtual.infrastructure.web.dto.SaveLessonProgressRequest;
import com.menta.virtual.infrastructure.web.dto.UpdateVirtualCourseRequest;
import com.menta.virtual.infrastructure.web.dto.UpdateVirtualLessonRequest;
import com.menta.virtual.infrastructure.web.dto.UpdateVirtualModuleRequest;
import com.menta.virtual.infrastructure.web.dto.VirtualCourseAdminDetailResponse;
import com.menta.virtual.infrastructure.web.dto.VirtualCourseManagementListResponse;
import com.menta.virtual.infrastructure.web.dto.VirtualCourseManagementResponse;
import com.menta.virtual.infrastructure.web.dto.VirtualLessonManagementResponse;
import com.menta.virtual.infrastructure.web.dto.VirtualModuleManagementListResponse;
import com.menta.virtual.infrastructure.web.dto.VirtualModuleManagementResponse;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
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
 * Guards drift between the virtual controllers/DTOs and {@code api/openapi/virtual-v1.yaml} (#774).
 * The yaml is hand-written, so this is what fails when a route, a record or a domain enum changes
 * without the contract: the route set is read from the real controllers, and the schemas from the
 * real records (Jackson writes {@code null} instead of omitting, so a response requires every
 * component and only the components that can really be {@code null} may declare it).
 */
class VirtualOpenApiContractTest {

    private static final String COURSES = "/api/v1/admin/virtual/courses";
    private static final String COURSE = COURSES + "/{courseId}";
    private static final String MODULE = "/api/v1/admin/virtual/modules/{moduleId}";
    private static final String ADMIN_LESSON = "/api/v1/admin/virtual/lessons/{lessonId}";
    private static final String LESSON = "/api/v1/virtual/lessons/{lessonId}";
    private static final Set<String> VALIDATION_REQUIRED = Set.of("NotNull", "NotBlank", "NotEmpty");

    private static Map<String, Object> spec;

    @BeforeAll
    static void loadSpec() throws IOException {
        // Gradle runs module tests with the module directory as the working directory.
        try (InputStream in = Files.newInputStream(Path.of("..", "openapi", "virtual-v1.yaml"))) {
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
                .map(VirtualOpenApiContractTest::asMap)
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
        boolean typed = "null".equals(type) || type instanceof List<?> types && types.contains("null");
        boolean union = property.get("oneOf") != null && list(property.get("oneOf")).stream()
            .anyMatch(option -> option instanceof Map<?, ?> branch && "null".equals(branch.get("type")));
        return typed || union;
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

    private static Set<String> mandatoryComponents(Class<? extends Record> type) {
        return new TreeSet<>(Arrays.stream(type.getRecordComponents())
            .filter(component -> Arrays.stream(component.getAccessor().getAnnotations())
                .anyMatch(annotation -> VALIDATION_REQUIRED.contains(annotation.annotationType().getSimpleName())))
            .map(RecordComponent::getName)
            .toList());
    }

    private static <E extends Enum<E>> List<String> names(Class<E> type) {
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

    private static Set<String> routes(java.util.function.Predicate<Method> filter) {
        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(RestController.class));
        Set<String> routes = new TreeSet<>();
        for (BeanDefinition candidate : scanner.findCandidateComponents("com.menta.virtual")) {
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

    private static Set<String> documentedStatuses(String route) {
        return new TreeSet<>(map(operation(route), "responses").keySet());
    }

    @Test
    void every_production_route_is_documented_and_nothing_else_is() {
        assertThat(documentedRoutes()).containsExactlyInAnyOrderElementsOf(codeRoutes());
    }

    @Test
    void every_route_documents_exactly_the_statuses_its_advice_and_security_rules_can_answer() {
        // Management: 401/403 come from SecurityConfig (ADMIN or INSTRUCTOR), 400 INVALID_REQUEST from a malformed
        // id or body, 404/403 from the course ownership guard. The reads that load nothing by id have no 404.
        Map<String, Set<String>> expected = new LinkedHashMap<>();
        expected.put("POST " + COURSES, new TreeSet<>(Set.of("201", "400", "401", "403")));
        expected.put("GET " + COURSES, new TreeSet<>(Set.of("200", "401", "403")));
        expected.put("GET " + COURSE, new TreeSet<>(Set.of("200", "401", "403", "404")));
        expected.put("PUT " + COURSE, new TreeSet<>(Set.of("200", "400", "401", "403", "404")));
        expected.put("DELETE " + COURSE, new TreeSet<>(Set.of("204", "400", "401", "403", "404", "409")));
        expected.put("PUT " + COURSE + "/publish", new TreeSet<>(Set.of("200", "400", "401", "403", "404")));
        expected.put("PUT " + COURSE + "/unpublish", new TreeSet<>(Set.of("200", "400", "401", "403", "404")));
        expected.put("POST " + COURSE + "/modules", new TreeSet<>(Set.of("201", "400", "401", "403", "404")));
        expected.put("PUT " + COURSE + "/modules/reorder", new TreeSet<>(Set.of("200", "400", "401", "403", "404")));
        expected.put("PUT " + MODULE, new TreeSet<>(Set.of("200", "400", "401", "403", "404")));
        expected.put("POST " + MODULE + "/lessons", new TreeSet<>(Set.of("201", "400", "401", "403", "404")));
        expected.put("PUT " + ADMIN_LESSON, new TreeSet<>(Set.of("200", "400", "401", "403", "404")));
        // Public reads: the token is optional, so there is no 401; a malformed id collapses into the 404.
        expected.put("GET " + LESSON, new TreeSet<>(Set.of("200", "403", "404")));
        expected.put("GET " + LESSON + "/stream", new TreeSet<>(Set.of("200", "403", "404")));
        // Student: any authenticated role (401), malformed ids collapse into the 404.
        expected.put("GET /api/v1/virtual/courses/me/in-progress", new TreeSet<>(Set.of("200", "400", "401")));
        expected.put("GET /api/v1/virtual/courses/{courseId}/progress",
            new TreeSet<>(Set.of("200", "401", "403", "404")));
        expected.put("PUT " + LESSON + "/progress", new TreeSet<>(Set.of("200", "400", "401", "403", "404")));
        expected.put("GET " + LESSON + "/progress", new TreeSet<>(Set.of("200", "401", "403", "404")));
        expected.put("POST " + LESSON + "/complete", new TreeSet<>(Set.of("200", "401", "403", "404")));

        Map<String, Set<String>> documented = new LinkedHashMap<>();
        expected.keySet().forEach(route -> documented.put(route, documentedStatuses(route)));
        assertThat(documented).isEqualTo(expected);
    }

    @Test
    void every_route_with_a_body_documents_a_400_problem_with_the_invalid_request_code() {
        // An unreadable or empty body (and a bean-validation failure) answers 400 INVALID_REQUEST on every
        // route that reads a body (#790), so each one must show that problem among its 400 examples.
        Set<String> routes = bodyRoutes();
        assertThat(routes).isNotEmpty();

        List<String> undocumented = new ArrayList<>();
        for (String route : routes) {
            Map<String, Object> response = resolveResponse(map(map(documentedOperation(route), "responses"), "400"));
            Map<String, Object> examples = map(map(map(response, "content"), "application/problem+json"), "examples");
            boolean documented = examples.values().stream()
                .map(example -> map(example, "value").get("code"))
                .anyMatch(code -> "INVALID_REQUEST".equals(code));
            if (!documented) {
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
    void only_the_two_public_lesson_reads_make_the_token_optional() {
        assertThat(list(spec.get("security"))).isEqualTo(List.of(Map.of("bearerAuth", List.of())));
        for (String route : List.of("GET " + LESSON, "GET " + LESSON + "/stream")) {
            assertThat(operation(route).get("security")).as(route)
                .isEqualTo(List.of(Map.of(), Map.of("bearerAuth", List.of())));
        }
        long overridden = map(spec, "paths").values().stream()
            .flatMap(item -> asMap(item).values().stream())
            .map(operation -> asMap(operation).get("security"))
            .filter(security -> security != null && !security.equals(List.of(Map.of("bearerAuth", List.of()))))
            .count();
        assertThat(overridden).isEqualTo(2);
    }

    @Test
    void the_response_schemas_match_the_records_and_only_nullable_components_declare_null() {
        Map<String, Object[]> responses = new LinkedHashMap<>();
        responses.put("VirtualCourseManagementResponse", new Object[] {VirtualCourseManagementResponse.class});
        responses.put("VirtualCourseManagementListResponse", new Object[] {VirtualCourseManagementListResponse.class});
        responses.put("VirtualModuleManagementResponse", new Object[] {VirtualModuleManagementResponse.class});
        responses.put("VirtualModuleManagementListResponse",
            new Object[] {VirtualModuleManagementListResponse.class});
        responses.put("VirtualLessonManagementResponse",
            new Object[] {VirtualLessonManagementResponse.class, "videoId"});
        responses.put("VirtualCourseAdminDetailResponse", new Object[] {VirtualCourseAdminDetailResponse.class});
        responses.put("VirtualCourseAdminDetailResponse/modules/[]",
            new Object[] {VirtualCourseAdminDetailResponse.AdminModuleDetail.class});
        responses.put("VirtualCourseAdminDetailResponse/modules/[]/lessons/[]",
            new Object[] {VirtualCourseAdminDetailResponse.AdminLessonDetail.class, "videoId"});
        responses.put("VirtualCourseAdminDetailResponse/stats",
            new Object[] {VirtualCourseAdminDetailResponse.AdminStats.class});
        responses.put("LessonProgressResponse", new Object[] {LessonProgressResponse.class, "completedAt"});
        responses.put("CourseProgressResponse", new Object[] {CourseProgressResponse.class, "resumeLesson"});
        responses.put("CourseProgressResponse/resumeLesson",
            new Object[] {CourseProgressResponse.ResumeLesson.class});
        responses.put("InProgressCourseResponse", new Object[] {InProgressCourseResponse.class});
        responses.put("InProgressCourseResponse/resumeLesson",
            new Object[] {InProgressCourseResponse.ResumeLesson.class});
        // The free variant carries a lesson without a video and the upsell prompt; the other one carries both
        // the (possibly absent) video and no prompt.
        responses.put("PublicLessonPreviewResponse", new Object[] {PublicLessonFreeResponse.class});
        responses.put("PublicLessonPreviewResponse/lesson", new Object[] {PublicLessonDetailDto.class, "videoId"});
        responses.put("PublicLessonPreviewResponse/subscription",
            new Object[] {PublicLessonSubscriptionPromptDto.class});
        responses.put("PublicLessonEntitledResponse", new Object[] {PublicLessonPremiumAccessibleResponse.class});
        responses.put("PublicLessonEntitledResponse/lesson", new Object[] {PublicLessonDetailDto.class, "videoId"});
        responses.put("PublicLessonEntitledResponse/lesson/module", new Object[] {PublicModuleDto.class});
        responses.put("PublicLessonEntitledResponse/lesson/course", new Object[] {PublicCourseDto.class});
        responses.put("PublicLessonEntitledResponse/navigation",
            new Object[] {PublicLessonNavigationDto.class, "nextLesson", "previousLesson"});
        responses.put("PublicLessonEntitledResponse/navigation/nextLesson",
            new Object[] {PublicLessonSummaryRefDto.class});
        responses.put("PublicLessonEntitledResponse/access", new Object[] {PublicLessonAccessMetadataDto.class});
        responses.put("PublicLessonStreamResponse", new Object[] {PublicLessonStreamResponse.class});
        responses.put("PublicLessonStreamResponse/stream", new Object[] {PublicLessonStreamResponse.StreamBlock.class});
        responses.put("PublicLessonStreamResponse/stream/qualities/[]", new Object[] {PublicStreamQualityDto.class});
        responses.put("PublicLessonStreamResponse/lesson", new Object[] {PublicLessonStreamResponse.LessonRef.class});

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
    void the_request_schemas_match_the_records_and_require_what_validation_requires() {
        Map<String, Class<? extends Record>> requests = new LinkedHashMap<>();
        requests.put("CreateVirtualCourseRequest", CreateVirtualCourseRequest.class);
        requests.put("UpdateVirtualCourseRequest", UpdateVirtualCourseRequest.class);
        requests.put("CreateVirtualModuleRequest", CreateVirtualModuleRequest.class);
        requests.put("UpdateVirtualModuleRequest", UpdateVirtualModuleRequest.class);
        requests.put("ReorderVirtualModulesRequest", ReorderVirtualModulesRequest.class);
        requests.put("CreateVirtualLessonRequest", CreateVirtualLessonRequest.class);
        requests.put("UpdateVirtualLessonRequest", UpdateVirtualLessonRequest.class);
        requests.put("SaveLessonProgressRequest", SaveLessonProgressRequest.class);

        List<String> drift = new ArrayList<>();
        requests.forEach((name, type) -> {
            if (!propertyNames(at(name)).equals(components(type))) {
                drift.add(name + " properties");
            }
            if (!requiredNames(at(name)).equals(mandatoryComponents(type))) {
                drift.add(name + " required");
            }
        });
        assertThat(drift).isEmpty();
    }

    @Test
    void the_enums_match_the_domain() {
        for (String path : List.of("CreateVirtualCourseRequest/level", "UpdateVirtualCourseRequest/level",
            "VirtualCourseManagementResponse/level", "VirtualCourseAdminDetailResponse/level")) {
            assertThat(at(path).get("enum")).as(path).isEqualTo(names(CourseLevel.class));
        }
        for (String path : List.of("VirtualCourseManagementResponse/status",
            "VirtualCourseAdminDetailResponse/status")) {
            assertThat(at(path).get("enum")).as(path).isEqualTo(names(CourseStatus.class));
        }
    }

    @Test
    void the_admin_detail_requires_the_fields_the_edit_forms_are_prefilled_with() {
        assertThat(requiredNames(at("VirtualCourseAdminDetailResponse"))).contains("shortDescription", "professorId");
        assertThat(requiredNames(at("VirtualCourseAdminDetailResponse/modules/[]"))).contains("preview");
        assertThat(requiredNames(at("VirtualCourseAdminDetailResponse/modules/[]/lessons/[]")))
            .contains("description");
    }
}
