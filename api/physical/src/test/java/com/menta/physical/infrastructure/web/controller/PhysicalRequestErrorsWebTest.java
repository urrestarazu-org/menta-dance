package com.menta.physical.infrastructure.web.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;

import com.menta.physical.application.dto.PhysicalCourseManagementResult;
import com.menta.physical.application.port.in.CreatePhysicalCourseUseCase;
import com.menta.physical.application.port.in.ProcessPhysicalCheckInUseCase;
import com.menta.physical.application.port.in.RegisterPhysicalDeviceUseCase;
import com.menta.physical.application.port.in.UpdatePhysicalCourseUseCase;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.AnnotationConfigUtils;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.mock.http.MockHttpInputMessage;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.support.GenericWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

/**
 * Request-decoding failures across every physical route that reads a body (#792).
 *
 * <p>Unlike the per-controller tests, which wire one controller with its own advice, this boots a
 * web context with every real physical controller and every real {@code @RestControllerAdvice} —
 * the same advice selection and ordering production gets. A route that no advice covers falls back
 * to Spring's default {@code 400} ({@code {timestamp, status, error, message, path}}, no
 * {@code code}), so each case asserts the {@code application/problem+json} {@code INVALID_REQUEST}
 * contract instead.</p>
 *
 * <p>The domain advices also answer {@code 400 INVALID_REQUEST} for an {@link IllegalArgumentException}
 * (a malformed id), with the detail {@code "Malformed request."}. Both share the code, so the
 * cases that tell them apart assert the detail text.</p>
 */
class PhysicalRequestErrorsWebTest {

    private static final String CONTROLLERS = "com.menta.physical.infrastructure.web.controller";
    private static final String UNREADABLE_JSON = "{\"title\": ";
    private static final String EMPTY_JSON = "{}";
    private static final String GENERIC_DETAIL = "La solicitud contiene datos inválidos.";
    private static final String MALFORMED_DETAIL = "Malformed request.";
    private static final String ID = UUID.randomUUID().toString();
    private static final String COURSES = "/api/v1/admin/physical/courses";
    private static final String COURSE = COURSES + "/" + ID;
    private static final String SESSION = "/api/v1/admin/physical/sessions/" + ID;
    private static final String DEVICES = "/api/v1/admin/physical/devices";
    private static final String CHECK_INS = "/api/v1/physical/sessions/" + ID + "/check-ins";
    private static final String VALID_COURSE = "{\"title\":\"Salsa\",\"description\":\"Inicial\","
        + "\"professorName\":\"María García\",\"dayOfWeek\":\"MONDAY\",\"startTime\":\"20:00\","
        + "\"durationMinutes\":60,\"level\":\"BEGINNER\",\"capacity\":20}";
    private static final String VALID_DEVICE = "{\"name\":\"Puerta\",\"location\":\"Sede Centro\"}";
    private static final String VALID_QR_CHECK_IN = "{\"type\":\"QR\",\"qrCredentials\":\"qr\","
        + "\"deviceId\":\"" + ID + "\",\"deviceToken\":\"token\"}";

    private static GenericWebApplicationContext context;
    private static MockMvc mockMvc;
    private static final List<Object> MOCKS = new ArrayList<>();

    @BeforeAll
    static void bootContext() {
        context = new GenericWebApplicationContext(new MockServletContext());
        AnnotationConfigUtils.registerAnnotationConfigProcessors(context);
        context.registerBean(WebConfig.class);
        Set<Class<?>> mocked = new HashSet<>();
        for (Class<?> type : scan(RestController.class)) {
            registerWithMockedDependencies(type, mocked);
        }
        for (Class<?> type : scan(RestControllerAdvice.class)) {
            context.registerBean(type);
        }
        context.refresh();
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    @AfterAll
    static void closeContext() {
        context.close();
    }

    @AfterEach
    void resetMocks() {
        reset(MOCKS.toArray());
    }

    @EnableWebMvc
    static class WebConfig {
    }

    private static List<Class<?>> scan(Class<? extends java.lang.annotation.Annotation> annotation) {
        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(annotation));
        return scanner.findCandidateComponents(CONTROLLERS).stream()
            .map(BeanDefinition::getBeanClassName)
            .map(PhysicalRequestErrorsWebTest::load)
            .toList();
    }

    private static Class<?> load(String className) {
        try {
            return Class.forName(className);
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Declared, not public, constructors: {@code PhysicalCheckInController}'s is package-private. */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void registerWithMockedDependencies(Class<?> controller, Set<Class<?>> mocked) {
        for (Constructor<?> constructor : controller.getDeclaredConstructors()) {
            for (Class<?> dependency : constructor.getParameterTypes()) {
                if (mocked.add(dependency)) {
                    Object instance = mock(dependency);
                    MOCKS.add(instance);
                    context.registerBean((Class) dependency, () -> instance);
                }
            }
        }
        context.registerBean(controller);
    }

    private static <T> T bean(Class<T> type) {
        return context.getBean(type);
    }

    private static Authentication caller() {
        return new UsernamePasswordAuthenticationToken(
            UUID.randomUUID().toString(), null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))
        );
    }

    private static MockHttpServletRequestBuilder json(HttpMethod method, String url, String body) {
        MockHttpServletRequestBuilder builder = request(method, url).contentType(MediaType.APPLICATION_JSON)
            .principal(caller());
        return body == null ? builder : builder.content(body);
    }

    private static MockHttpServletResponse perform(HttpMethod method, String url, String body) throws Exception {
        return mockMvc.perform(json(method, url, body)).andReturn().getResponse();
    }

    /** Every route with a {@code @RequestBody}: method and url. */
    private static Stream<Arguments> bodyRoutes() {
        return Stream.of(
            Arguments.of(HttpMethod.POST, COURSES),
            Arguments.of(HttpMethod.PATCH, COURSE),
            Arguments.of(HttpMethod.POST, COURSE + "/sessions"),
            Arguments.of(HttpMethod.POST, COURSE + "/sessions/batch"),
            Arguments.of(HttpMethod.PATCH, SESSION),
            Arguments.of(HttpMethod.POST, DEVICES),
            Arguments.of(HttpMethod.POST, CHECK_INS)
        );
    }

    /** The routes whose {@code @Valid} body has a mandatory field: an empty object violates a constraint. */
    private static Stream<Arguments> validatedRoutes() {
        return Stream.of(
            Arguments.of(HttpMethod.POST, COURSES),
            Arguments.of(HttpMethod.POST, COURSE + "/sessions"),
            Arguments.of(HttpMethod.POST, COURSE + "/sessions/batch"),
            Arguments.of(HttpMethod.POST, DEVICES),
            Arguments.of(HttpMethod.POST, CHECK_INS)
        );
    }

    /** A value Jackson cannot decode: an enum constant that does not exist, or a malformed date or time. */
    private static Stream<Arguments> undecodableValues() {
        return Stream.of(
            Arguments.of(HttpMethod.POST, COURSES, "{\"level\":\"EXPERT\"}"),
            Arguments.of(HttpMethod.POST, COURSES, "{\"dayOfWeek\":\"FUNDAY\"}"),
            Arguments.of(HttpMethod.PATCH, COURSE, "{\"status\":\"DELETED\"}"),
            Arguments.of(HttpMethod.PATCH, COURSE, "{\"level\":\"EXPERT\"}"),
            Arguments.of(HttpMethod.PATCH, COURSE, "{\"dayOfWeek\":\"FUNDAY\"}"),
            Arguments.of(HttpMethod.PATCH, SESSION, "{\"status\":\"POSTPONED\"}"),
            Arguments.of(HttpMethod.POST, COURSE + "/sessions", "{\"date\":\"not-a-date\",\"startTime\":\"20:00\"}"),
            Arguments.of(HttpMethod.POST, COURSE + "/sessions/batch", "{\"fromDate\":\"2026-13-01\"}"),
            Arguments.of(HttpMethod.PATCH, SESSION, "{\"startTime\":\"25:99\"}"),
            Arguments.of(HttpMethod.POST, DEVICES,
                "{\"name\":\"Puerta\",\"location\":\"Sede\",\"expiresAt\":\"mañana\"}")
        );
    }

    /**
     * One body route per domain advice, each with a valid body and a way to make its use case throw.
     * The use case is the earliest point a test can raise an exception on a route.
     */
    private static Stream<Arguments> routesPerAdvice() {
        return Stream.of(
            Arguments.of(HttpMethod.POST, COURSES, VALID_COURSE, (Consumer<RuntimeException>) failure ->
                when(bean(CreatePhysicalCourseUseCase.class).create(any(), any(), anyBoolean())).thenThrow(failure)),
            Arguments.of(HttpMethod.POST, DEVICES, VALID_DEVICE, (Consumer<RuntimeException>) failure ->
                when(bean(RegisterPhysicalDeviceUseCase.class).register(any(), any())).thenThrow(failure)),
            Arguments.of(HttpMethod.POST, CHECK_INS, VALID_QR_CHECK_IN, (Consumer<RuntimeException>) failure ->
                when(bean(ProcessPhysicalCheckInUseCase.class).checkIn(any())).thenThrow(failure))
        );
    }

    private static void assertProblem(MockHttpServletResponse response, String detail) throws Exception {
        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(response.getContentType()).startsWith("application/problem+json");
        assertThat(response.getContentAsString())
            .contains("\"code\":\"INVALID_REQUEST\"")
            .contains("\"detail\":\"" + detail + "\"")
            .doesNotContain("\"timestamp\"")
            .doesNotContain("\"errors\"");
    }

    private static void assertInvalidRequestProblem(MockHttpServletResponse response) throws Exception {
        assertProblem(response, GENERIC_DETAIL);
    }

    @Test
    void every_route_with_a_request_body_is_listed_here() {
        long routes = scan(RestController.class).stream()
            .flatMap(controller -> Arrays.stream(controller.getDeclaredMethods()))
            .filter(PhysicalRequestErrorsWebTest::readsBody)
            .count();

        assertThat(routes).isEqualTo(7).isEqualTo(bodyRoutes().count());
    }

    private static boolean readsBody(Method method) {
        return Arrays.stream(method.getParameters())
            .anyMatch(parameter -> parameter.isAnnotationPresent(RequestBody.class));
    }

    @ParameterizedTest(name = "{0} {1}")
    @MethodSource("bodyRoutes")
    void unreadable_json_answers_400_invalid_request_problem(HttpMethod method, String url) throws Exception {
        assertInvalidRequestProblem(perform(method, url, UNREADABLE_JSON));
    }

    @ParameterizedTest(name = "{0} {1}")
    @MethodSource("bodyRoutes")
    void an_empty_body_answers_400_invalid_request_problem(HttpMethod method, String url) throws Exception {
        assertInvalidRequestProblem(perform(method, url, null));
    }

    @ParameterizedTest(name = "{0} {1}")
    @MethodSource("validatedRoutes")
    void a_body_failing_bean_validation_answers_400_invalid_request_without_field_messages(
        HttpMethod method, String url
    ) throws Exception {
        assertInvalidRequestProblem(perform(method, url, EMPTY_JSON));
    }

    @ParameterizedTest(name = "{0} {1} {2}")
    @MethodSource("undecodableValues")
    void a_value_that_cannot_be_decoded_answers_400_invalid_request_problem(
        HttpMethod method, String url, String body
    ) throws Exception {
        assertInvalidRequestProblem(perform(method, url, body));
    }

    @ParameterizedTest(name = "{0} {1}")
    @MethodSource("routesPerAdvice")
    void an_unreadable_body_whose_cause_is_an_illegal_argument_is_not_swallowed_by_the_malformed_id_handler(
        HttpMethod method, String url, String body, Consumer<RuntimeException> failWith
    ) throws Exception {
        // Each domain advice maps IllegalArgumentException to "Malformed request.". Spring looks at
        // the cause chain of the thrown exception, so the request advice must win that lookup.
        failWith.accept(new HttpMessageNotReadableException(
            "unreadable", new IllegalArgumentException("bad value"), new MockHttpInputMessage(new byte[0])
        ));

        assertInvalidRequestProblem(perform(method, url, body));
    }

    @ParameterizedTest(name = "{0} {1}")
    @MethodSource("routesPerAdvice")
    void an_illegal_argument_from_the_use_case_still_answers_the_malformed_request_problem(
        HttpMethod method, String url, String body, Consumer<RuntimeException> failWith
    ) throws Exception {
        failWith.accept(new IllegalArgumentException("Invalid id format"));

        assertProblem(perform(method, url, body), MALFORMED_DETAIL);
    }

    @Test
    void a_malformed_professor_id_in_a_readable_body_still_answers_the_malformed_request_problem() throws Exception {
        String body = VALID_COURSE.replace("{", "{\"professorId\":\"not-a-uuid\",");

        assertProblem(perform(HttpMethod.POST, COURSES, body), MALFORMED_DETAIL);
    }

    @Test
    void a_readable_body_with_a_valid_enum_still_answers_200() throws Exception {
        when(bean(UpdatePhysicalCourseUseCase.class).update(anyString(), any(), any(), anyBoolean()))
            .thenReturn(new PhysicalCourseManagementResult(
                ID, "Salsa inicial", "desc", UUID.randomUUID().toString(), "María García",
                "WEDNESDAY", "20:00", 60, "INTERMEDIATE", 20, "INACTIVE"
            ));

        MockHttpServletResponse response = perform(HttpMethod.PATCH, COURSE, "{\"status\":\"INACTIVE\"}");

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getContentAsString()).contains("\"status\":\"INACTIVE\"");
    }
}
