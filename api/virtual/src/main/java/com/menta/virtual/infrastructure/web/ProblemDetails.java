package com.menta.virtual.infrastructure.web;

import java.net.URI;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;

/**
 * RFC 9457 {@link ProblemDetail} factory for Virtual's controllers and
 * exception handlers. Mirrors {@code physical}'s own {@code ProblemDetails}
 * byte-for-byte — module isolation forbids reusing another module's
 * package-private one, and the helper is small enough that duplicating it
 * costs less than a cross-module dependency would.
 */
public final class ProblemDetails {

    private ProblemDetails() {
    }

    /** Builds a {@code application/problem+json} response with the given status, detail and error code. */
    public static ResponseEntity<ProblemDetail> response(HttpStatus status, String detail, String code) {
        return ResponseEntity.status(status)
            .contentType(MediaType.APPLICATION_PROBLEM_JSON)
            .body(body(status, detail, code));
    }

    /**
     * Builds the problem body: type {@code https://menta.dance/problems/} plus the lower-cased
     * code, the status's reason phrase as title and the code as the {@code code} property.
     */
    public static ProblemDetail body(HttpStatus status, String detail, String code) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(status, detail);
        problemDetail.setType(URI.create("https://menta.dance/problems/" + code.toLowerCase(Locale.ROOT)));
        problemDetail.setTitle(status.getReasonPhrase());
        problemDetail.setProperty("code", code);
        return problemDetail;
    }
}
