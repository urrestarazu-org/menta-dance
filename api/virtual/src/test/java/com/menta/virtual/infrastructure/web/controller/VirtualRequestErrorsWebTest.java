package com.menta.virtual.infrastructure.web.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;

import com.menta.virtual.application.dto.CreateVirtualLessonCommand;
import com.menta.virtual.application.dto.LessonProgressView;
import com.menta.virtual.application.dto.UpdateVirtualLessonCommand;
import com.menta.virtual.application.dto.UpdateVirtualModuleCommand;
import com.menta.virtual.application.dto.VirtualLessonManagementResult;
import com.menta.virtual.application.dto.VirtualModuleManagementResult;
import com.menta.virtual.application.port.in.CreateVirtualLessonUseCase;
import com.menta.virtual.application.port.in.SaveLessonProgressUseCase;
import com.menta.virtual.application.port.in.UpdateVirtualLessonUseCase;
import com.menta.virtual.application.port.in.UpdateVirtualModuleUseCase;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
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
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
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
        reset(
            context.getBean(SaveLessonProgressUseCase.class),
            context.getBean(UpdateVirtualModuleUseCase.class),
            context.getBean(UpdateVirtualLessonUseCase.class),
            context.getBean(CreateVirtualLessonUseCase.class)
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

    private static final String MODULE_EDIT = "/api/v1/admin/virtual/modules/" + ID;
    private static final String LESSON_EDIT = "/api/v1/admin/virtual/lessons/" + ID;

    /** Edit bodies whose constrained field is negative: the domain rejects them too, but later. */
    private static Stream<Arguments> negativeEdits() {
        return Stream.of(
            Arguments.of(MODULE_EDIT, "{\"order\": -1}"),
            Arguments.of(MODULE_EDIT, "{\"title\": \"M1\", \"order\": -5}"),
            Arguments.of(LESSON_EDIT, "{\"durationMinutes\": -1}"),
            Arguments.of(LESSON_EDIT, "{\"order\": -1}"),
            Arguments.of(LESSON_EDIT, "{\"free\": true, \"durationMinutes\": 10, \"order\": -3}")
        );
    }

    @ParameterizedTest(name = "{0} {1}")
    @MethodSource("negativeEdits")
    void a_negative_order_or_duration_on_an_edit_answers_400_invalid_request_and_never_reaches_the_use_case(
        String url, String body
    ) throws Exception {
        MockHttpServletResponse response = mockMvc.perform(json(HttpMethod.PUT, url, body)).andReturn().getResponse();

        assertInvalidRequestProblem(response);
        verify(context.getBean(UpdateVirtualModuleUseCase.class), never())
            .update(anyString(), any(), any(), anyBoolean());
        verify(context.getBean(UpdateVirtualLessonUseCase.class), never())
            .update(anyString(), any(), any(), anyBoolean());
    }

    @Test
    void editing_a_module_with_a_zero_order_or_without_the_constrained_field_still_answers_200() throws Exception {
        UpdateVirtualModuleUseCase useCase = context.getBean(UpdateVirtualModuleUseCase.class);
        when(useCase.update(anyString(), any(), any(), anyBoolean()))
            .thenReturn(new VirtualModuleManagementResult(ID, ID, "M1", 0));

        MockHttpServletResponse zero = mockMvc.perform(json(HttpMethod.PUT, MODULE_EDIT, "{\"order\": 0}"))
            .andReturn().getResponse();
        MockHttpServletResponse omitted = mockMvc.perform(json(HttpMethod.PUT, MODULE_EDIT, "{\"title\": \"M1\"}"))
            .andReturn().getResponse();

        assertThat(zero.getStatus()).isEqualTo(200);
        assertThat(omitted.getStatus()).isEqualTo(200);
        ArgumentCaptor<UpdateVirtualModuleCommand> commands = ArgumentCaptor.forClass(UpdateVirtualModuleCommand.class);
        verify(useCase, times(2)).update(anyString(), commands.capture(), any(), anyBoolean());
        assertThat(commands.getAllValues().get(0).order()).contains(0);
        assertThat(commands.getAllValues().get(1).order()).isEmpty();
    }

    @Test
    void editing_a_lesson_with_zero_values_or_without_the_constrained_fields_still_answers_200() throws Exception {
        UpdateVirtualLessonUseCase useCase = context.getBean(UpdateVirtualLessonUseCase.class);
        when(useCase.update(anyString(), any(), any(), anyBoolean()))
            .thenReturn(new VirtualLessonManagementResult(ID, ID, ID, "L1", "d", "v", 0, false, 0));

        MockHttpServletResponse zero = mockMvc
            .perform(json(HttpMethod.PUT, LESSON_EDIT, "{\"durationMinutes\": 0, \"order\": 0}"))
            .andReturn().getResponse();
        MockHttpServletResponse omitted = mockMvc.perform(json(HttpMethod.PUT, LESSON_EDIT, "{\"free\": true}"))
            .andReturn().getResponse();

        assertThat(zero.getStatus()).isEqualTo(200);
        assertThat(omitted.getStatus()).isEqualTo(200);
        ArgumentCaptor<UpdateVirtualLessonCommand> commands = ArgumentCaptor.forClass(UpdateVirtualLessonCommand.class);
        verify(useCase, times(2)).update(anyString(), commands.capture(), any(), anyBoolean());
        assertThat(commands.getAllValues().get(0).durationMinutes()).contains(0);
        assertThat(commands.getAllValues().get(0).order()).contains(0);
        assertThat(commands.getAllValues().get(1).durationMinutes()).isEmpty();
        assertThat(commands.getAllValues().get(1).order()).isEmpty();
        assertThat(commands.getAllValues().get(1).free()).isEqualTo(Optional.of(true));
    }

    private static final String LESSON_CREATE = "/api/v1/admin/virtual/modules/" + ID + "/lessons";

    private static String createLessonBody(String videoField) {
        return "{\"title\": \"L1\", \"description\": \"d\", \"durationMinutes\": 10" + videoField + "}";
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "\\t\\n"})
    void a_blank_video_id_on_a_lesson_edit_answers_400_invalid_request_and_never_reaches_the_use_case(String blank)
        throws Exception {
        UpdateVirtualLessonUseCase useCase = context.getBean(UpdateVirtualLessonUseCase.class);
        when(useCase.update(anyString(), any(), any(), anyBoolean()))
            .thenReturn(new VirtualLessonManagementResult(ID, ID, ID, "L1", "d", "v", 0, false, 0));

        MockHttpServletResponse response = mockMvc
            .perform(json(HttpMethod.PUT, LESSON_EDIT, "{\"videoId\": \"" + blank + "\"}"))
            .andReturn().getResponse();

        assertInvalidRequestProblem(response);
        verify(useCase, never()).update(anyString(), any(), any(), anyBoolean());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "\\t\\n"})
    void a_blank_video_id_on_a_lesson_creation_answers_400_invalid_request_and_never_reaches_the_use_case(
        String blank
    ) throws Exception {
        CreateVirtualLessonUseCase useCase = context.getBean(CreateVirtualLessonUseCase.class);
        when(useCase.create(anyString(), any(), any(), anyBoolean()))
            .thenReturn(new VirtualLessonManagementResult(ID, ID, ID, "L1", "d", "v", 10, false, 0));

        MockHttpServletResponse response = mockMvc
            .perform(json(HttpMethod.POST, LESSON_CREATE, createLessonBody(", \"videoId\": \"" + blank + "\"")))
            .andReturn().getResponse();

        assertInvalidRequestProblem(response);
        verify(useCase, never()).create(anyString(), any(), any(), anyBoolean());
    }

    @Test
    void a_null_or_omitted_video_id_on_a_lesson_edit_means_unchanged_and_answers_200() throws Exception {
        UpdateVirtualLessonUseCase useCase = context.getBean(UpdateVirtualLessonUseCase.class);
        when(useCase.update(anyString(), any(), any(), anyBoolean()))
            .thenReturn(new VirtualLessonManagementResult(ID, ID, ID, "L1", "d", "v", 0, false, 0));

        MockHttpServletResponse explicitNull = mockMvc
            .perform(json(HttpMethod.PUT, LESSON_EDIT, "{\"videoId\": null, \"free\": true}"))
            .andReturn().getResponse();
        MockHttpServletResponse withVideo = mockMvc
            .perform(json(HttpMethod.PUT, LESSON_EDIT, "{\"videoId\": \"bunny-1\"}"))
            .andReturn().getResponse();

        assertThat(explicitNull.getStatus()).isEqualTo(200);
        assertThat(withVideo.getStatus()).isEqualTo(200);
        ArgumentCaptor<UpdateVirtualLessonCommand> commands = ArgumentCaptor.forClass(UpdateVirtualLessonCommand.class);
        verify(useCase, times(2)).update(anyString(), commands.capture(), any(), anyBoolean());
        assertThat(commands.getAllValues().get(0).videoId()).isEmpty();
        assertThat(commands.getAllValues().get(1).videoId()).contains("bunny-1");
    }

    @Test
    void a_null_or_omitted_video_id_on_a_lesson_creation_means_a_stub_lesson_and_answers_201() throws Exception {
        CreateVirtualLessonUseCase useCase = context.getBean(CreateVirtualLessonUseCase.class);
        when(useCase.create(anyString(), any(), any(), anyBoolean()))
            .thenReturn(new VirtualLessonManagementResult(ID, ID, ID, "L1", "d", null, 10, false, 0));

        MockHttpServletResponse omitted = mockMvc.perform(json(HttpMethod.POST, LESSON_CREATE, createLessonBody("")))
            .andReturn().getResponse();
        MockHttpServletResponse explicitNull = mockMvc
            .perform(json(HttpMethod.POST, LESSON_CREATE, createLessonBody(", \"videoId\": null")))
            .andReturn().getResponse();

        assertThat(omitted.getStatus()).isEqualTo(201);
        assertThat(explicitNull.getStatus()).isEqualTo(201);
        ArgumentCaptor<CreateVirtualLessonCommand> commands = ArgumentCaptor.forClass(CreateVirtualLessonCommand.class);
        verify(useCase, times(2)).create(anyString(), commands.capture(), any(), anyBoolean());
        assertThat(commands.getAllValues()).extracting(CreateVirtualLessonCommand::videoId).containsOnlyNulls();
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
