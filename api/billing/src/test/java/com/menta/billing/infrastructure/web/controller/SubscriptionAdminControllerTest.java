package com.menta.billing.infrastructure.web.controller;

import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.menta.billing.application.dto.AssignTrialCommand;
import com.menta.billing.application.dto.CancelSubscriptionCommand;
import com.menta.billing.application.dto.CancellationResult;
import com.menta.billing.application.dto.CancellationTarget;
import com.menta.billing.application.dto.TrialAssignmentResult;
import com.menta.billing.application.port.in.AssignTrialSubscriptionUseCase;
import com.menta.billing.application.port.in.CancelSubscriptionUseCase;
import com.menta.billing.domain.exception.PlanNotAvailableException;
import com.menta.billing.domain.exception.SubscriptionNotFoundException;
import com.menta.billing.domain.exception.UserNotFoundException;
import com.menta.billing.domain.model.SubscriptionStatus;
import com.menta.billing.domain.model.SubscriptionType;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * Controller-level coverage for the admin cancellation route (US-BILLING-011 escenarios 8/9) and
 * the admin trial-grant route (US-BILLING-012, Phase 4).
 *
 * <p>Standalone {@code MockMvc} setup does not run {@code SecurityConfig}'s filter chain, so
 * the true 403 for a non-admin caller (S4/S11) is regression-tested at the security-matcher
 * level ({@code SecurityConfigTest}), not here. This class verifies the controller's own
 * defense-in-depth {@code isAdmin(...)} pass-through and the mandatory-field contracts.</p>
 */
class SubscriptionAdminControllerTest {

    private static final UUID SUBSCRIPTION_ID = UUID.randomUUID();

    private CancelSubscriptionUseCase useCase;
    private AssignTrialSubscriptionUseCase assignTrialUseCase;
    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        useCase = mock(CancelSubscriptionUseCase.class);
        assignTrialUseCase = mock(AssignTrialSubscriptionUseCase.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new SubscriptionAdminController(useCase, assignTrialUseCase))
            .setControllerAdvice(new SubscriptionExceptionHandler())
            .setMessageConverters(new MappingJackson2HttpMessageConverter(Jackson2ObjectMapperBuilder.json()
                .featuresToDisable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .build()))
            .build();
    }

    private static Authentication authOf(UUID userId, String role) {
        return new UsernamePasswordAuthenticationToken(
            userId.toString(), null, List.of(new SimpleGrantedAuthority("ROLE_" + role))
        );
    }

    private static CancellationResult result() {
        return new CancellationResult(
            SUBSCRIPTION_ID.toString(), SubscriptionStatus.CANCELLED, Instant.parse("2026-09-17T12:00:00Z"),
            "Política de reembolso"
        );
    }

    private String body(String reason) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("reason", reason);
        return objectMapper.writeValueAsString(body);
    }

    @Test
    void cancel_with_a_valid_reason_returns_200_and_forwards_it_to_the_use_case() throws Exception {
        UUID adminId = UUID.randomUUID();
        when(useCase.cancel(any())).thenReturn(result());

        mockMvc.perform(delete("/api/v1/admin/billing/subscriptions/{id}", SUBSCRIPTION_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("cliente lo solicitó por soporte"))
                .principal(authOf(adminId, "ADMIN")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status", is("CANCELLED")))
            .andExpect(jsonPath("$.cancellationPolicy", is("Política de reembolso")));

        ArgumentCaptor<CancelSubscriptionCommand> command = ArgumentCaptor.forClass(CancelSubscriptionCommand.class);
        verify(useCase).cancel(command.capture());
        Assertions.assertThat(command.getValue().target()).isEqualTo(new CancellationTarget.ById(SUBSCRIPTION_ID));
        Assertions.assertThat(command.getValue().actingUserId()).isEqualTo(adminId);
        Assertions.assertThat(command.getValue().isAdmin()).isTrue();
        Assertions.assertThat(command.getValue().reason()).isEqualTo("cliente lo solicitó por soporte");
    }

    /** The response shape carries no cancellationReason — same D2 discipline as the /me route. */
    @Test
    void cancel_response_never_includes_a_cancellation_reason_key() throws Exception {
        when(useCase.cancel(any())).thenReturn(result());

        mockMvc.perform(delete("/api/v1/admin/billing/subscriptions/{id}", SUBSCRIPTION_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("motivo"))
                .principal(authOf(UUID.randomUUID(), "ADMIN")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.cancellationReason").doesNotExist());
    }

    @Test
    void a_blank_reason_is_rejected_with_400_before_the_use_case_runs() throws Exception {
        mockMvc.perform(delete("/api/v1/admin/billing/subscriptions/{id}", SUBSCRIPTION_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("   "))
                .principal(authOf(UUID.randomUUID(), "ADMIN")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code", is("INVALID_REQUEST")));

        verify(useCase, never()).cancel(any());
    }

    @Test
    void an_absent_reason_is_rejected_with_400_before_the_use_case_runs() throws Exception {
        mockMvc.perform(delete("/api/v1/admin/billing/subscriptions/{id}", SUBSCRIPTION_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}")
                .principal(authOf(UUID.randomUUID(), "ADMIN")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code", is("INVALID_REQUEST")));

        verify(useCase, never()).cancel(any());
    }

    @Test
    void a_missing_subscription_returns_404() throws Exception {
        when(useCase.cancel(any())).thenThrow(new SubscriptionNotFoundException());

        mockMvc.perform(delete("/api/v1/admin/billing/subscriptions/{id}", SUBSCRIPTION_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("motivo"))
                .principal(authOf(UUID.randomUUID(), "ADMIN")))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code", is("SUBSCRIPTION_NOT_FOUND")));
    }

    /**
     * A non-admin authenticated caller passed directly to the controller (bypassing
     * {@code SecurityConfig}'s own gate) still forwards {@code isAdmin=false} — the use case
     * then rejects it as {@code SubscriptionNotFoundException} (anti-oracle, design.md A5).
     */
    @Test
    void a_non_admin_principal_is_passed_through_as_not_admin() throws Exception {
        when(useCase.cancel(any())).thenThrow(new SubscriptionNotFoundException());
        UUID studentId = UUID.randomUUID();

        mockMvc.perform(delete("/api/v1/admin/billing/subscriptions/{id}", SUBSCRIPTION_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("motivo"))
                .principal(authOf(studentId, "STUDENT")))
            .andExpect(status().isNotFound());

        ArgumentCaptor<CancelSubscriptionCommand> command = ArgumentCaptor.forClass(CancelSubscriptionCommand.class);
        verify(useCase).cancel(command.capture());
        Assertions.assertThat(command.getValue().isAdmin()).isFalse();
    }

    @Test
    void a_subscription_id_that_is_not_a_uuid_is_a_400_not_a_500() throws Exception {
        mockMvc.perform(delete("/api/v1/admin/billing/subscriptions/{id}", "not-a-uuid")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("motivo"))
                .principal(authOf(UUID.randomUUID(), "ADMIN")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code", is("INVALID_REQUEST")));
    }

    // --- POST /trial (US-BILLING-012, Phase 4) -----------------------------

    private static TrialAssignmentResult trialResult() {
        return new TrialAssignmentResult(
            SUBSCRIPTION_ID.toString(), UUID.randomUUID().toString(), UUID.randomUUID().toString(),
            SubscriptionType.TRIAL, SubscriptionStatus.ACTIVE, Instant.parse("2026-09-17T12:00:00Z"),
            Instant.parse("2026-09-24T12:00:00Z"), 7
        );
    }

    private String trialBody(String userId, String planId, String reason, Integer days) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("userId", userId);
        body.put("planId", planId);
        body.put("reason", reason);
        if (days != null) {
            body.put("days", days);
        }
        return objectMapper.writeValueAsString(body);
    }

    /** Escenario "Admin grants a trial subscription" [S1]. */
    @Test
    void assign_trial_with_a_valid_request_returns_201_and_forwards_it_to_the_use_case() throws Exception {
        UUID adminId = UUID.randomUUID();
        UUID targetUserId = UUID.randomUUID();
        String planId = UUID.randomUUID().toString();
        when(assignTrialUseCase.assign(any())).thenReturn(trialResult());

        mockMvc.perform(post("/api/v1/admin/billing/subscriptions/trial")
                .contentType(MediaType.APPLICATION_JSON)
                .content(trialBody(targetUserId.toString(), planId, "evaluación de catálogo completo", 7))
                .principal(authOf(adminId, "ADMIN")))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.type", is("TRIAL")))
            .andExpect(jsonPath("$.status", is("ACTIVE")))
            .andExpect(jsonPath("$.days", is(7)));

        ArgumentCaptor<AssignTrialCommand> command = ArgumentCaptor.forClass(AssignTrialCommand.class);
        verify(assignTrialUseCase).assign(command.capture());
        Assertions.assertThat(command.getValue().userId()).isEqualTo(targetUserId);
        Assertions.assertThat(command.getValue().planId()).isEqualTo(planId);
        Assertions.assertThat(command.getValue().actingUserId()).isEqualTo(adminId);
        Assertions.assertThat(command.getValue().isAdmin()).isTrue();
        Assertions.assertThat(command.getValue().reason()).isEqualTo("evaluación de catálogo completo");
        Assertions.assertThat(command.getValue().days()).isEqualTo(7);
    }

    /** Same structural-absence discipline as the cancellation response (design.md D3/D2 of #130). */
    @Test
    void assign_trial_response_never_includes_the_admins_reason() throws Exception {
        when(assignTrialUseCase.assign(any())).thenReturn(trialResult());

        mockMvc.perform(post("/api/v1/admin/billing/subscriptions/trial")
                .contentType(MediaType.APPLICATION_JSON)
                .content(trialBody(UUID.randomUUID().toString(), UUID.randomUUID().toString(), "motivo", 7))
                .principal(authOf(UUID.randomUUID(), "ADMIN")))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.reason").doesNotExist());
    }

    /** [S2]. */
    @Test
    void a_blank_trial_reason_is_rejected_with_400_before_the_use_case_runs() throws Exception {
        mockMvc.perform(post("/api/v1/admin/billing/subscriptions/trial")
                .contentType(MediaType.APPLICATION_JSON)
                .content(trialBody(UUID.randomUUID().toString(), UUID.randomUUID().toString(), "   ", 7))
                .principal(authOf(UUID.randomUUID(), "ADMIN")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code", is("INVALID_REQUEST")));

        verifyNoInteractions(assignTrialUseCase);
    }

    /** [S2]. */
    @Test
    void an_absent_trial_reason_is_rejected_with_400_before_the_use_case_runs() throws Exception {
        mockMvc.perform(post("/api/v1/admin/billing/subscriptions/trial")
                .contentType(MediaType.APPLICATION_JSON)
                .content(trialBody(UUID.randomUUID().toString(), UUID.randomUUID().toString(), null, 7))
                .principal(authOf(UUID.randomUUID(), "ADMIN")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code", is("INVALID_REQUEST")));

        verifyNoInteractions(assignTrialUseCase);
    }

    /** [S3]. */
    @Test
    void a_zero_days_value_is_rejected_with_400_and_nothing_is_created() throws Exception {
        mockMvc.perform(post("/api/v1/admin/billing/subscriptions/trial")
                .contentType(MediaType.APPLICATION_JSON)
                .content(trialBody(UUID.randomUUID().toString(), UUID.randomUUID().toString(), "motivo", 0))
                .principal(authOf(UUID.randomUUID(), "ADMIN")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code", is("INVALID_REQUEST")));

        verifyNoInteractions(assignTrialUseCase);
    }

    /** [S3]. */
    @Test
    void a_negative_days_value_is_rejected_with_400_and_nothing_is_created() throws Exception {
        mockMvc.perform(post("/api/v1/admin/billing/subscriptions/trial")
                .contentType(MediaType.APPLICATION_JSON)
                .content(trialBody(UUID.randomUUID().toString(), UUID.randomUUID().toString(), "motivo", -3))
                .principal(authOf(UUID.randomUUID(), "ADMIN")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code", is("INVALID_REQUEST")));

        verifyNoInteractions(assignTrialUseCase);
    }

    /** [S3] — an absent {@code days} value must not silently substitute the plan's own duration. */
    @Test
    void an_absent_days_value_is_rejected_with_400_and_nothing_is_created() throws Exception {
        mockMvc.perform(post("/api/v1/admin/billing/subscriptions/trial")
                .contentType(MediaType.APPLICATION_JSON)
                .content(trialBody(UUID.randomUUID().toString(), UUID.randomUUID().toString(), "motivo", null))
                .principal(authOf(UUID.randomUUID(), "ADMIN")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code", is("INVALID_REQUEST")));

        verifyNoInteractions(assignTrialUseCase);
    }

    /**
     * A non-admin authenticated caller passed directly to the controller (bypassing {@code
     * SecurityConfig}'s own gate) still forwards {@code isAdmin=false} — the use case then
     * rejects it as {@code UserNotFoundException} (anti-oracle, design.md A4), same shape as an
     * unknown user. The real {@code 403} (S4) is regression-tested at {@code SecurityConfigTest}.
     */
    @Test
    void a_non_admin_principal_is_passed_through_as_not_admin_for_the_trial_route() throws Exception {
        when(assignTrialUseCase.assign(any())).thenThrow(new UserNotFoundException());
        UUID studentId = UUID.randomUUID();

        mockMvc.perform(post("/api/v1/admin/billing/subscriptions/trial")
                .contentType(MediaType.APPLICATION_JSON)
                .content(trialBody(UUID.randomUUID().toString(), UUID.randomUUID().toString(), "motivo", 7))
                .principal(authOf(studentId, "STUDENT")))
            .andExpect(status().isNotFound());

        ArgumentCaptor<AssignTrialCommand> command = ArgumentCaptor.forClass(AssignTrialCommand.class);
        verify(assignTrialUseCase).assign(command.capture());
        Assertions.assertThat(command.getValue().isAdmin()).isFalse();
    }

    /** [S9] — an unknown {@code userId} maps to 404, not a 500. */
    @Test
    void an_unknown_user_id_maps_to_404() throws Exception {
        when(assignTrialUseCase.assign(any())).thenThrow(new UserNotFoundException());

        mockMvc.perform(post("/api/v1/admin/billing/subscriptions/trial")
                .contentType(MediaType.APPLICATION_JSON)
                .content(trialBody(UUID.randomUUID().toString(), UUID.randomUUID().toString(), "motivo", 7))
                .principal(authOf(UUID.randomUUID(), "ADMIN")))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code", is("USER_NOT_FOUND")));
    }

    /** Proves the shared {@code @SubscriptionEndpoint} advice maps this route's own
     * {@link PlanNotAvailableException} to 422, not just the sibling checkout route. */
    @Test
    void an_unavailable_plan_maps_to_422() throws Exception {
        when(assignTrialUseCase.assign(any())).thenThrow(new PlanNotAvailableException());

        mockMvc.perform(post("/api/v1/admin/billing/subscriptions/trial")
                .contentType(MediaType.APPLICATION_JSON)
                .content(trialBody(UUID.randomUUID().toString(), UUID.randomUUID().toString(), "motivo", 7))
                .principal(authOf(UUID.randomUUID(), "ADMIN")))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.code", is("PLAN_NOT_AVAILABLE")));
    }

    @Test
    void a_target_user_id_that_is_not_a_uuid_is_a_400_not_a_500() throws Exception {
        mockMvc.perform(post("/api/v1/admin/billing/subscriptions/trial")
                .contentType(MediaType.APPLICATION_JSON)
                .content(trialBody("not-a-uuid", UUID.randomUUID().toString(), "motivo", 7))
                .principal(authOf(UUID.randomUUID(), "ADMIN")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code", is("INVALID_REQUEST")));

        verifyNoInteractions(assignTrialUseCase);
    }
}
