package com.menta.billing.infrastructure.web.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.menta.billing.domain.exception.BankTransferRateLimitedException;
import com.menta.billing.domain.exception.BillingDegradedException;
import com.menta.billing.domain.exception.IllegalPaymentStateTransitionException;
import com.menta.billing.domain.exception.PaymentNotFoundException;
import com.menta.billing.domain.exception.PaymentProofRejectedException;
import com.menta.billing.domain.model.ManualVerificationDecision;
import com.menta.billing.domain.model.PaymentId;
import com.menta.billing.domain.model.PaymentStatus;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
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

    /** #31, P3c, design C8: never a 403 — a non-owner and a missing payment map identically. */
    @Test
    void maps_payment_not_found_to_404() {
        PaymentNotFoundException exception = new PaymentNotFoundException(PaymentId.generate());

        ResponseEntity<ProblemDetail> response = handler.paymentNotFound(exception);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(response.getBody().getProperties().get("code")).isEqualTo("PAYMENT_NOT_FOUND");
    }

    /** #31, P3c, design C7: the 3 proof uploads/payment/72h budget is spent. */
    @Test
    void maps_bank_transfer_rate_limited_to_429_with_retry_after() {
        BankTransferRateLimitedException exception = new BankTransferRateLimitedException(Duration.ofMinutes(90));

        ResponseEntity<ProblemDetail> response = handler.bankTransferRateLimited(exception);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(response.getHeaders().getFirst(HttpHeaders.RETRY_AFTER)).isEqualTo("5400");
        assertThat(response.getBody().getProperties().get("code")).isEqualTo("BANK_TRANSFER_RATE_LIMITED");
    }

    /** #31, P3c, design C7: fail closed when Redis is unreachable. */
    @Test
    void maps_billing_degraded_to_503_with_retry_after() {
        BillingDegradedException exception = new BillingDegradedException(new IllegalStateException("down"));

        ResponseEntity<ProblemDetail> response = handler.degraded(exception);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(response.getHeaders().getFirst(HttpHeaders.RETRY_AFTER)).isEqualTo("30");
    }

    @Test
    void maps_illegal_argument_to_400_invalid_request() {
        ResponseEntity<ProblemDetail> response = handler.malformedRequest(new IllegalArgumentException("bad id"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(response.getBody().getProperties().get("code")).isEqualTo("INVALID_REQUEST");
    }
}
