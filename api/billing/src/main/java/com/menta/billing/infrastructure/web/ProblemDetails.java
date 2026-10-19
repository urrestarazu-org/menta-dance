package com.menta.billing.infrastructure.web;

import java.net.URI;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;

/**
 * RFC 9457 {@link ProblemDetail} factory for billing's controllers and
 * exception handlers. Mirrors {@code auth}'s own {@code ProblemDetails}
 * byte-for-byte — module isolation forbids reusing auth's package-private
 * one, and the helper is small enough that duplicating it costs less than a
 * cross-module dependency would.
 */
public final class ProblemDetails {

    private ProblemDetails() {
    }

    /**
     * Builds a complete RFC 9457 response entity with the {@code application/problem+json} content
     * type.
     *
     * <p>The body is the one produced by {@link #body}.
     */
    public static ResponseEntity<ProblemDetail> response(HttpStatus status, String detail, String code) {
        return ResponseEntity.status(status)
            .contentType(MediaType.APPLICATION_PROBLEM_JSON)
            .body(body(status, detail, code));
    }

    /**
     * Builds the problem body for a status and a stable machine-readable code.
     *
     * <p>The {@code type} URI is {@code https://menta.dance/problems/} plus the lower-cased code,
     * the title is the status reason phrase, and the code is repeated in the {@code code} property
     * for clients.
     */
    public static ProblemDetail body(HttpStatus status, String detail, String code) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(status, detail);
        problemDetail.setType(URI.create("https://menta.dance/problems/" + code.toLowerCase(Locale.ROOT)));
        problemDetail.setTitle(status.getReasonPhrase());
        problemDetail.setProperty("code", code);
        return problemDetail;
    }
}
