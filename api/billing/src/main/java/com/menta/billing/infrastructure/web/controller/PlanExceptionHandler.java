package com.menta.billing.infrastructure.web.controller;

import com.menta.billing.domain.exception.BillingDegradedException;
import com.menta.billing.domain.exception.PlanNotFoundException;
import com.menta.billing.domain.exception.PlanRateLimitedException;
import com.menta.billing.infrastructure.web.ProblemDetails;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * RFC 9457 Problem Details mapping for the public plans endpoints.
 *
 * <p>Unlike auth's activation flow, a plan id is not a secret and does not
 * enumerate a person's account — so {@code PlanNotFoundException} freely
 * returns 404 with a stable code. It still does not distinguish "never
 * existed" from "exists but inactive": that distinction has no legitimate
 * use for a public catalog client and only helps someone probing plan ids.</p>
 */
@RestControllerAdvice(annotations = PublicBillingEndpoint.class)
public class PlanExceptionHandler {

    private static final String RETRY_AFTER_DEGRADED_SECONDS = "30";

    @ExceptionHandler(PlanNotFoundException.class)
    ResponseEntity<ProblemDetail> notFound(PlanNotFoundException exception) {
        return ProblemDetails.response(
            HttpStatus.NOT_FOUND, "Plan no encontrado o no disponible.", exception.getErrorCode()
        );
    }

    @ExceptionHandler(PlanRateLimitedException.class)
    ResponseEntity<ProblemDetail> rateLimited(PlanRateLimitedException exception) {
        long seconds = Math.max(1, exception.getRetryAfter().toSeconds());
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
            .header(HttpHeaders.RETRY_AFTER, Long.toString(seconds))
            .contentType(MediaType.APPLICATION_PROBLEM_JSON)
            .body(ProblemDetails.body(
                HttpStatus.TOO_MANY_REQUESTS,
                "Demasiadas solicitudes; reintentá más tarde.",
                exception.getErrorCode()
            ));
    }

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
}
