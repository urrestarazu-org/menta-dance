package com.menta.billing.infrastructure.web.controller;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.menta.billing.application.dto.ResolvePaymentProofCommand;
import com.menta.billing.application.port.in.ResolvePaymentProofUseCase;
import com.menta.billing.domain.model.ManualVerificationDecision;
import com.menta.billing.domain.model.PaymentId;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * MockMvc coverage for {@code POST /api/v1/admin/billing/payments/{id}/approve} and {@code
 * /{id}/reject} (#31, US-BILLING-003, design D1/C1/C8).
 *
 * <p>{@code SecurityConfig}'s generic {@code /api/v1/admin/**} rule already restricts this path
 * to {@code ROLE_ADMIN}; {@link PaymentAdminController#isAdmin(Authentication)} is a second,
 * independent defense-in-depth check exercised directly here through standalone {@code MockMvc}
 * (which does not run that filter chain), the same shape {@code SubscriptionAdminController} uses.</p>
 */
class PaymentAdminControllerTest {

    private static final PaymentId PAYMENT_ID = PaymentId.generate();

    private ResolvePaymentProofUseCase resolvePaymentProofUseCase;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        resolvePaymentProofUseCase = mock(ResolvePaymentProofUseCase.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new PaymentAdminController(resolvePaymentProofUseCase))
            .setControllerAdvice(new PaymentExceptionHandler())
            .build();
    }

    private static Authentication authOf(String role) {
        return new UsernamePasswordAuthenticationToken(
            UUID.randomUUID().toString(), null, List.of(new SimpleGrantedAuthority("ROLE_" + role))
        );
    }

    @Test
    void approve_resolves_the_payment_as_approved() throws Exception {
        mockMvc.perform(post("/api/v1/admin/billing/payments/{id}/approve", PAYMENT_ID)
            .principal(authOf("ADMIN")))
            .andExpect(status().isOk());

        verify(resolvePaymentProofUseCase).resolve(
            new ResolvePaymentProofCommand(PAYMENT_ID.toString(), ManualVerificationDecision.APPROVED, null)
        );
    }

    @Test
    void reject_resolves_the_payment_as_rejected_with_the_given_reason() throws Exception {
        mockMvc.perform(post("/api/v1/admin/billing/payments/{id}/reject", PAYMENT_ID)
            .principal(authOf("ADMIN"))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"reason\": \"Comprobante ilegible\"}"))
            .andExpect(status().isOk());

        verify(resolvePaymentProofUseCase).resolve(new ResolvePaymentProofCommand(
            PAYMENT_ID.toString(), ManualVerificationDecision.REJECTED, "Comprobante ilegible"
        ));
    }

    /** A blank reason is rejected by bean validation before the use case ever runs. */
    @Test
    void reject_with_a_blank_reason_returns_400_before_any_use_case_call() throws Exception {
        mockMvc.perform(post("/api/v1/admin/billing/payments/{id}/reject", PAYMENT_ID)
            .principal(authOf("ADMIN"))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"reason\": \"\"}"))
            .andExpect(status().isBadRequest());

        verifyNoInteractions(resolvePaymentProofUseCase);
    }

    /** Defense-in-depth: a non-admin never reaches the use case, real 403, nothing changes. */
    @Test
    void approve_from_a_non_admin_returns_403_and_changes_nothing() throws Exception {
        mockMvc.perform(post("/api/v1/admin/billing/payments/{id}/approve", PAYMENT_ID)
            .principal(authOf("STUDENT")))
            .andExpect(status().isForbidden());

        verifyNoInteractions(resolvePaymentProofUseCase);
    }

    @Test
    void reject_from_a_non_admin_returns_403_and_changes_nothing() throws Exception {
        mockMvc.perform(post("/api/v1/admin/billing/payments/{id}/reject", PAYMENT_ID)
            .principal(authOf("STUDENT"))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"reason\": \"Comprobante ilegible\"}"))
            .andExpect(status().isForbidden());

        verifyNoInteractions(resolvePaymentProofUseCase);
    }
}
