package com.menta.billing.infrastructure.web.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.menta.billing.application.dto.SubmitPaymentProofCommand;
import com.menta.billing.application.port.in.SubmitPaymentProofUseCase;
import com.menta.billing.domain.exception.BankTransferRateLimitedException;
import com.menta.billing.domain.exception.PaymentNotFoundException;
import com.menta.billing.domain.exception.PaymentProofRejectedException;
import com.menta.billing.domain.model.PaymentId;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
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
    private PaymentController controller;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        submitPaymentProofUseCase = mock(SubmitPaymentProofUseCase.class);
        controller = new PaymentController(submitPaymentProofUseCase);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
            .setControllerAdvice(new PaymentExceptionHandler())
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
}
