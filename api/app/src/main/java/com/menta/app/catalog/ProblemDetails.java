package com.menta.app.catalog;

import java.net.URI;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;

/**
 * RFC 9457 {@link ProblemDetail} factory for the catalog controller and
 * exception handler. Mirrors auth's and billing's own {@code
 * ProblemDetails} byte-for-byte — module isolation forbids reusing either,
 * and the helper is small enough that duplicating it costs less than a
 * cross-module dependency would.
 */
public final class ProblemDetails {

    private ProblemDetails() {
    }

    /**
     * Wraps a problem body in an HTTP response with the matching status.
     *
     * <p>The content type is forced to {@code application/problem+json} so clients can recognise
     * the RFC 9457 payload regardless of the request's {@code Accept} header.</p>
     *
     * @param status the HTTP status of the response, also reflected in the body
     * @param detail the human-readable explanation of this occurrence of the problem
     * @param code the stable machine-readable error code, also used to build the problem type URI
     * @return the response entity carrying the problem body
     */
    public static ResponseEntity<ProblemDetail> response(HttpStatus status, String detail, String code) {
        return ResponseEntity.status(status)
            .contentType(MediaType.APPLICATION_PROBLEM_JSON)
            .body(body(status, detail, code));
    }

    /**
     * Builds the RFC 9457 problem body without wrapping it in a response.
     *
     * <p>The type URI is {@code https://menta.dance/problems/} followed by the lower-cased code
     * (locale-independent), the title is the status reason phrase, and the raw code is exposed as
     * the {@code code} extension property. Use this overload where a {@link ProblemDetail} must be
     * returned directly, such as from an exception handler.</p>
     *
     * @param status the HTTP status of the problem
     * @param detail the human-readable explanation of this occurrence of the problem
     * @param code the stable machine-readable error code
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
