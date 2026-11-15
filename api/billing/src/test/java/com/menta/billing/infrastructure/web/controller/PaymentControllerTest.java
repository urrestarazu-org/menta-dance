package com.menta.billing.infrastructure.web.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.menta.billing.application.dto.OwnPaymentEntry;
import com.menta.billing.application.dto.PaymentStatusResult;
import com.menta.billing.application.dto.SubmitPaymentProofCommand;
import com.menta.billing.application.port.in.GetPaymentUseCase;
import com.menta.billing.application.port.in.ListOwnPaymentsUseCase;
import com.menta.billing.application.port.in.SubmitPaymentProofUseCase;
import com.menta.billing.domain.exception.BankTransferRateLimitedException;
import com.menta.billing.domain.exception.InvalidPaymentsLimitException;
import com.menta.billing.domain.exception.PaymentNotFoundException;
import com.menta.billing.domain.exception.PaymentProofRejectedException;
import com.menta.billing.domain.model.FulfillmentStatus;
import com.menta.billing.domain.model.Money;
import com.menta.billing.domain.model.PaymentId;
import com.menta.billing.domain.model.PaymentMethod;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * MockMvc coverage for {@code POST /api/v1/billing/payments/{id}/proof} (#31, US-BILLING-003,
 * design C8/C12). The owner is read from {@link org.springframework.security.core.Authentication},
 * never the request body — there is no body field for it in the first place, multipart carries
 * only the file — and every domain exception this controller can throw maps to {@code
 * application/problem+json} via {@link PaymentExceptionHandler}.
 */
class PaymentControllerTest {

    private static final UUID USER_ID = UUID.randomUUID();
    private static final PaymentId PAYMENT_ID = PaymentId.generate();

    private SubmitPaymentProofUseCase submitPaymentProofUseCase;
    private GetPaymentUseCase getPaymentUseCase;
    private ListOwnPaymentsUseCase listOwnPaymentsUseCase;
    private PaymentController controller;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        submitPaymentProofUseCase = mock(SubmitPaymentProofUseCase.class);
        getPaymentUseCase = mock(GetPaymentUseCase.class);
        listOwnPaymentsUseCase = mock(ListOwnPaymentsUseCase.class);
        controller = new PaymentController(submitPaymentProofUseCase, getPaymentUseCase, listOwnPaymentsUseCase);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
            .setControllerAdvice(new PaymentExceptionHandler())
            // Matches production's Spring Boot auto-configured ObjectMapper — registers the
            // JavaTimeModule (ISO-8601 Instants, not epoch seconds), which a bare
            // `new ObjectMapper()` does not provide (see SubscriptionControllerTest's own setUp).
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

    private static MockMultipartFile file() {
        return new MockMultipartFile("file", "comprobante.png", "image/png", new byte[]{1, 2, 3});
    }

    @Test
    void a_valid_submission_returns_200() throws Exception {
        mockMvc.perform(multipart("/api/v1/billing/payments/" + PAYMENT_ID + "/proof")
                .file(file())
                .with(authenticatedAs(USER_ID)))
            .andExpect(status().isOk());
    }

    /** C8: the owner comes from the token — there is no body field a client could spoof instead. */
    @Test
    void the_owner_comes_from_the_token_never_the_body() throws Exception {
        mockMvc.perform(multipart("/api/v1/billing/payments/" + PAYMENT_ID + "/proof")
                .file(file())
                .with(authenticatedAs(USER_ID)))
            .andExpect(status().isOk());

        ArgumentCaptor<SubmitPaymentProofCommand> command = ArgumentCaptor.forClass(SubmitPaymentProofCommand.class);
        verify(submitPaymentProofUseCase).submit(command.capture());
        assertThat(command.getValue().actingUserId()).isEqualTo(USER_ID);
        assertThat(command.getValue().paymentId()).isEqualTo(PAYMENT_ID.toString());
    }

    @Test
    void a_non_owner_or_missing_payment_maps_to_404_problem_json() throws Exception {
        doThrow(new PaymentNotFoundException(PAYMENT_ID)).when(submitPaymentProofUseCase).submit(any());

        mockMvc.perform(multipart("/api/v1/billing/payments/" + PAYMENT_ID + "/proof")
                .file(file())
                .with(authenticatedAs(USER_ID)))
            .andExpect(status().isNotFound())
            .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.code", is("PAYMENT_NOT_FOUND")));
    }

    @Test
    void a_rejected_proof_maps_to_400_problem_json() throws Exception {
        doThrow(new PaymentProofRejectedException("bad file")).when(submitPaymentProofUseCase).submit(any());

        mockMvc.perform(multipart("/api/v1/billing/payments/" + PAYMENT_ID + "/proof")
                .file(file())
                .with(authenticatedAs(USER_ID)))
            .andExpect(status().isBadRequest())
            .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.code", is("PAYMENT_PROOF_REJECTED")));
    }

    @Test
    void an_exhausted_upload_budget_maps_to_429_problem_json_with_retry_after() throws Exception {
        doThrow(new BankTransferRateLimitedException(Duration.ofHours(1)))
            .when(submitPaymentProofUseCase).submit(any());

        mockMvc.perform(multipart("/api/v1/billing/payments/" + PAYMENT_ID + "/proof")
                .file(file())
                .with(authenticatedAs(USER_ID)))
            .andExpect(status().isTooManyRequests())
            .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.code", is("BANK_TRANSFER_RATE_LIMITED")));
    }

    @Test
    void a_malformed_payment_id_is_a_400_not_a_500() throws Exception {
        doThrow(new IllegalArgumentException("Invalid PaymentId format"))
            .when(submitPaymentProofUseCase).submit(any());

        mockMvc.perform(multipart("/api/v1/billing/payments/not-a-uuid/proof")
                .file(file())
                .with(authenticatedAs(USER_ID)))
            .andExpect(status().isBadRequest())
            .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.code", is("INVALID_REQUEST")));
    }

    /** #31, US-BILLING-003, design C9. */
    @Test
    void a_valid_read_returns_200_with_status_createdAt_and_updatedAt() throws Exception {
        Instant createdAt = Instant.parse("2026-09-19T10:00:00Z");
        Instant updatedAt = Instant.parse("2026-09-19T12:00:00Z");
        when(getPaymentUseCase.getPayment(eq(PAYMENT_ID.toString()), eq(USER_ID)))
            .thenReturn(new PaymentStatusResult("COMPLETED", createdAt, updatedAt, FulfillmentStatus.EXCEPTION));

        mockMvc.perform(get("/api/v1/billing/payments/" + PAYMENT_ID)
                .with(authenticatedAs(USER_ID)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status", is("COMPLETED")))
            .andExpect(jsonPath("$.createdAt", is("2026-09-19T10:00:00Z")))
            .andExpect(jsonPath("$.updatedAt", is("2026-09-19T12:00:00Z")))
            .andExpect(jsonPath("$.fulfillment", is("EXCEPTION")))
            .andExpect(jsonPath("$.reason").doesNotExist())
            .andExpect(jsonPath("$.resolution").doesNotExist())
            .andExpect(jsonPath("$.resolutionType").doesNotExist())
            .andExpect(jsonPath("$.resolutionNote").doesNotExist());
    }

    /** #559: a payment with no Purchase row serializes {@code fulfillment} as an explicit null. */
    @Test
    void a_payment_without_a_purchase_serializes_fulfillment_as_null() throws Exception {
        when(getPaymentUseCase.getPayment(any(), any()))
            .thenReturn(new PaymentStatusResult("COMPLETED", Instant.now(), Instant.now(), null));

        mockMvc.perform(get("/api/v1/billing/payments/" + PAYMENT_ID)
                .with(authenticatedAs(USER_ID)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", org.hamcrest.Matchers.hasKey("fulfillment")))
            .andExpect(jsonPath("$.fulfillment").value(org.hamcrest.Matchers.nullValue()));
    }

    /** C8: owner comes from the token, never from a query/body parameter — same pattern as the proof upload. */
    @Test
    void the_read_owner_comes_from_the_token() throws Exception {
        when(getPaymentUseCase.getPayment(any(), any()))
            .thenReturn(new PaymentStatusResult("AWAITING_MANUAL_VERIFICATION", Instant.now(), Instant.now(), null));

        mockMvc.perform(get("/api/v1/billing/payments/" + PAYMENT_ID)
                .with(authenticatedAs(USER_ID)))
            .andExpect(status().isOk());

        ArgumentCaptor<UUID> actingUserId = ArgumentCaptor.forClass(UUID.class);
        verify(getPaymentUseCase).getPayment(eq(PAYMENT_ID.toString()), actingUserId.capture());
        assertThat(actingUserId.getValue()).isEqualTo(USER_ID);
    }

    @Test
    void a_non_owner_or_missing_payment_read_maps_to_404_problem_json() throws Exception {
        doThrow(new PaymentNotFoundException(PAYMENT_ID)).when(getPaymentUseCase).getPayment(any(), any());

        mockMvc.perform(get("/api/v1/billing/payments/" + PAYMENT_ID)
                .with(authenticatedAs(USER_ID)))
            .andExpect(status().isNotFound())
            .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.code", is("PAYMENT_NOT_FOUND")));
    }

    // --- #767: GET /me ----------------------------------------------------------------------------

    @Test
    void own_payments_render_the_agreed_fields_and_nothing_else() throws Exception {
        String paymentId = PAYMENT_ID.toString();
        when(listOwnPaymentsUseCase.list(USER_ID, ListOwnPaymentsUseCase.DEFAULT_LIMIT)).thenReturn(List.of(
            new OwnPaymentEntry(
                paymentId, "COMPLETED", Money.of(new BigDecimal("15000.00"), "ARS"), PaymentMethod.MERCADO_PAGO,
                Instant.parse("2026-11-10T12:00:00Z"),
                new OwnPaymentEntry.Item(OwnPaymentEntry.ItemType.PHYSICAL_COURSE, "course-1", null)
            )
        ));

        mockMvc.perform(get("/api/v1/billing/payments/me").with(authenticatedAs(USER_ID)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].paymentId", is(paymentId)))
            .andExpect(jsonPath("$[0].status", is("COMPLETED")))
            .andExpect(jsonPath("$[0].amount", is(15000.00)))
            .andExpect(jsonPath("$[0].currency", is("ARS")))
            .andExpect(jsonPath("$[0].paymentMethod", is("MERCADO_PAGO")))
            .andExpect(jsonPath("$[0].createdAt", is("2026-11-10T12:00:00Z")))
            .andExpect(jsonPath("$[0].item.type", is("PHYSICAL_COURSE")))
            .andExpect(jsonPath("$[0].item.id", is("course-1")))
            .andExpect(jsonPath("$[0].item", org.hamcrest.Matchers.hasKey("name")))
            .andExpect(jsonPath("$[0].item.name").value(org.hamcrest.Matchers.nullValue()))
            .andExpect(jsonPath("$[0].*", org.hamcrest.Matchers.hasSize(7)))
            .andExpect(jsonPath("$[0].item.*", org.hamcrest.Matchers.hasSize(3)));
    }

    @Test
    void own_payments_read_the_user_from_the_token_and_pass_the_requested_limit() throws Exception {
        when(listOwnPaymentsUseCase.list(any(), org.mockito.ArgumentMatchers.anyInt())).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/billing/payments/me").param("limit", "5").with(authenticatedAs(USER_ID)))
            .andExpect(status().isOk())
            .andExpect(content().json("[]"));

        verify(listOwnPaymentsUseCase).list(USER_ID, 5);
    }

    /** The literal {@code /me} route wins over {@code /{paymentId}}: the single-payment read is never reached. */
    @Test
    void the_me_route_is_never_read_as_a_payment_id() throws Exception {
        when(listOwnPaymentsUseCase.list(any(), org.mockito.ArgumentMatchers.anyInt())).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/billing/payments/me").with(authenticatedAs(USER_ID)))
            .andExpect(status().isOk());

        verifyNoInteractions(getPaymentUseCase);
    }

    @Test
    void a_non_numeric_limit_is_a_400_invalid_request() throws Exception {
        mockMvc.perform(get("/api/v1/billing/payments/me").param("limit", "abc").with(authenticatedAs(USER_ID)))
            .andExpect(status().isBadRequest())
            .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.code", is("INVALID_REQUEST")));

        verifyNoInteractions(listOwnPaymentsUseCase);
    }

    @Test
    void an_out_of_range_limit_is_a_400_invalid_request() throws Exception {
        when(listOwnPaymentsUseCase.list(USER_ID, 101)).thenThrow(new InvalidPaymentsLimitException(101, 100));

        mockMvc.perform(get("/api/v1/billing/payments/me").param("limit", "101").with(authenticatedAs(USER_ID)))
            .andExpect(status().isBadRequest())
            .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.code", is("INVALID_REQUEST")))
            .andExpect(jsonPath("$.detail", is("El parámetro limit debe ser un número entre 1 y 100.")));
    }
}
