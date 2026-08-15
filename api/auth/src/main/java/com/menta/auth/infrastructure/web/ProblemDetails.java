package com.menta.auth.infrastructure.web;

import java.net.URI;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;

/**
 * RFC 9457 {@link ProblemDetail} factory shared by every auth controller and exception handler.
 *
 * <p>Every problem uses the {@code application/problem+json} media type and a stable
 * {@code https://menta.dance/problems/<code>} type URI derived from the application-specific
 * {@code code}; clients should key off {@code code}, not the human-readable {@code detail}.</p>
 */
public final class ProblemDetails {

    private ProblemDetails() {
    }

    public static ResponseEntity<ProblemDetail> response(HttpStatus status, String detail, String code) {
        return ResponseEntity.status(status)
            .contentType(MediaType.APPLICATION_PROBLEM_JSON)
            .body(body(status, detail, code));
    }

    public static ProblemDetail body(HttpStatus status, String detail, String code) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(status, detail);
        problemDetail.setType(URI.create("https://menta.dance/problems/" + code.toLowerCase(Locale.ROOT)));
        problemDetail.setTitle(status.getReasonPhrase());
        problemDetail.setProperty("code", code);
        return problemDetail;
    }
}
