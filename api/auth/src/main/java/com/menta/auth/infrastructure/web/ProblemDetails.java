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

    /**
     * Builds the HTTP response that carries the problem as {@code application/problem+json}.
     *
     * @param status the HTTP status of the response and of the problem
     * @param detail the human-readable explanation, shown to the client
     * @param code the stable application-specific error code
     * @return a response with the given status whose body comes from {@link #body}
     */
    public static ResponseEntity<ProblemDetail> response(HttpStatus status, String detail, String code) {
        return ResponseEntity.status(status)
            .contentType(MediaType.APPLICATION_PROBLEM_JSON)
            .body(body(status, detail, code));
    }

    /**
     * Builds the problem body without wrapping it in a response.
     *
     * <p>The title is the reason phrase of the status, the type is derived from the lowercase {@code code}, and
     * the code is also exposed as the {@code code} property.
     *
     * @param status the HTTP status of the problem
     * @param detail the human-readable explanation, shown to the client
     * @param code the stable application-specific error code
     * @return the populated problem detail
     */
    public static ProblemDetail body(HttpStatus status, String detail, String code) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(status, detail);
        problemDetail.setType(URI.create("https://menta.dance/problems/" + code.toLowerCase(Locale.ROOT)));
        problemDetail.setTitle(status.getReasonPhrase());
        problemDetail.setProperty("code", code);
        return problemDetail;
    }
}
