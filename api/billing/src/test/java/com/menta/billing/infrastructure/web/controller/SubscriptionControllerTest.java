package com.menta.billing.infrastructure.web.controller;

import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.menta.billing.application.dto.CreateSubscriptionCheckoutCommand;
import com.menta.billing.application.dto.SubscriptionCheckoutResult;
import com.menta.billing.application.port.in.CreateSubscriptionCheckoutUseCase;
import com.menta.billing.domain.exception.PaymentMethodNotAcceptedException;
import com.menta.billing.domain.exception.PaymentPreferenceUnavailableException;
import com.menta.billing.domain.exception.PlanNotAvailableException;
import com.menta.billing.domain.exception.SubscriptionAlreadyActiveException;
import com.menta.billing.domain.model.PaymentMethod;
import com.menta.billing.domain.model.SubscriptionStatus;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class SubscriptionControllerTest {

    private static final UUID USER_ID = UUID.randomUUID();
    private static final String PLAN_ID = UUID.randomUUID().toString();

    private CreateSubscriptionCheckoutUseCase useCase;
    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        useCase = mock(CreateSubscriptionCheckoutUseCase.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new SubscriptionController(useCase))
            .setControllerAdvice(new SubscriptionExceptionHandler())
            .build();
    }

    private static RequestPostProcessor authenticatedAs(UUID userId) {
        return request -> {
            request.setUserPrincipal(new UsernamePasswordAuthenticationToken(userId.toString(), "n/a"));
            return request;
        };
    }

    private String body(String planId, String paymentMethod, String idempotencyKey) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("planId", planId);
        body.put("paymentMethod", paymentMethod);
        body.put("idempotencyKey", idempotencyKey);
        return objectMapper.writeValueAsString(body);
    }

    private static SubscriptionCheckoutResult result() {
        return new SubscriptionCheckoutResult(
            "sub-1", "pay-1", PLAN_ID, SubscriptionStatus.PENDING, "pref-1",
            "https://mp.example/checkout/pref-1", "SUB-pay-1"
        );
    }

    @Test
    void returns_201_with_the_subscription_identifier_and_the_checkout_url() throws Exception {
        when(useCase.create(any())).thenReturn(result());

        mockMvc.perform(post("/api/v1/billing/subscriptions")
                .with(authenticatedAs(USER_ID))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(PLAN_ID, "MERCADO_PAGO", "idem-1")))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.subscriptionId", is("sub-1")))
            .andExpect(jsonPath("$.paymentId", is("pay-1")))
            .andExpect(jsonPath("$.status", is("PENDING")))
            .andExpect(jsonPath("$.providerPreferenceId", is("pref-1")))
            .andExpect(jsonPath("$.externalReference", is("SUB-pay-1")))
            .andExpect(jsonPath("$.checkoutUrl", is("https://mp.example/checkout/pref-1")));
    }

    /** The owning user comes from the token — the body has nowhere to name someone else. */
    @Test
    void binds_the_checkout_to_the_token_subject_and_never_to_a_body_field() throws Exception {
        when(useCase.create(any())).thenReturn(result());
        Map<String, Object> spoofed = new HashMap<>();
        spoofed.put("planId", PLAN_ID);
        spoofed.put("paymentMethod", "MERCADO_PAGO");
        spoofed.put("idempotencyKey", "idem-1");
        spoofed.put("userId", UUID.randomUUID().toString());

        mockMvc.perform(post("/api/v1/billing/subscriptions")
                .with(authenticatedAs(USER_ID))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(spoofed)))
            .andExpect(status().isCreated());

        ArgumentCaptor<CreateSubscriptionCheckoutCommand> command =
            ArgumentCaptor.forClass(CreateSubscriptionCheckoutCommand.class);
        verify(useCase).create(command.capture());
        Assertions.assertThat(command.getValue().userId()).isEqualTo(USER_ID);
        Assertions.assertThat(command.getValue().planId()).isEqualTo(PLAN_ID);
        Assertions.assertThat(command.getValue().paymentMethod()).isEqualTo(PaymentMethod.MERCADO_PAGO);
        Assertions.assertThat(command.getValue().idempotencyKey()).isEqualTo("idem-1");
    }

    @Test
    void an_unavailable_plan_maps_to_422() throws Exception {
        when(useCase.create(any())).thenThrow(new PlanNotAvailableException());

        mockMvc.perform(post("/api/v1/billing/subscriptions")
                .with(authenticatedAs(USER_ID))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(PLAN_ID, "MERCADO_PAGO", "idem-1")))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.code", is("PLAN_NOT_AVAILABLE")));
    }

    @Test
    void bank_transfer_is_invalid_for_the_checkout_pro_endpoint() throws Exception {
        mockMvc.perform(post("/api/v1/billing/subscriptions")
                .with(authenticatedAs(USER_ID))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(PLAN_ID, "BANK_TRANSFER", "idem-1")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code", is("INVALID_REQUEST")));

        verify(useCase, never()).create(any());
    }

    @Test
    void an_unaccepted_payment_method_maps_to_422_and_lists_what_the_plan_accepts() throws Exception {
        when(useCase.create(any())).thenThrow(new PaymentMethodNotAcceptedException(
            PaymentMethod.MERCADO_PAGO, Set.of(PaymentMethod.BANK_TRANSFER)
        ));

        mockMvc.perform(post("/api/v1/billing/subscriptions")
                .with(authenticatedAs(USER_ID))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(PLAN_ID, "MERCADO_PAGO", "idem-1")))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.code", is("PAYMENT_METHOD_NOT_ACCEPTED")))
            .andExpect(jsonPath("$.acceptedPaymentMethods[0]", is("BANK_TRANSFER")));
    }

    @Test
    void an_existing_subscription_maps_to_409_and_reports_its_expiry() throws Exception {
        when(useCase.create(any()))
            .thenThrow(new SubscriptionAlreadyActiveException(Instant.parse("2026-09-17T12:00:00Z")));

        mockMvc.perform(post("/api/v1/billing/subscriptions")
                .with(authenticatedAs(USER_ID))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(PLAN_ID, "MERCADO_PAGO", "idem-1")))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code", is("SUBSCRIPTION_ALREADY_ACTIVE")))
            .andExpect(jsonPath("$.currentEndDate", is("2026-09-17T12:00:00Z")));
    }

    @Test
    void a_pending_checkout_conflict_omits_the_expiry_it_does_not_have() throws Exception {
        when(useCase.create(any())).thenThrow(new SubscriptionAlreadyActiveException(null));

        mockMvc.perform(post("/api/v1/billing/subscriptions")
                .with(authenticatedAs(USER_ID))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(PLAN_ID, "MERCADO_PAGO", "idem-1")))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.currentEndDate").doesNotExist());
    }

    @Test
    void a_provider_failure_maps_to_503() throws Exception {
        when(useCase.create(any()))
            .thenThrow(new PaymentPreferenceUnavailableException(new IllegalStateException("down")));

        mockMvc.perform(post("/api/v1/billing/subscriptions")
                .with(authenticatedAs(USER_ID))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(PLAN_ID, "MERCADO_PAGO", "idem-1")))
            .andExpect(status().isServiceUnavailable())
            .andExpect(jsonPath("$.code", is("PAYMENT_PREFERENCE_UNAVAILABLE")));
    }

    @Test
    void a_missing_idempotency_key_is_rejected_before_the_use_case_runs() throws Exception {
        mockMvc.perform(post("/api/v1/billing/subscriptions")
                .with(authenticatedAs(USER_ID))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(PLAN_ID, "MERCADO_PAGO", " ")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code", is("INVALID_REQUEST")));

        verify(useCase, never()).create(any());
    }

    @Test
    void a_plan_id_that_is_not_a_uuid_is_a_400_not_a_500() throws Exception {
        when(useCase.create(any())).thenThrow(new IllegalArgumentException("Invalid PlanId format"));

        mockMvc.perform(post("/api/v1/billing/subscriptions")
                .with(authenticatedAs(USER_ID))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("not-a-uuid", "MERCADO_PAGO", "idem-1")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code", is("INVALID_REQUEST")));
    }
}
