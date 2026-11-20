package com.menta.virtual.infrastructure.web.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;

import com.menta.virtual.application.dto.LessonProgressView;
import com.menta.virtual.application.port.in.SaveLessonProgressUseCase;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
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
 * Request-decoding failures across every virtual route that reads a body (#790).
 *
 * <p>Unlike the per-controller tests, which wire one controller with its own advice, this boots a
 * web context with every real virtual controller and every real {@code @RestControllerAdvice} —
 * the same advice selection and ordering production gets. A route that no advice covers falls back
 * to Spring's default {@code 400} ({@code {timestamp, status, error, message, path}}, no
 * {@code code}), so each case asserts the {@code application/problem+json} {@code INVALID_REQUEST}
 * contract instead.</p>
 */
class VirtualRequestErrorsWebTest {

    private static final String CONTROLLERS = "com.menta.virtual.infrastructure.web.controller";
    private static final String UNREADABLE_JSON = "{\"title\": ";
    private static final String EMPTY_JSON = "{}";
    private static final String GENERIC_DETAIL = "La solicitud contiene datos inválidos.";
    private static final String ID = UUID.randomUUID().toString();
    private static final String PROGRESS = "/api/v1/virtual/lessons/" + ID + "/progress";

    private static GenericWebApplicationContext context;
    private static MockMvc mockMvc;

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
        reset(context.getBean(SaveLessonProgressUseCase.class));
    }

    @EnableWebMvc
    static class WebConfig {
    }

    private static List<Class<?>> scan(Class<? extends java.lang.annotation.Annotation> annotation) {
        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(annotation));
        return scanner.findCandidateComponents(CONTROLLERS).stream()
            .map(BeanDefinition::getBeanClassName)
            .map(VirtualRequestErrorsWebTest::load)
            .toList();
    }

    private static Class<?> load(String className) {
        try {
            return Class.forName(className);
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException(e);
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void registerWithMockedDependencies(Class<?> controller, Set<Class<?>> mocked) {
        for (Constructor<?> constructor : controller.getConstructors()) {
            for (Class<?> dependency : constructor.getParameterTypes()) {
                if (mocked.add(dependency)) {
                    context.registerBean((Class) dependency, () -> mock(dependency));
                }
            }
        }
        context.registerBean(controller);
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

    /** Every route with a {@code @RequestBody}: method and url. */
    private static Stream<Arguments> bodyRoutes() {
        return Stream.of(
            Arguments.of(HttpMethod.POST, "/api/v1/admin/virtual/courses"),
            Arguments.of(HttpMethod.PUT, "/api/v1/admin/virtual/courses/" + ID),
            Arguments.of(HttpMethod.POST, "/api/v1/admin/virtual/courses/" + ID + "/modules"),
            Arguments.of(HttpMethod.PUT, "/api/v1/admin/virtual/courses/" + ID + "/modules/reorder"),
            Arguments.of(HttpMethod.PUT, "/api/v1/admin/virtual/modules/" + ID),
            Arguments.of(HttpMethod.POST, "/api/v1/admin/virtual/modules/" + ID + "/lessons"),
            Arguments.of(HttpMethod.PUT, "/api/v1/admin/virtual/lessons/" + ID),
            Arguments.of(HttpMethod.PUT, PROGRESS)
        );
    }

    /** The routes whose body is {@code @Valid}: an empty object violates a constraint. */
    private static Stream<Arguments> validatedRoutes() {
        return Stream.of(
            Arguments.of(HttpMethod.POST, "/api/v1/admin/virtual/courses"),
            Arguments.of(HttpMethod.POST, "/api/v1/admin/virtual/courses/" + ID + "/modules"),
            Arguments.of(HttpMethod.PUT, "/api/v1/admin/virtual/courses/" + ID + "/modules/reorder"),
            Arguments.of(HttpMethod.POST, "/api/v1/admin/virtual/modules/" + ID + "/lessons"),
            Arguments.of(HttpMethod.PUT, PROGRESS)
        );
    }

    private static void assertInvalidRequestProblem(MockHttpServletResponse response) throws Exception {
        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(response.getContentType()).startsWith("application/problem+json");
        assertThat(response.getContentAsString())
            .contains("\"code\":\"INVALID_REQUEST\"")
            .contains("\"detail\":\"" + GENERIC_DETAIL + "\"")
            .doesNotContain("\"timestamp\"")
            .doesNotContain("\"errors\"");
    }

    @Test
    void every_route_with_a_request_body_is_listed_here() {
        long routes = scan(RestController.class).stream()
            .flatMap(controller -> Arrays.stream(controller.getDeclaredMethods()))
            .filter(VirtualRequestErrorsWebTest::readsBody)
            .count();

        assertThat(routes).isEqualTo(bodyRoutes().count());
    }

    private static boolean readsBody(Method method) {
        return Arrays.stream(method.getParameters())
            .anyMatch(parameter -> parameter.isAnnotationPresent(RequestBody.class));
    }

    @ParameterizedTest(name = "{0} {1}")
    @MethodSource("bodyRoutes")
    void unreadable_json_answers_400_invalid_request_problem(HttpMethod method, String url) throws Exception {
        MockHttpServletResponse response = mockMvc.perform(json(method, url, UNREADABLE_JSON))
            .andReturn().getResponse();

        assertInvalidRequestProblem(response);
    }

    @ParameterizedTest(name = "{0} {1}")
    @MethodSource("bodyRoutes")
    void an_empty_body_answers_400_invalid_request_problem(HttpMethod method, String url) throws Exception {
        MockHttpServletResponse response = mockMvc.perform(json(method, url, null)).andReturn().getResponse();

        assertInvalidRequestProblem(response);
    }

    @ParameterizedTest(name = "{0} {1}")
    @MethodSource("validatedRoutes")
    void a_body_failing_bean_validation_answers_400_invalid_request_without_field_messages(
        HttpMethod method, String url
    ) throws Exception {
        MockHttpServletResponse response = mockMvc.perform(json(method, url, EMPTY_JSON)).andReturn().getResponse();

        assertInvalidRequestProblem(response);
    }

    @ParameterizedTest
    @MethodSource("missingPositions")
    void saving_progress_without_a_position_is_rejected_and_never_overwrites_the_saved_one(String body)
        throws Exception {
        MockHttpServletResponse response = mockMvc.perform(json(HttpMethod.PUT, PROGRESS, body))
            .andReturn().getResponse();

        assertInvalidRequestProblem(response);
        verify(context.getBean(SaveLessonProgressUseCase.class), never()).save(anyString(), any(), anyInt());
    }

    private static Stream<String> missingPositions() {
        return Stream.of(EMPTY_JSON, "{\"positionSeconds\": null}");
    }

    @Test
    void saving_progress_with_a_position_still_answers_200() throws Exception {
        SaveLessonProgressUseCase useCase = context.getBean(SaveLessonProgressUseCase.class);
        when(useCase.save(anyString(), any(), anyInt())).thenReturn(new LessonProgressView(ID, 0, false, null));

        MockHttpServletResponse response = mockMvc.perform(json(HttpMethod.PUT, PROGRESS, "{\"positionSeconds\": 0}"))
            .andReturn().getResponse();

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getContentAsString()).contains("\"positionSeconds\":0");
    }

    @Test
    void an_unreadable_body_whose_cause_is_an_illegal_argument_is_not_swallowed_by_the_malformed_id_advice()
        throws Exception {
        // The student advice maps IllegalArgumentException to the anti-enumeration 404. Spring looks
        // at the cause chain of the thrown exception, so the request advice must win that lookup.
        SaveLessonProgressUseCase useCase = context.getBean(SaveLessonProgressUseCase.class);
        when(useCase.save(anyString(), any(), anyInt())).thenThrow(new HttpMessageNotReadableException(
            "unreadable", new IllegalArgumentException("bad value"), new MockHttpInputMessage(new byte[0])
        ));

        MockHttpServletResponse response = mockMvc.perform(json(HttpMethod.PUT, PROGRESS, "{\"positionSeconds\": 1}"))
            .andReturn().getResponse();

        assertInvalidRequestProblem(response);
    }
}
