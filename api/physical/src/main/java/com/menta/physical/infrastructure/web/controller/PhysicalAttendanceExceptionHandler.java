package com.menta.physical.infrastructure.web.controller;

import com.menta.physical.infrastructure.web.ProblemDetails;
import java.time.format.DateTimeParseException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * RFC 9457 Problem Details mapping for Physical's attendance-history endpoints (#39,
 * US-PHYSICAL-002, design C7). Both {@code month} and {@code studentId} are decidable
 * client-side — neither is an existence oracle (C4) — so both map to a plain {@code 400}, never
 * a {@code 500} or a {@code 404}.
 */
@RestControllerAdvice(annotations = PhysicalAttendanceEndpoint.class)
public class PhysicalAttendanceExceptionHandler {

    /** A malformed {@code month} query parameter, e.g. not {@code YYYY-MM}. */
    @ExceptionHandler(DateTimeParseException.class)
    ResponseEntity<ProblemDetail> malformedMonth(DateTimeParseException exception) {
        return ProblemDetails.response(
            HttpStatus.BAD_REQUEST, "The month parameter is malformed; expected YYYY-MM.", "INVALID_REQUEST"
        );
    }

    /** A malformed {@code studentId} path variable — {@code UUID.fromString} rejects it. */
    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<ProblemDetail> malformedId(IllegalArgumentException exception) {
        return ProblemDetails.response(
            HttpStatus.BAD_REQUEST, "Malformed request.", "INVALID_REQUEST"
        );
    }
}
