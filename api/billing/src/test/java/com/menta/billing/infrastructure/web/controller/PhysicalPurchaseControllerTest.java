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
import com.menta.billing.application.dto.CreatePhysicalPurchaseCheckoutCommand;
import com.menta.billing.application.dto.PhysicalPurchaseCheckoutResult;
import com.menta.billing.application.port.in.CreatePhysicalPurchaseCheckoutUseCase;
import com.menta.billing.domain.exception.PaymentPreferenceUnavailableException;
import com.menta.billing.domain.exception.PhysicalCapacityUnavailableException;
import com.menta.billing.domain.exception.PhysicalCourseQuoteExpiredException;
import com.menta.billing.domain.model.PaymentMethod;
import java.util.HashMap;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class PhysicalPurchaseControllerTest {

    private static final UUID USER_ID = UUID.randomUUID();
    private static final String QUOTE_ID = UUID.randomUUID().toString();

    private CreatePhysicalPurchaseCheckoutUseCase useCase;
    private PhysicalPurchaseController controller;
    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        useCase = mock(CreatePhysicalPurchaseCheckoutUseCase.class);
        controller = new PhysicalPurchaseController(useCase);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
            .setControllerAdvice(new PhysicalPurchaseExceptionHandler())
            .setMessageConverters(new MappingJackson2HttpMessageConverter(Jackson2ObjectMapperBuilder.json()
                .featuresToDisable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .build()))
            .build();
    }

    private static RequestPostProcessor authenticatedAs(UUID userId) {
        return request -> {
            request.setUserPrincipal(new UsernamePasswordAuthenticationToken(userId.toString(), "n/a"));
            return request;
        };
    }

    private String body(String quoteId, String paymentMethod, String idempotencyKey) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("quoteId", quoteId);
        body.put("paymentMethod", paymentMethod);
        body.put("idempotencyKey", idempotencyKey);
        return objectMapper.writeValueAsString(body);
    }

    private static PhysicalPurchaseCheckoutResult result() {
        return new PhysicalPurchaseCheckoutResult(
            "pay-1", QUOTE_ID, "PENDING", "pref-1", "https://mp.example/checkout/pref-1", "PHY-pay-1"
        );
    }

    @Test
    void returns_201_with_the_payment_identifier_and_the_checkout_url() throws Exception {
        when(useCase.create(any())).thenReturn(result());

        mockMvc.perform(post("/api/v1/billing/physical/purchases")
                .with(authenticatedAs(USER_ID))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(QUOTE_ID, "MERCADO_PAGO", "idem-1")))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.paymentId", is("pay-1")))
            .andExpect(jsonPath("$.quoteId", is(QUOTE_ID)))
            .andExpect(jsonPath("$.status", is("PENDING")))
            .andExpect(jsonPath("$.providerPreferenceId", is("pref-1")))
            .andExpect(jsonPath("$.checkoutUrl", is("https://mp.example/checkout/pref-1")))
            .andExpect(jsonPath("$.externalReference", is("PHY-pay-1")));
    }

    /** The owning user comes from the token — the body has nowhere to name someone else. */
    @Test
    void binds_the_checkout_to_the_token_subject_and_never_to_a_body_field() throws Exception {
        when(useCase.create(any())).thenReturn(result());
        Map<String, Object> spoofed = new HashMap<>();
        spoofed.put("quoteId", QUOTE_ID);
        spoofed.put("paymentMethod", "MERCADO_PAGO");
        spoofed.put("idempotencyKey", "idem-1");
        spoofed.put("userId", UUID.randomUUID().toString());

        mockMvc.perform(post("/api/v1/billing/physical/purchases")
                .with(authenticatedAs(USER_ID))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(spoofed)))
            .andExpect(status().isCreated());

        ArgumentCaptor<CreatePhysicalPurchaseCheckoutCommand> command =
            ArgumentCaptor.forClass(CreatePhysicalPurchaseCheckoutCommand.class);
        verify(useCase).create(command.capture());
        Assertions.assertThat(command.getValue().userId()).isEqualTo(USER_ID);
        Assertions.assertThat(command.getValue().quoteId()).isEqualTo(QUOTE_ID);
        Assertions.assertThat(command.getValue().paymentMethod()).isEqualTo(PaymentMethod.MERCADO_PAGO);
        Assertions.assertThat(command.getValue().idempotencyKey()).isEqualTo("idem-1");
    }

    @Test
    void a_missing_quote_id_is_rejected_before_the_use_case_runs() throws Exception {
        Map<String, Object> incomplete = new HashMap<>();
        incomplete.put("paymentMethod", "MERCADO_PAGO");
        incomplete.put("idempotencyKey", "idem-1");

        mockMvc.perform(post("/api/v1/billing/physical/purchases")
                .with(authenticatedAs(USER_ID))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(incomplete)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code", is("INVALID_REQUEST")));

        verify(useCase, never()).create(any());
    }

    @Test
    void a_missing_payment_method_is_rejected_before_the_use_case_runs() throws Exception {
        Map<String, Object> incomplete = new HashMap<>();
        incomplete.put("quoteId", QUOTE_ID);
        incomplete.put("idempotencyKey", "idem-1");

        mockMvc.perform(post("/api/v1/billing/physical/purchases")
                .with(authenticatedAs(USER_ID))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(incomplete)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code", is("INVALID_REQUEST")));

        verify(useCase, never()).create(any());
    }

    @Test
    void a_missing_idempotency_key_is_rejected_before_the_use_case_runs() throws Exception {
        mockMvc.perform(post("/api/v1/billing/physical/purchases")
                .with(authenticatedAs(USER_ID))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(QUOTE_ID, "MERCADO_PAGO", " ")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code", is("INVALID_REQUEST")));

        verify(useCase, never()).create(any());
    }

    @Test
    void bank_transfer_is_invalid_for_the_checkout_pro_endpoint() throws Exception {
        mockMvc.perform(post("/api/v1/billing/physical/purchases")
                .with(authenticatedAs(USER_ID))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(QUOTE_ID, "BANK_TRANSFER", "idem-1")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code", is("INVALID_REQUEST")));

        verify(useCase, never()).create(any());
    }

    // --- A7: expired quote is 410 ---

    @Test
    void an_expired_quote_maps_to_410() throws Exception {
        when(useCase.create(any())).thenThrow(new PhysicalCourseQuoteExpiredException());

        mockMvc.perform(post("/api/v1/billing/physical/purchases")
                .with(authenticatedAs(USER_ID))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(QUOTE_ID, "MERCADO_PAGO", "idem-1")))
            .andExpect(status().isGone())
            .andExpect(jsonPath("$.code", is("PHYSICAL_COURSE_QUOTE_EXPIRED")));
    }

    // --- A6/D5: visibly-full quote is 409, distinct code from 410 ---

    @Test
    void a_visibly_full_quote_maps_to_409_with_a_distinct_code_from_the_expired_quote() throws Exception {
        when(useCase.create(any())).thenThrow(new PhysicalCapacityUnavailableException());

        mockMvc.perform(post("/api/v1/billing/physical/purchases")
                .with(authenticatedAs(USER_ID))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(QUOTE_ID, "MERCADO_PAGO", "idem-1")))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code", is("CAPACITY_UNAVAILABLE")));
    }

    @Test
    void a_provider_failure_maps_to_503() throws Exception {
        when(useCase.create(any()))
            .thenThrow(new PaymentPreferenceUnavailableException(new IllegalStateException("down")));

        mockMvc.perform(post("/api/v1/billing/physical/purchases")
                .with(authenticatedAs(USER_ID))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(QUOTE_ID, "MERCADO_PAGO", "idem-1")))
            .andExpect(status().isServiceUnavailable())
            .andExpect(jsonPath("$.code", is("PAYMENT_PREFERENCE_UNAVAILABLE")));
    }
}
