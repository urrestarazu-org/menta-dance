package com.menta.billing.infrastructure.web.controller;

import com.menta.billing.domain.exception.PaymentPreferenceUnavailableException;
import com.menta.billing.domain.exception.PhysicalCapacityUnavailableException;
import com.menta.billing.domain.exception.PhysicalCourseQuoteExpiredException;
import com.menta.billing.infrastructure.web.ProblemDetails;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * RFC 9457 Problem Details mapping for the physical purchase checkout
 * endpoint (#41, US-PHYSICAL-004, design A6/A7).
 *
 * <p>{@link PhysicalCourseQuoteExpiredException} (410) and {@link
 * PhysicalCapacityUnavailableException} (409) deliberately carry distinct
 * codes on this same route — validity is checked before availability
 * (design A7), so the caller is told the actionable thing first (request a
 * new quote) rather than a capacity reading taken from a quote that no
 * longer applies.</p>
 */
@RestControllerAdvice(annotations = PhysicalPurchaseEndpoint.class)
public class PhysicalPurchaseExceptionHandler {

    /** A7: a time-limited artifact whose remedy is a fresh one — same precedent as {@code PasswordResetTokenExpiredException}. */
    @ExceptionHandler(PhysicalCourseQuoteExpiredException.class)
    ResponseEntity<ProblemDetail> quoteExpired(PhysicalCourseQuoteExpiredException exception) {
        return ProblemDetails.response(
            HttpStatus.GONE, "La cotización expiró o no existe. Solicitá una nueva.", exception.getErrorCode()
        );
    }

    /** A6/D5: best-effort rejection, not a capacity guarantee — see the exception's own javadoc. */
    @ExceptionHandler(PhysicalCapacityUnavailableException.class)
    ResponseEntity<ProblemDetail> capacityUnavailable(PhysicalCapacityUnavailableException exception) {
        return ProblemDetails.response(
            HttpStatus.CONFLICT,
            "No hay cupo disponible para los horarios elegibles de esta cotización.", exception.getErrorCode()
        );
    }

    /** The provider could not open a checkout — nothing was written and nothing was charged. */
    @ExceptionHandler(PaymentPreferenceUnavailableException.class)
    ResponseEntity<ProblemDetail> preferenceUnavailable(PaymentPreferenceUnavailableException exception) {
        return ProblemDetails.response(
            HttpStatus.SERVICE_UNAVAILABLE,
            "No pudimos iniciar el pago en este momento. Intentá de nuevo en unos minutos.",
            exception.getErrorCode()
        );
    }

    /** A malformed body field — never a 500. */
    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<ProblemDetail> malformedRequest(IllegalArgumentException exception) {
        return ProblemDetails.response(HttpStatus.BAD_REQUEST, "La solicitud es inválida.", "INVALID_REQUEST");
    }

    /**
     * Maps bean-validation failures to a generic RFC 9457 problem instead of
     * falling through to Spring's default handler, which would echo raw
     * field messages back to the client.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ProblemDetail> invalidRequestBody(MethodArgumentNotValidException exception) {
        return ProblemDetails.response(
            HttpStatus.BAD_REQUEST, "La solicitud contiene datos inválidos.", "INVALID_REQUEST"
        );
    }
}
