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
import com.menta.billing.application.dto.BankTransferInstructions;
import com.menta.billing.application.dto.CreatePhysicalPurchaseCheckoutCommand;
import com.menta.billing.application.dto.PhysicalPurchaseCheckoutResult;
import com.menta.billing.application.port.in.CreatePhysicalPurchaseCheckoutUseCase;
import com.menta.billing.domain.exception.PaymentPreferenceUnavailableException;
import com.menta.billing.domain.exception.PhysicalCapacityUnavailableException;
import com.menta.billing.domain.exception.PhysicalCourseQuoteExpiredException;
import com.menta.billing.domain.model.Money;
import com.menta.billing.domain.model.PaymentMethod;
import java.math.BigDecimal;
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
            "pay-1", QUOTE_ID, "PENDING", "pref-1", "https://mp.example/checkout/pref-1", "PHY-pay-1", null
        );
    }

    /** #36 P5: what the router builds for the {@code BANK_TRANSFER} arm — no provider fields, instructions set. */
    private static PhysicalPurchaseCheckoutResult bankTransferResult() {
        BankTransferInstructions instructions = new BankTransferInstructions(
            "0000003100000000000000", "menta.dance", "Menta Dance SRL", "30-00000000-0",
            Money.of(new BigDecimal("10000.00"), "ARS"), "PHY-BT-pay-2"
        );
        return new PhysicalPurchaseCheckoutResult(
            "pay-2", QUOTE_ID, "PENDING", null, null, "PHY-BT-pay-2", instructions
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

    /**
     * #36 P5: {@code BANK_TRANSFER} is a legitimate {@code paymentMethod} for this endpoint since
     * {@code RoutingCreatePhysicalPurchaseCheckoutUseCase} (#36 P4) dispatches on this same field —
     * a DTO-level restriction to Checkout Pro only would make the bank-transfer route unreachable
     * over HTTP regardless of the router underneath, same rationale as {@code
     * CreateSubscriptionRequest}'s own javadoc (#252).
     */
    @Test
    void bank_transfer_is_accepted_and_the_response_surfaces_the_transfer_instructions() throws Exception {
        when(useCase.create(any())).thenReturn(bankTransferResult());

        mockMvc.perform(post("/api/v1/billing/physical/purchases")
                .with(authenticatedAs(USER_ID))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(QUOTE_ID, "BANK_TRANSFER", "idem-1")))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.paymentId", is("pay-2")))
            .andExpect(jsonPath("$.providerPreferenceId").value(org.hamcrest.Matchers.nullValue()))
            .andExpect(jsonPath("$.checkoutUrl").value(org.hamcrest.Matchers.nullValue()))
            .andExpect(jsonPath("$.bankTransferInstructions.cbu", is("0000003100000000000000")))
            .andExpect(jsonPath("$.bankTransferInstructions.alias", is("menta.dance")))
            .andExpect(jsonPath("$.bankTransferInstructions.holder", is("Menta Dance SRL")))
            .andExpect(jsonPath("$.bankTransferInstructions.cuit", is("30-00000000-0")))
            .andExpect(jsonPath("$.bankTransferInstructions.reference", is("PHY-BT-pay-2")));
    }

    /** A Mercado Pago result never carries transfer instructions — the field stays absent, not merely null. */
    @Test
    void mercado_pago_response_carries_no_bank_transfer_instructions() throws Exception {
        when(useCase.create(any())).thenReturn(result());

        mockMvc.perform(post("/api/v1/billing/physical/purchases")
                .with(authenticatedAs(USER_ID))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(QUOTE_ID, "MERCADO_PAGO", "idem-1")))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.bankTransferInstructions").value(org.hamcrest.Matchers.nullValue()));
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
