package com.menta.billing.infrastructure.web.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.menta.billing.application.dto.CorrectPaymentCommand;
import com.menta.billing.application.dto.PendingVerificationItem;
import com.menta.billing.application.dto.PendingVerificationPage;
import com.menta.billing.application.dto.ResolvePaymentProofCommand;
import com.menta.billing.application.port.in.CorrectPaymentUseCase;
import com.menta.billing.application.port.in.ResolvePaymentProofUseCase;
import com.menta.billing.application.port.out.PaymentProofRepository;
import com.menta.billing.application.port.out.PaymentRepository;
import com.menta.billing.domain.model.ManualVerificationDecision;
import com.menta.billing.domain.model.Money;
import com.menta.billing.domain.model.Payment;
import com.menta.billing.domain.model.PaymentId;
import com.menta.billing.domain.model.PaymentProof;
import com.menta.billing.domain.model.PaymentProofId;
import com.menta.billing.domain.model.PaymentTarget;
import com.menta.billing.infrastructure.proof.ProofAccessTokenSigner;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
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
    private CorrectPaymentUseCase correctPaymentUseCase;
    private PaymentRepository paymentRepository;
    private PaymentProofRepository paymentProofRepository;
    private ProofAccessTokenSigner proofAccessTokenSigner;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        resolvePaymentProofUseCase = mock(ResolvePaymentProofUseCase.class);
        correctPaymentUseCase = mock(CorrectPaymentUseCase.class);
        paymentRepository = mock(PaymentRepository.class);
        paymentProofRepository = mock(PaymentProofRepository.class);
        proofAccessTokenSigner = mock(ProofAccessTokenSigner.class);
        mockMvc = MockMvcBuilders
            .standaloneSetup(new PaymentAdminController(
                resolvePaymentProofUseCase, correctPaymentUseCase, paymentRepository, paymentProofRepository,
                proofAccessTokenSigner
            ))
            .setControllerAdvice(new PaymentExceptionHandler())
            .build();
    }

    private static Authentication authOf(String role) {
        return authOf(role, UUID.randomUUID());
    }

    private static Authentication authOf(String role, UUID userId) {
        return new UsernamePasswordAuthenticationToken(
            userId.toString(), null, List.of(new SimpleGrantedAuthority("ROLE_" + role))
        );
    }

    @Test
    void approve_resolves_the_payment_as_approved() throws Exception {
        UUID adminId = UUID.randomUUID();

        mockMvc.perform(post("/api/v1/admin/billing/payments/{id}/approve", PAYMENT_ID)
            .principal(authOf("ADMIN", adminId)))
            .andExpect(status().isOk());

        verify(resolvePaymentProofUseCase).resolve(new ResolvePaymentProofCommand(
            PAYMENT_ID.toString(), ManualVerificationDecision.APPROVED, null, adminId
        ));
    }

    @Test
    void reject_resolves_the_payment_as_rejected_with_the_given_reason() throws Exception {
        UUID adminId = UUID.randomUUID();

        mockMvc.perform(post("/api/v1/admin/billing/payments/{id}/reject", PAYMENT_ID)
            .principal(authOf("ADMIN", adminId))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"reason\": \"Comprobante ilegible\"}"))
            .andExpect(status().isOk());

        verify(resolvePaymentProofUseCase).resolve(new ResolvePaymentProofCommand(
            PAYMENT_ID.toString(), ManualVerificationDecision.REJECTED, "Comprobante ilegible", adminId
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

    // --- corrections (#33, US-BILLING-005, design D9/C1) ---

    @Test
    void corrections_applies_the_admin_decision() throws Exception {
        UUID adminId = UUID.randomUUID();

        mockMvc.perform(post("/api/v1/admin/billing/payments/{id}/corrections", PAYMENT_ID)
            .principal(authOf("ADMIN", adminId))
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"decision": "APPROVED", "reason": "Transferencia confirmada por el banco", \
                "evidence": "captura-extracto.pdf"}
                """))
            .andExpect(status().isOk());

        verify(correctPaymentUseCase).correct(new CorrectPaymentCommand(
            PAYMENT_ID.toString(), ManualVerificationDecision.APPROVED,
            "Transferencia confirmada por el banco", "captura-extracto.pdf", adminId
        ));
    }

    /** A blank reason is rejected by bean validation before the use case ever runs. */
    @Test
    void corrections_with_a_blank_reason_returns_400_before_any_use_case_call() throws Exception {
        mockMvc.perform(post("/api/v1/admin/billing/payments/{id}/corrections", PAYMENT_ID)
            .principal(authOf("ADMIN"))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"decision\": \"APPROVED\", \"reason\": \"\", \"evidence\": \"captura-extracto.pdf\"}"))
            .andExpect(status().isBadRequest());

        verifyNoInteractions(correctPaymentUseCase);
    }

    /** A blank evidence is rejected by bean validation before the use case ever runs. */
    @Test
    void corrections_with_a_blank_evidence_returns_400_before_any_use_case_call() throws Exception {
        mockMvc.perform(post("/api/v1/admin/billing/payments/{id}/corrections", PAYMENT_ID)
            .principal(authOf("ADMIN"))
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"decision": "APPROVED", "reason": "Transferencia confirmada por el banco", "evidence": ""}
                """))
            .andExpect(status().isBadRequest());

        verifyNoInteractions(correctPaymentUseCase);
    }

    /** Defense-in-depth: a non-admin never reaches the use case, real 403, nothing changes. */
    @Test
    void corrections_from_a_non_admin_returns_403_and_changes_nothing() throws Exception {
        mockMvc.perform(post("/api/v1/admin/billing/payments/{id}/corrections", PAYMENT_ID)
            .principal(authOf("STUDENT"))
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"decision": "APPROVED", "reason": "Transferencia confirmada por el banco", \
                "evidence": "captura-extracto.pdf"}
                """))
            .andExpect(status().isForbidden());

        verifyNoInteractions(correctPaymentUseCase);
    }

    // --- pending-verification list (#33, US-BILLING-005, design C10) ---

    private static PendingVerificationItem pendingItem() {
        return new PendingVerificationItem(
            UUID.randomUUID(), UUID.randomUUID(), "SUBSCRIPTION", "plan-basic", BigDecimal.TEN, "ARS",
            "AWAITING_MANUAL_VERIFICATION", true, Instant.parse("2026-09-01T10:00:00Z")
        );
    }

    @Test
    void list_returns_the_page_from_the_repository_ordered_oldest_first() throws Exception {
        PendingVerificationPage page = new PendingVerificationPage(List.of(pendingItem()), 0, 20, 1, 1);
        when(paymentRepository.findAwaitingManualVerification(0, 20)).thenReturn(page);

        mockMvc.perform(get("/api/v1/admin/billing/payments")
            .param("status", "PENDING")
            .param("substatus", "AWAITING_MANUAL_VERIFICATION")
            .principal(authOf("ADMIN")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items[0].hasProof").value(true))
            .andExpect(jsonPath("$.totalElements").value(1));

        verify(paymentRepository).findAwaitingManualVerification(0, 20);
    }

    @Test
    void list_defaults_to_page_zero_size_twenty() throws Exception {
        when(paymentRepository.findAwaitingManualVerification(eq(0), eq(20)))
            .thenReturn(new PendingVerificationPage(List.of(), 0, 20, 0, 0));

        mockMvc.perform(get("/api/v1/admin/billing/payments")
            .param("status", "PENDING")
            .param("substatus", "AWAITING_MANUAL_VERIFICATION")
            .principal(authOf("ADMIN")))
            .andExpect(status().isOk());

        verify(paymentRepository).findAwaitingManualVerification(0, 20);
    }

    /** Deviations #1: the spec requires an explicit 400, not a clamp to 50. */
    @Test
    void list_with_page_size_above_50_returns_400_and_no_results() throws Exception {
        mockMvc.perform(get("/api/v1/admin/billing/payments")
            .param("status", "PENDING")
            .param("substatus", "AWAITING_MANUAL_VERIFICATION")
            .param("size", "51")
            .principal(authOf("ADMIN")))
            .andExpect(status().isBadRequest());

        verifyNoInteractions(paymentRepository);
    }

    @Test
    void list_from_a_non_admin_returns_403() throws Exception {
        mockMvc.perform(get("/api/v1/admin/billing/payments")
            .param("status", "PENDING")
            .param("substatus", "AWAITING_MANUAL_VERIFICATION")
            .principal(authOf("STUDENT")))
            .andExpect(status().isForbidden());

        verifyNoInteractions(paymentRepository);
    }

    // --- payment detail with signed proof URL (#33, US-BILLING-005, design C5/C6) ---

    private static Payment paymentFixture() {
        return Payment.awaitingManualVerification(
            PAYMENT_ID, UUID.randomUUID(), Money.of(new BigDecimal("15000.00"), "ARS"), "SUB-" + PAYMENT_ID,
            "0000003100000000000000", new PaymentTarget.Virtual("plan-basic"), Instant.parse("2026-09-01T10:00:00Z")
        );
    }

    /** Scenario "Admin views payment detail with a proof link": a proof exists, so a signed URL is returned. */
    @Test
    void detail_includes_a_signed_proof_url_when_a_proof_exists() throws Exception {
        when(paymentRepository.findById(PAYMENT_ID)).thenReturn(Optional.of(paymentFixture()));
        when(paymentProofRepository.findByPaymentId(PAYMENT_ID)).thenReturn(Optional.of(new PaymentProof(
            PaymentProofId.generate(), PAYMENT_ID, PAYMENT_ID + "/proof.png", "comprobante.png", "image/png",
            1024L, Instant.parse("2026-09-01T10:05:00Z")
        )));
        when(proofAccessTokenSigner.sign(eq(PAYMENT_ID), any())).thenReturn("signed-token");

        mockMvc.perform(get("/api/v1/admin/billing/payments/{id}", PAYMENT_ID).principal(authOf("ADMIN")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.paymentId").value(PAYMENT_ID.toString()))
            .andExpect(jsonPath("$.statusType").value("AWAITING_MANUAL_VERIFICATION"))
            .andExpect(jsonPath("$.proofUrl").value(
                "/api/v1/billing/payments/" + PAYMENT_ID + "/proof?token=signed-token"
            ));
    }

    /** Absent when no proof was ever submitted — no token is minted. */
    @Test
    void detail_omits_the_proof_url_when_no_proof_exists() throws Exception {
        when(paymentRepository.findById(PAYMENT_ID)).thenReturn(Optional.of(paymentFixture()));
        when(paymentProofRepository.findByPaymentId(PAYMENT_ID)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/admin/billing/payments/{id}", PAYMENT_ID).principal(authOf("ADMIN")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.proofUrl").doesNotExist());

        verifyNoInteractions(proofAccessTokenSigner);
    }

    @Test
    void detail_for_a_missing_payment_returns_404() throws Exception {
        when(paymentRepository.findById(PAYMENT_ID)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/admin/billing/payments/{id}", PAYMENT_ID).principal(authOf("ADMIN")))
            .andExpect(status().isNotFound());
    }

    /** Scenario "Non-admin is rejected". */
    @Test
    void detail_from_a_non_admin_returns_403() throws Exception {
        mockMvc.perform(get("/api/v1/admin/billing/payments/{id}", PAYMENT_ID).principal(authOf("STUDENT")))
            .andExpect(status().isForbidden());

        verifyNoInteractions(paymentRepository);
    }
}
