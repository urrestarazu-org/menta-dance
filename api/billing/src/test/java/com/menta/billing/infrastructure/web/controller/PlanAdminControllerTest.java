package com.menta.billing.infrastructure.web.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.menta.billing.application.dto.CreatePlanCommand;
import com.menta.billing.application.dto.PlanCourseResult;
import com.menta.billing.application.dto.PlanDetailResult;
import com.menta.billing.application.port.in.CreatePlanUseCase;
import com.menta.billing.domain.model.PaymentMethod;
import com.menta.billing.domain.model.PlanStatus;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * Controller-level coverage for {@code POST /api/v1/admin/billing/plans} (#762).
 *
 * <p>Standalone {@code MockMvc} does not run {@code SecurityConfig}'s filter chain: the 401 for an
 * anonymous caller and the 403 for a non-admin role are pinned by {@code SecurityConfigTest} and
 * by {@code PlanAdminIntegrationTest}. This class verifies the controller's own defense-in-depth
 * admin check, the request decoding, the 400 mapping and the response shape.</p>
 */
class PlanAdminControllerTest {

    private static final String URL = "/api/v1/admin/billing/plans";
    private static final UUID ADMIN_ID = UUID.randomUUID();
    private static final String PLAN_ID = UUID.randomUUID().toString();

    private CreatePlanUseCase useCase;
    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        useCase = mock(CreatePlanUseCase.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new PlanAdminController(useCase))
            .setControllerAdvice(new PlanAdminExceptionHandler())
            .build();
    }

    private static Authentication authOf(String role) {
        return new UsernamePasswordAuthenticationToken(
            ADMIN_ID.toString(), null, List.of(new SimpleGrantedAuthority("ROLE_" + role))
        );
    }

    private static Map<String, Object> validBody() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", "Plan Mensual");
        body.put("description", "Acceso mensual");
        body.put("price", new BigDecimal("15000.00"));
        body.put("currency", "ARS");
        body.put("durationDays", 30);
        body.put("featured", true);
        body.put("status", "ACTIVE");
        body.put("termsAndConditions", "Términos");
        body.put("cancellationPolicy", "Política de cancelación");
        body.put("paymentMethods", List.of("MERCADO_PAGO", "BANK_TRANSFER"));
        body.put("courseIds", List.of("course-1"));
        return body;
    }

    private static PlanDetailResult created() {
        return new PlanDetailResult(
            PLAN_ID, "Plan Mensual", "Acceso mensual", new BigDecimal("15000.00"), "ARS", 30, true,
            "Términos", "Política de cancelación", List.of(new PlanCourseResult("course-1", "Tango Básico"))
        );
    }

    private MvcResult postAsAdmin(String json) throws Exception {
        return mockMvc.perform(post(URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json)
                .principal(authOf("ADMIN")))
            .andReturn();
    }

    @Test
    void an_admin_creates_a_plan_and_gets_201_with_the_public_detail_shape() throws Exception {
        when(useCase.create(any())).thenReturn(created());

        MvcResult result = mockMvc.perform(post(URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(validBody()))
                .principal(authOf("ADMIN")))
            .andExpect(status().isCreated())
            .andExpect(header().doesNotExist("Location"))
            .andExpect(jsonPath("$.id", is(PLAN_ID)))
            .andExpect(jsonPath("$.price", is(15000.00)))
            .andExpect(jsonPath("$.currency", is("ARS")))
            .andExpect(jsonPath("$.courses[0].id", is("course-1")))
            .andExpect(jsonPath("$.courses[0].name", is("Tango Básico")))
            .andReturn();

        Map<String, Object> body = objectMapper.readValue(result.getResponse().getContentAsString(), Map.class);
        assertThat(body).containsOnlyKeys(
            "id", "name", "description", "price", "currency", "durationDays", "featured",
            "termsAndConditions", "cancellationPolicy", "courses"
        );
    }

    @Test
    void forwards_every_field_and_the_acting_admin_to_the_use_case() throws Exception {
        when(useCase.create(any())).thenReturn(created());

        postAsAdmin(objectMapper.writeValueAsString(validBody()));

        ArgumentCaptor<CreatePlanCommand> command = ArgumentCaptor.forClass(CreatePlanCommand.class);
        verify(useCase).create(command.capture());
        CreatePlanCommand value = command.getValue();
        assertThat(value.name()).isEqualTo("Plan Mensual");
        assertThat(value.description()).isEqualTo("Acceso mensual");
        assertThat(value.price()).isEqualByComparingTo("15000.00");
        assertThat(value.currency()).isEqualTo("ARS");
        assertThat(value.durationDays()).isEqualTo(30);
        assertThat(value.featured()).isTrue();
        assertThat(value.status()).isEqualTo(PlanStatus.ACTIVE);
        assertThat(value.termsAndConditions()).isEqualTo("Términos");
        assertThat(value.cancellationPolicy()).isEqualTo("Política de cancelación");
        assertThat(value.paymentMethods())
            .containsExactlyInAnyOrder(PaymentMethod.MERCADO_PAGO, PaymentMethod.BANK_TRANSFER);
        assertThat(value.courseIds()).containsExactly("course-1");
        assertThat(value.actingAdminId()).isEqualTo(ADMIN_ID);
    }

    @Test
    void an_empty_course_list_is_forwarded_as_is() throws Exception {
        when(useCase.create(any())).thenReturn(created());
        Map<String, Object> body = validBody();
        body.put("courseIds", List.of());

        assertThat(postAsAdmin(objectMapper.writeValueAsString(body)).getResponse().getStatus()).isEqualTo(201);

        ArgumentCaptor<CreatePlanCommand> command = ArgumentCaptor.forClass(CreatePlanCommand.class);
        verify(useCase).create(command.capture());
        assertThat(command.getValue().courseIds()).isEmpty();
    }

    /** Defense in depth: SecurityConfig already rejects these, the controller still refuses them. */
    @Test
    void a_non_admin_principal_is_forbidden_without_reaching_the_use_case() throws Exception {
        mockMvc.perform(post(URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(validBody()))
                .principal(authOf("INSTRUCTOR")))
            .andExpect(status().isForbidden());

        verify(useCase, never()).create(any());
    }

    @Test
    void a_request_without_a_principal_is_forbidden_without_reaching_the_use_case() throws Exception {
        mockMvc.perform(post(URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(validBody())))
            .andExpect(status().isForbidden());

        verify(useCase, never()).create(any());
    }

    private static Arguments invalid(String description, Consumer<Map<String, Object>> mutation) {
        return Arguments.of(description, mutation);
    }

    static Stream<Arguments> invalidBodies() {
        return Stream.of(
            invalid("missing name", body -> body.remove("name")),
            invalid("blank name", body -> body.put("name", "  ")),
            invalid("name longer than the column", body -> body.put("name", "n".repeat(256))),
            invalid("missing description", body -> body.remove("description")),
            invalid("missing price", body -> body.remove("price")),
            invalid("price with more than two decimals", body -> body.put("price", new BigDecimal("10.001"))),
            invalid("price above the column precision", body -> body.put("price", new BigDecimal("100000000"))),
            invalid("missing currency", body -> body.remove("currency")),
            invalid("missing durationDays", body -> body.remove("durationDays")),
            invalid("missing featured", body -> body.remove("featured")),
            invalid("missing status", body -> body.remove("status")),
            invalid("missing terms", body -> body.remove("termsAndConditions")),
            invalid("missing cancellation policy", body -> body.remove("cancellationPolicy")),
            invalid("missing payment methods", body -> body.remove("paymentMethods")),
            invalid("null payment method", body -> body.put("paymentMethods", Arrays.asList("MERCADO_PAGO", null))),
            invalid("missing course ids", body -> body.remove("courseIds")),
            invalid("blank course id", body -> body.put("courseIds", List.of("course-1", " "))),
            invalid("null course id", body -> body.put("courseIds", new ArrayList<>(Arrays.asList((String) null)))),
            invalid("course id longer than the column", body -> body.put("courseIds", List.of("c".repeat(65)))),
            invalid("unknown status", body -> body.put("status", "ARCHIVED")),
            invalid("unknown payment method", body -> body.put("paymentMethods", List.of("CASH"))),
            invalid("durationDays that is not a number", body -> body.put("durationDays", "thirty"))
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidBodies")
    void an_invalid_body_is_rejected_with_400_before_the_use_case_runs(
        String description, Consumer<Map<String, Object>> mutation
    ) throws Exception {
        Map<String, Object> body = validBody();
        mutation.accept(body);

        mockMvc.perform(post(URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body))
                .principal(authOf("ADMIN")))
            .andExpect(status().isBadRequest())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.code", is("INVALID_REQUEST")));

        verify(useCase, never()).create(any());
    }

    @Test
    void a_malformed_json_body_is_rejected_with_400() throws Exception {
        MvcResult result = postAsAdmin("{\"name\": ");

        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        assertThat(result.getResponse().getContentAsString()).contains("\"code\":\"INVALID_REQUEST\"");
        verify(useCase, never()).create(any());
    }

    /** A domain invariant the use case enforces (for instance a zero price) is a 400, never a 500. */
    @Test
    void a_rule_rejected_by_the_use_case_is_a_400_with_the_same_code() throws Exception {
        when(useCase.create(any())).thenThrow(new IllegalArgumentException("price must be positive"));

        mockMvc.perform(post(URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(validBody()))
                .principal(authOf("ADMIN")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code", is("INVALID_REQUEST")))
            .andExpect(jsonPath("$.detail", is("La solicitud contiene datos inválidos.")));
    }
}
