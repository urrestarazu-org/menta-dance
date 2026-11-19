package com.menta.billing.infrastructure.web.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;

import java.lang.reflect.Constructor;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.AnnotationConfigUtils;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.support.GenericWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

/**
 * Request-decoding failures across every billing route that reads a body or a multipart part (#788).
 *
 * <p>Unlike the per-controller tests, which wire one controller with its own advice, this boots a
 * web context with every real billing controller and every real {@code @RestControllerAdvice} —
 * the same advice selection and ordering production gets. A route whose marker annotation no advice
 * covers falls back to Spring's default {@code 400} ({@code {timestamp, status, error, message,
 * path}}, no {@code code}, and with {@code include-binding-errors: always} raw field messages), so
 * each case asserts the {@code application/problem+json} {@code INVALID_REQUEST} contract instead.</p>
 */
class BillingRequestErrorsWebTest {

    private static final String CONTROLLERS = "com.menta.billing.infrastructure.web.controller";
    private static final String UNREADABLE_JSON = "{\"reason\": ";
    private static final String EMPTY_JSON = "{}";
    private static final String GENERIC_DETAIL = "La solicitud contiene datos inválidos.";
    private static final String UUID_ID = UUID.randomUUID().toString();

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

    @EnableWebMvc
    static class WebConfig {
    }

    private static List<Class<?>> scan(Class<? extends java.lang.annotation.Annotation> annotation) {
        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(annotation));
        // The e2e support controller is @Profile-gated; activate it so it is scanned like the rest.
        StandardEnvironment environment = new StandardEnvironment();
        environment.setActiveProfiles("e2e-mercadopago");
        scanner.setEnvironment(environment);
        return scanner.findCandidateComponents(CONTROLLERS).stream()
            .map(BeanDefinition::getBeanClassName)
            .map(BillingRequestErrorsWebTest::load)
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

    private static Authentication admin() {
        return new UsernamePasswordAuthenticationToken(
            UUID.randomUUID().toString(), null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))
        );
    }

    private static MockHttpServletRequestBuilder json(HttpMethod method, String url, String body) {
        return request(method, url).contentType(MediaType.APPLICATION_JSON).content(body).principal(admin());
    }

    /** Every route with a {@code @RequestBody}: method and url. */
    private static Stream<Arguments> bodyRoutes() {
        return Stream.of(
            Arguments.of(HttpMethod.POST, "/api/v1/billing/subscriptions"),
            Arguments.of(HttpMethod.DELETE, "/api/v1/admin/billing/subscriptions/" + UUID_ID),
            Arguments.of(HttpMethod.POST, "/api/v1/admin/billing/subscriptions/trial"),
            Arguments.of(HttpMethod.POST, "/api/v1/billing/physical/purchases"),
            Arguments.of(HttpMethod.POST, "/api/v1/billing/physical/quotes"),
            Arguments.of(HttpMethod.PUT, "/api/v1/billing/physical/courses/course-1/pricing"),
            Arguments.of(HttpMethod.POST, "/api/v1/admin/billing/plans"),
            Arguments.of(HttpMethod.POST, "/api/v1/admin/billing/payments/" + UUID_ID + "/reject"),
            Arguments.of(HttpMethod.POST, "/api/v1/admin/billing/payments/" + UUID_ID + "/corrections"),
            Arguments.of(HttpMethod.POST, "/api/v1/admin/billing/purchases/" + UUID_ID + "/resolution"),
            Arguments.of(HttpMethod.POST, "/api/v1/e2e/mercadopago/approved-webhook"),
            Arguments.of(HttpMethod.POST, "/api/v1/e2e/mercadopago/inconsistent-webhook")
        );
    }

    /** The routes whose body carries bean-validation constraints (the e2e support routes have none). */
    private static Stream<Arguments> validatedRoutes() {
        return bodyRoutes().filter(route -> !route.get()[1].toString().contains("/e2e/"));
    }

    private static void assertInvalidRequestProblem(MockHttpServletResponse response, String detail)
        throws Exception {
        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(response.getContentType()).startsWith("application/problem+json");
        assertThat(response.getContentAsString())
            .contains("\"code\":\"INVALID_REQUEST\"")
            .contains("\"detail\":\"" + detail + "\"")
            .doesNotContain("\"timestamp\"")
            .doesNotContain("\"errors\"");
    }

    @ParameterizedTest(name = "{0} {1}")
    @MethodSource("bodyRoutes")
    void unreadable_json_answers_400_invalid_request_problem(HttpMethod method, String url) throws Exception {
        MockHttpServletResponse response = mockMvc.perform(json(method, url, UNREADABLE_JSON))
            .andReturn().getResponse();

        assertInvalidRequestProblem(response, GENERIC_DETAIL);
    }

    @ParameterizedTest(name = "{0} {1}")
    @MethodSource("validatedRoutes")
    void a_body_failing_bean_validation_answers_400_invalid_request_without_field_messages(
        HttpMethod method, String url
    ) throws Exception {
        MockHttpServletResponse response = mockMvc.perform(json(method, url, EMPTY_JSON))
            .andReturn().getResponse();

        assertInvalidRequestProblem(response, GENERIC_DETAIL);
    }

    @Test
    void a_proof_upload_without_the_file_part_answers_400_invalid_request_problem() throws Exception {
        MockHttpServletResponse response = mockMvc.perform(
                multipart("/api/v1/billing/payments/" + UUID_ID + "/proof").principal(admin()))
            .andReturn().getResponse();

        assertInvalidRequestProblem(response, "Falta una parte requerida de la solicitud.");
    }
}
