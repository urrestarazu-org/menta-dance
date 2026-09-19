package com.menta.billing.infrastructure.web.controller;

import com.menta.billing.domain.exception.BankTransferRateLimitedException;
import com.menta.billing.domain.exception.BillingDegradedException;
import com.menta.billing.domain.exception.IllegalPaymentStateTransitionException;
import com.menta.billing.domain.exception.PaymentNotFoundException;
import com.menta.billing.domain.exception.PaymentProofRejectedException;
import com.menta.billing.infrastructure.web.ProblemDetails;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * RFC 9457 Problem Details mapping for bank-transfer payment endpoints (#31, US-BILLING-003).
 * Sibling of {@link SubscriptionExceptionHandler}. P1 mapped {@link
 * IllegalPaymentStateTransitionException}; P3a added {@code 400} for a rejected proof; P3c adds
 * {@code 404}/{@code 429}/{@code 503}/{@code 400} for the now-reachable submit endpoint.
 */
@RestControllerAdvice(annotations = PaymentEndpoint.class)
public class PaymentExceptionHandler {

    private static final String RETRY_AFTER_DEGRADED_SECONDS = "30";

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

    /**
     * {@link com.menta.billing.domain.service.PaymentProofContentValidator} rejected the uploaded
     * proof — unsupported type, oversized, empty, or the sniffed magic bytes disagree with the
     * declared type (design C11).
     */
    @ExceptionHandler(PaymentProofRejectedException.class)
    ResponseEntity<ProblemDetail> paymentProofRejected(PaymentProofRejectedException exception) {
        return ProblemDetails.response(
            HttpStatus.BAD_REQUEST,
            "El comprobante no cumple los requisitos: debe ser PNG, JPG/JPEG o PDF de hasta 5MB.",
            exception.getErrorCode()
        );
    }

    /**
     * C8: a payment that does not exist and a payment that exists but does not belong to the
     * caller (or is no longer {@code AwaitingManualVerification}) map to the exact same {@code
     * 404} — never a {@code 403}, so the response cannot be used to probe which is true. Same
     * anti-enumeration discipline {@link SubscriptionExceptionHandler} documents for {@code
     * SubscriptionNotFoundException}.
     */
    @ExceptionHandler(PaymentNotFoundException.class)
    ResponseEntity<ProblemDetail> paymentNotFound(PaymentNotFoundException exception) {
        return ProblemDetails.response(
            HttpStatus.NOT_FOUND, "No se encontró el pago indicado.", exception.getErrorCode()
        );
    }

    /** Design C7: the 3 proof uploads/payment/72h budget is spent. */
    @ExceptionHandler(BankTransferRateLimitedException.class)
    ResponseEntity<ProblemDetail> bankTransferRateLimited(BankTransferRateLimitedException exception) {
        long seconds = Math.max(1, exception.getRetryAfter().toSeconds());
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
            .header(HttpHeaders.RETRY_AFTER, Long.toString(seconds))
            .contentType(MediaType.APPLICATION_PROBLEM_JSON)
            .body(ProblemDetails.body(
                HttpStatus.TOO_MANY_REQUESTS,
                "Demasiadas solicitudes de subida de comprobante; reintentá más tarde.",
                exception.getErrorCode()
            ));
    }

    /**
     * Design C7: the proof-upload rate limiter could not reach Redis. Fail closed — never let an
     * unbounded number of uploads through while the budget is unreachable.
     */
    @ExceptionHandler(BillingDegradedException.class)
    ResponseEntity<ProblemDetail> degraded(BillingDegradedException exception) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
            .header(HttpHeaders.RETRY_AFTER, RETRY_AFTER_DEGRADED_SECONDS)
            .contentType(MediaType.APPLICATION_PROBLEM_JSON)
            .body(ProblemDetails.body(
                HttpStatus.SERVICE_UNAVAILABLE,
                "El servicio de facturación no está disponible temporalmente.",
                exception.getErrorCode()
            ));
    }

    /** A malformed path/body field — for instance a {@code paymentId} that is not a UUID. Never a 500. */
    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<ProblemDetail> malformedRequest(IllegalArgumentException exception) {
        return ProblemDetails.response(HttpStatus.BAD_REQUEST, "La solicitud es inválida.", "INVALID_REQUEST");
    }
}
