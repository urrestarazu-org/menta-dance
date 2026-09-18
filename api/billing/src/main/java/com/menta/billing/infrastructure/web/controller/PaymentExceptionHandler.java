package com.menta.billing.infrastructure.web.controller;

import com.menta.billing.domain.exception.IllegalPaymentStateTransitionException;
import com.menta.billing.infrastructure.web.ProblemDetails;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * RFC 9457 Problem Details mapping for bank-transfer payment endpoints (#31, US-BILLING-003).
 * Sibling of {@link SubscriptionExceptionHandler}. This phase (P1) maps only {@link
 * IllegalPaymentStateTransitionException}; P3a adds {@code 400} for a rejected proof.
 */
@RestControllerAdvice(annotations = PaymentEndpoint.class)
public class PaymentExceptionHandler {

    /**
     * A second admin decision, or a stale sweep race, landing on a payment that is no longer
     * {@code AwaitingManualVerification} (C4). Never resurrects or re-terminates a subscription.
     */
    @ExceptionHandler(IllegalPaymentStateTransitionException.class)
    ResponseEntity<ProblemDetail> illegalStateTransition(IllegalPaymentStateTransitionException exception) {
        return ProblemDetails.response(
            HttpStatus.CONFLICT,
            "El pago ya no está a la espera de verificación manual.",
            exception.getErrorCode()
        );
    }
}
