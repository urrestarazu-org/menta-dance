package com.menta.billing.infrastructure.web.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.menta.billing.application.port.out.PaymentProofRepository;
import com.menta.billing.application.port.out.PaymentProofStoragePort;
import com.menta.billing.domain.exception.InvalidProofAccessTokenException;
import com.menta.billing.domain.model.PaymentId;
import com.menta.billing.domain.model.PaymentProof;
import com.menta.billing.domain.model.PaymentProofId;
import com.menta.billing.infrastructure.proof.ProofAccessTokenVerifier;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * MockMvc coverage for {@code GET /api/v1/billing/payments/{paymentId}/proof} (#33,
 * US-BILLING-005, design D1/C5/C6, spec "Token-authenticated proof file serving"). Standalone
 * setup — the real {@code SecurityConfig} matcher is exercised separately by {@code
 * SecurityConfigTest} and {@code PaymentProofIntegrationTest}.
 */
class PaymentProofControllerTest {

    private static final PaymentId PAYMENT_ID = PaymentId.generate();

    private ProofAccessTokenVerifier tokenVerifier;
    private PaymentProofRepository proofRepository;
    private PaymentProofStoragePort storagePort;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        tokenVerifier = mock(ProofAccessTokenVerifier.class);
        proofRepository = mock(PaymentProofRepository.class);
        storagePort = mock(PaymentProofStoragePort.class);
        mockMvc = MockMvcBuilders
            .standaloneSetup(new PaymentProofController(tokenVerifier, proofRepository, storagePort))
            .setControllerAdvice(new PaymentExceptionHandler())
            .build();
    }

    private static PaymentProof proofFor(PaymentId paymentId) {
        return new PaymentProof(
            PaymentProofId.generate(), paymentId, paymentId.getValue() + "/proof.png", "comprobante.png",
            "image/png", 1024L, Instant.parse("2026-09-01T10:00:00Z")
        );
    }

    @Test
    void a_valid_token_serves_the_file_with_inline_disposition_and_no_store() throws Exception {
        when(tokenVerifier.verify(eq("valid-token"), any())).thenReturn(PAYMENT_ID);
        PaymentProof proof = proofFor(PAYMENT_ID);
        when(proofRepository.findByPaymentId(PAYMENT_ID)).thenReturn(Optional.of(proof));
        when(storagePort.read(proof.getStorageKey())).thenReturn(new byte[] {1, 2, 3});

        mockMvc.perform(get("/api/v1/billing/payments/{id}/proof", PAYMENT_ID).param("token", "valid-token"))
            .andExpect(status().isOk())
            .andExpect(header().string("Content-Disposition", "inline"))
            .andExpect(header().string("Cache-Control", "no-store"));
    }

    /** Deviations #2: absent token is 401, never folded into the verifier's uniform 403. */
    @Test
    void an_absent_token_returns_401_before_the_verifier_is_ever_called() throws Exception {
        mockMvc.perform(get("/api/v1/billing/payments/{id}/proof", PAYMENT_ID))
            .andExpect(status().isUnauthorized());

        org.mockito.Mockito.verifyNoInteractions(tokenVerifier);
    }

    @Test
    void a_blank_token_returns_401_before_the_verifier_is_ever_called() throws Exception {
        mockMvc.perform(get("/api/v1/billing/payments/{id}/proof", PAYMENT_ID).param("token", ""))
            .andExpect(status().isUnauthorized());

        org.mockito.Mockito.verifyNoInteractions(tokenVerifier);
    }

    @Test
    void a_tampered_token_returns_403() throws Exception {
        when(tokenVerifier.verify(eq("tampered"), any())).thenThrow(new InvalidProofAccessTokenException());

        mockMvc.perform(get("/api/v1/billing/payments/{id}/proof", PAYMENT_ID).param("token", "tampered"))
            .andExpect(status().isForbidden());
    }

    @Test
    void an_expired_token_returns_403() throws Exception {
        when(tokenVerifier.verify(eq("expired"), any())).thenThrow(new InvalidProofAccessTokenException());

        mockMvc.perform(get("/api/v1/billing/payments/{id}/proof", PAYMENT_ID).param("token", "expired"))
            .andExpect(status().isForbidden());
    }

    /** A token valid for a DIFFERENT payment must not serve THIS payment's file. */
    @Test
    void a_token_valid_for_a_different_payment_returns_403_and_never_reads_storage() throws Exception {
        PaymentId otherPaymentId = PaymentId.generate();
        when(tokenVerifier.verify(eq("cross-payment"), any())).thenReturn(otherPaymentId);

        mockMvc.perform(get("/api/v1/billing/payments/{id}/proof", PAYMENT_ID).param("token", "cross-payment"))
            .andExpect(status().isForbidden());

        org.mockito.Mockito.verifyNoInteractions(storagePort);
    }

    /** No proof record for this payment — same 403, never a 404 oracle (design C5). */
    @Test
    void a_valid_token_for_a_payment_with_no_proof_returns_403() throws Exception {
        when(tokenVerifier.verify(eq("valid-token"), any())).thenReturn(PAYMENT_ID);
        when(proofRepository.findByPaymentId(PAYMENT_ID)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/billing/payments/{id}/proof", PAYMENT_ID).param("token", "valid-token"))
            .andExpect(status().isForbidden());
    }
}
