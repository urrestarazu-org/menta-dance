package com.menta.auth.infrastructure.web.controller;

import com.menta.auth.domain.exception.ActivationRateLimitedException;
import com.menta.auth.domain.exception.ActivationTokenInvalidException;
import com.menta.auth.domain.exception.AuthDegradedException;
import com.menta.auth.domain.exception.DuplicateRegistrationException;
import com.menta.auth.infrastructure.web.ProblemDetails;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * RFC 9457 Problem Details mapping for the public account-activation endpoints.
 *
 * <p>RFC 9457 standardizes machine-readable HTTP error responses. Every problem emitted here uses
 * the {@code application/problem+json} media type and contains the standard {@code type},
 * {@code title}, {@code status}, and {@code detail} members. The additional {@code code} member is
 * Menta's stable, application-specific error identifier; clients should use it instead of parsing
 * human-readable text.</p>
 *
 * <p>The handler deliberately keeps details generic for public activation flows. A detailed error
 * could reveal whether an account, token, or pending activation exists, enabling enumeration. Retry
 * responses additionally use the standard {@code Retry-After} header so clients know when another
 * request may be attempted.</p>
 */
@RestControllerAdvice(annotations = PublicActivationEndpoint.class)
public class ActivationExceptionHandler {

    private static final String RETRY_AFTER_DEGRADED_SECONDS = "30";

    @ExceptionHandler(DuplicateRegistrationException.class)
    ResponseEntity<Void> duplicateRegistration(DuplicateRegistrationException exception) {
        return ResponseEntity.accepted().build();
    }

    @ExceptionHandler(ActivationTokenInvalidException.class)
    ResponseEntity<ProblemDetail> invalidToken(ActivationTokenInvalidException exception) {
        return ProblemDetails.response(
            HttpStatus.BAD_REQUEST, "El token de activación es inválido o expiró.", exception.getErrorCode()
        );
    }

    @ExceptionHandler(ActivationRateLimitedException.class)
    ResponseEntity<ProblemDetail> rateLimited(ActivationRateLimitedException exception) {
        long seconds = Math.max(1, exception.getRetryAfter().toSeconds());
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
            .header(HttpHeaders.RETRY_AFTER, Long.toString(seconds))
            .contentType(MediaType.APPLICATION_PROBLEM_JSON)
            .body(ProblemDetails.body(
                HttpStatus.TOO_MANY_REQUESTS,
                "Demasiados intentos de activación; reintentá más tarde.",
                exception.getErrorCode()
            ));
    }

    @ExceptionHandler(AuthDegradedException.class)
    ResponseEntity<ProblemDetail> degraded(AuthDegradedException exception) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
            .header(HttpHeaders.RETRY_AFTER, RETRY_AFTER_DEGRADED_SECONDS)
            .contentType(MediaType.APPLICATION_PROBLEM_JSON)
            .body(ProblemDetails.body(
                HttpStatus.SERVICE_UNAVAILABLE,
                "La autenticación no está disponible temporalmente.",
                exception.getErrorCode()
            ));
    }

    /**
     * Maps bean-validation failures (e.g. malformed email) to a generic RFC 9457 problem instead
     * of falling through to Spring's default error handler, which — with
     * {@code server.error.include-binding-errors: always} — would echo the raw field messages
     * back to the client.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ProblemDetail> invalidRequestBody(MethodArgumentNotValidException exception) {
        return ProblemDetails.response(
            HttpStatus.BAD_REQUEST, "La solicitud contiene datos inválidos.", "INVALID_REQUEST"
        );
    }
}
