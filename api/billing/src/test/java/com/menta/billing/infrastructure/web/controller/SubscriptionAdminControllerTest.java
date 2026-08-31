package com.menta.billing.infrastructure.web.controller;

import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.menta.billing.application.dto.CancelSubscriptionCommand;
import com.menta.billing.application.dto.CancellationResult;
import com.menta.billing.application.dto.CancellationTarget;
import com.menta.billing.application.port.in.CancelSubscriptionUseCase;
import com.menta.billing.domain.exception.SubscriptionNotFoundException;
import com.menta.billing.domain.model.SubscriptionStatus;
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
 * Controller-level coverage for the admin cancellation route (US-BILLING-011 escenarios 8/9).
 *
 * <p>Standalone {@code MockMvc} setup does not run {@code SecurityConfig}'s filter chain, so
 * the true 403 for a non-admin caller (S11) is regression-tested at the security-matcher level
 * ({@code SecurityConfigTest}), not here. This class verifies the controller's own
 * defense-in-depth {@code isAdmin(...)} pass-through and the mandatory-reason contract.</p>
 */
class SubscriptionAdminControllerTest {

    private static final UUID SUBSCRIPTION_ID = UUID.randomUUID();

    private CancelSubscriptionUseCase useCase;
    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        useCase = mock(CancelSubscriptionUseCase.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new SubscriptionAdminController(useCase))
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
}
