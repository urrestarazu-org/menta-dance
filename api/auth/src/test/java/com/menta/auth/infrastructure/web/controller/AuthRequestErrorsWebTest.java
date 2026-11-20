package com.menta.auth.infrastructure.web.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;

import com.menta.auth.application.port.in.ActivateAccountUseCase;
import com.menta.auth.application.port.in.RegisterUserUseCase;
import com.menta.auth.application.port.in.RequestPasswordResetUseCase;
import com.menta.auth.application.port.in.ResendActivationUseCase;
import com.menta.auth.application.port.in.ResetPasswordUseCase;
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
 * Request-decoding failures across every auth route that reads a body (#791).
 *
 * <p>Unlike the per-controller tests, which wire one controller with its own advice, this boots a web context with
 * every real auth controller and every real {@code @RestControllerAdvice} — the same advice selection and ordering
 * production gets. A route nothing covers falls back to Spring's default {@code 400} ({@code {timestamp, status,
 * error, message, path}}, no {@code code}), so each case asserts the {@code application/problem+json} contract
 * instead: {@code INVALID_REQUEST} everywhere, except login whose own handler answers
 * {@code INVALID_AUTH_REQUEST}.</p>
 */
class AuthRequestErrorsWebTest {

    private static final String CONTROLLERS = "com.menta.auth.infrastructure.web.controller";
    private static final String UNREADABLE_JSON = "{\"email\": ";
    private static final String EMPTY_JSON = "{}";
    private static final String GENERIC_DETAIL = "La solicitud contiene datos inválidos.";
    private static final String INVALID_REQUEST = "INVALID_REQUEST";
    private static final String INVALID_AUTH_REQUEST = "INVALID_AUTH_REQUEST";
    private static final String ID = UUID.randomUUID().toString();

    private static final String LOGIN = "/api/v1/auth/login";
    private static final String REGISTER = "/api/v1/auth/register";
    private static final String REGISTER_ALIAS = "/api/v1/users/register";
    private static final String ACTIVATE = "/api/v1/auth/activate";
    private static final String RESEND = "/api/v1/auth/resend-activation";
    private static final String FORGOT = "/api/v1/auth/forgot-password";
    private static final String RESET = "/api/v1/auth/reset-password";
    private static final String ADMIN_CREATE = "/api/v1/admin/users";
    private static final String ADMIN_ROLE = "/api/v1/admin/users/" + ID + "/role";

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
        reset(
            context.getBean(RegisterUserUseCase.class),
            context.getBean(ActivateAccountUseCase.class),
            context.getBean(ResendActivationUseCase.class),
            context.getBean(RequestPasswordResetUseCase.class),
            context.getBean(ResetPasswordUseCase.class)
        );
    }

    @EnableWebMvc
    static class WebConfig {
    }

    private static List<Class<?>> scan(Class<? extends java.lang.annotation.Annotation> annotation) {
        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(annotation));
        return scanner.findCandidateComponents(CONTROLLERS).stream()
            .map(BeanDefinition::getBeanClassName)
            .map(AuthRequestErrorsWebTest::load)
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

    /** Every route with a {@code @RequestBody}: method, url and the code its 400 carries. */
    private static Stream<Arguments> bodyRoutes() {
        return Stream.of(
            Arguments.of(HttpMethod.POST, LOGIN, INVALID_AUTH_REQUEST),
            Arguments.of(HttpMethod.POST, REGISTER, INVALID_REQUEST),
            Arguments.of(HttpMethod.POST, REGISTER_ALIAS, INVALID_REQUEST),
            Arguments.of(HttpMethod.POST, ACTIVATE, INVALID_REQUEST),
            Arguments.of(HttpMethod.POST, RESEND, INVALID_REQUEST),
            Arguments.of(HttpMethod.POST, FORGOT, INVALID_REQUEST),
            Arguments.of(HttpMethod.POST, RESET, INVALID_REQUEST),
            Arguments.of(HttpMethod.POST, ADMIN_CREATE, INVALID_REQUEST),
            Arguments.of(HttpMethod.PATCH, ADMIN_ROLE, INVALID_REQUEST)
        );
    }

    /** The routes with a {@code role} enum in the body: a value that is not a role cannot be decoded. */
    private static Stream<Arguments> roleRoutes() {
        return Stream.of(
            Arguments.of(HttpMethod.POST, REGISTER),
            Arguments.of(HttpMethod.POST, REGISTER_ALIAS),
            Arguments.of(HttpMethod.POST, ADMIN_CREATE),
            Arguments.of(HttpMethod.PATCH, ADMIN_ROLE)
        );
    }

    private static void assertProblem(MockHttpServletResponse response, String code) throws Exception {
        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(response.getContentType()).startsWith("application/problem+json");
        assertThat(response.getContentAsString())
            .contains("\"code\":\"" + code + "\"")
            .doesNotContain("\"timestamp\"")
            .doesNotContain("\"errors\"");
    }

    private static void assertInvalidRequestProblem(MockHttpServletResponse response) throws Exception {
        assertProblem(response, INVALID_REQUEST);
        assertThat(response.getContentAsString()).contains("\"detail\":\"" + GENERIC_DETAIL + "\"");
    }

    @Test
    void every_route_with_a_request_body_is_listed_here() {
        // 8 handler methods; register is mapped twice (canonical and legacy alias), so 9 routes.
        long methods = scan(RestController.class).stream()
            .flatMap(controller -> Arrays.stream(controller.getDeclaredMethods()))
            .filter(AuthRequestErrorsWebTest::readsBody)
            .count();

        assertThat(methods + 1).isEqualTo(bodyRoutes().count());
    }

    private static boolean readsBody(Method method) {
        return Arrays.stream(method.getParameters())
            .anyMatch(parameter -> parameter.isAnnotationPresent(RequestBody.class));
    }

    @ParameterizedTest(name = "{0} {1}")
    @MethodSource("bodyRoutes")
    void unreadable_json_answers_400_problem_with_its_code(HttpMethod method, String url, String code)
        throws Exception {
        MockHttpServletResponse response = mockMvc.perform(json(method, url, UNREADABLE_JSON))
            .andReturn().getResponse();

        assertProblem(response, code);
    }

    @ParameterizedTest(name = "{0} {1}")
    @MethodSource("bodyRoutes")
    void an_empty_body_answers_400_problem_with_its_code(HttpMethod method, String url, String code)
        throws Exception {
        MockHttpServletResponse response = mockMvc.perform(json(method, url, null)).andReturn().getResponse();

        assertProblem(response, code);
    }

    @ParameterizedTest(name = "{0} {1}")
    @MethodSource("bodyRoutes")
    void a_body_failing_bean_validation_answers_400_problem_without_field_messages(
        HttpMethod method, String url, String code
    ) throws Exception {
        MockHttpServletResponse response = mockMvc.perform(json(method, url, EMPTY_JSON)).andReturn().getResponse();

        assertProblem(response, code);
    }

    @ParameterizedTest(name = "{0} {1}")
    @MethodSource("roleRoutes")
    void a_role_that_does_not_exist_answers_400_invalid_request(HttpMethod method, String url) throws Exception {
        String body = "{\"role\":\"SUPERHERO\",\"email\":\"a@example.com\",\"password\":\"SecurePass123!\","
            + "\"firstName\":\"Ada\",\"lastName\":\"Lovelace\",\"phone\":\"+5491100000000\"}";

        MockHttpServletResponse response = mockMvc.perform(json(method, url, body)).andReturn().getResponse();

        assertInvalidRequestProblem(response);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("publicBodies")
    void the_public_routes_keep_answering_their_success_status_for_a_readable_body(String url, String body, int status)
        throws Exception {
        MockHttpServletResponse response = mockMvc.perform(json(HttpMethod.POST, url, body)).andReturn().getResponse();

        assertThat(response.getStatus()).isEqualTo(status);
    }

    private static Stream<Arguments> publicBodies() {
        return Stream.of(
            Arguments.of(REGISTER, "{\"role\":\"STUDENT\",\"email\":\"a@example.com\",\"password\":\"SecurePass123!\","
                + "\"firstName\":\"Ada\",\"lastName\":\"Lovelace\",\"phone\":\"+5491100000000\"}", 202),
            Arguments.of(ACTIVATE, "{\"token\":\"opaque\"}", 204),
            Arguments.of(RESEND, "{\"email\":\"a@example.com\"}", 202),
            Arguments.of(FORGOT, "{\"email\":\"a@example.com\"}", 202),
            Arguments.of(RESET, "{\"token\":\"opaque\",\"newPassword\":\"SecurePass123!\"}", 200)
        );
    }

    /** Throws the unreadable-body exception from the use case, as if the cause chain carried an IAE. */
    private static HttpMessageNotReadableException unreadableWithIllegalArgumentCause() {
        return new HttpMessageNotReadableException(
            "unreadable", new IllegalArgumentException("bad value"), new MockHttpInputMessage(new byte[0])
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("publicBodies")
    void an_unreadable_body_whose_cause_is_an_illegal_argument_still_answers_400_invalid_request(
        String url, String body, int ignoredStatus
    ) throws Exception {
        // Spring resolves a handler by looking at the exception and then at its cause chain, advice by advice and
        // controller-local handlers first. The register and resend controllers map an IllegalArgumentException from
        // the use case to the anti-enumeration 202 / INVALID_ACTIVATION_REQUEST; none of that may swallow a decoding
        // failure.
        RuntimeException failure = unreadableWithIllegalArgumentCause();
        doThrow(failure).when(context.getBean(RegisterUserUseCase.class)).register(any());
        doThrow(failure).when(context.getBean(ActivateAccountUseCase.class)).activate(any());
        doThrow(failure).when(context.getBean(ResendActivationUseCase.class)).resend(any());
        doThrow(failure).when(context.getBean(RequestPasswordResetUseCase.class)).request(any());
        doThrow(failure).when(context.getBean(ResetPasswordUseCase.class)).reset(any());

        MockHttpServletResponse response = mockMvc.perform(json(HttpMethod.POST, url, body)).andReturn().getResponse();

        assertInvalidRequestProblem(response);
    }

    @Test
    void an_illegal_argument_from_the_use_case_keeps_its_own_meaning() throws Exception {
        doThrow(new IllegalArgumentException("semantic")).when(context.getBean(RegisterUserUseCase.class))
            .register(any());
        doThrow(new IllegalArgumentException("semantic")).when(context.getBean(ResendActivationUseCase.class))
            .resend(any());

        MockHttpServletResponse register = mockMvc.perform(json(HttpMethod.POST, REGISTER, publicBody(REGISTER)))
            .andReturn().getResponse();
        MockHttpServletResponse resend = mockMvc.perform(json(HttpMethod.POST, RESEND, publicBody(RESEND)))
            .andReturn().getResponse();

        assertThat(register.getStatus()).isEqualTo(202);
        assertProblem(resend, "INVALID_ACTIVATION_REQUEST");
    }

    private static String publicBody(String url) {
        return (String) publicBodies().map(Arguments::get).filter(args -> args[0].equals(url))
            .findFirst().orElseThrow()[1];
    }
}
