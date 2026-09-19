package com.menta.billing.infrastructure.web.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.menta.billing.domain.exception.IllegalPaymentStateTransitionException;
import com.menta.billing.domain.exception.PaymentProofRejectedException;
import com.menta.billing.domain.model.ManualVerificationDecision;
import com.menta.billing.domain.model.PaymentId;
import com.menta.billing.domain.model.PaymentStatus;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;

/**
 * Unit coverage for {@link PaymentExceptionHandler} (#31, US-BILLING-003, phases P1/P3a) — mirrors
 * {@code SubscriptionExceptionHandlerTest}'s direct-instantiation pattern.
 */
class PaymentExceptionHandlerTest {

    private final PaymentExceptionHandler handler = new PaymentExceptionHandler();

    @Test
    void maps_illegal_payment_state_transition_to_409() {
        IllegalPaymentStateTransitionException exception = new IllegalPaymentStateTransitionException(
            PaymentId.generate(), new PaymentStatus.Expired(java.time.Instant.now()),
            ManualVerificationDecision.APPROVED
        );

        ResponseEntity<ProblemDetail> response = handler.illegalStateTransition(exception);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(response.getBody().getProperties().get("code"))
            .isEqualTo("ILLEGAL_PAYMENT_STATE_TRANSITION");
    }

    @Test
    void maps_payment_proof_rejected_to_400() {
        PaymentProofRejectedException exception = new PaymentProofRejectedException("bad content");

        ResponseEntity<ProblemDetail> response = handler.paymentProofRejected(exception);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(response.getBody().getProperties().get("code")).isEqualTo("PAYMENT_PROOF_REJECTED");
    }
}
