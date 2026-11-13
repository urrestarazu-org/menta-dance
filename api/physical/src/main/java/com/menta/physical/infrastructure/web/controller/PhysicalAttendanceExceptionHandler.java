package com.menta.physical.infrastructure.web.controller;

import com.menta.physical.domain.exception.InvalidSessionRangeException;
import com.menta.physical.infrastructure.web.ProblemDetails;
import java.time.format.DateTimeParseException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * RFC 9457 Problem Details mapping for Physical's student-facing reads over a UTC instant range: the
 * attendance history (#39, US-PHYSICAL-002, design C7) and the student's own sessions (#356, #729).
 * Neither the range nor {@code studentId} is an existence oracle (C4) — both are decidable client-side
 * — so every violation maps to a plain {@code 400}, never a {@code 500} or a {@code 404}.
 */
@RestControllerAdvice(annotations = PhysicalAttendanceEndpoint.class)
public class PhysicalAttendanceExceptionHandler {

    /** A {@code from} or {@code to} query parameter that is not a valid ISO instant. */
    @ExceptionHandler(DateTimeParseException.class)
    ResponseEntity<ProblemDetail> malformedInstant(DateTimeParseException exception) {
        return ProblemDetails.response(
            HttpStatus.BAD_REQUEST, "Malformed request.", "INVALID_REQUEST"
        );
    }

    /** A required query parameter that is absent, e.g. {@code to}. */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    ResponseEntity<ProblemDetail> missingParameter(MissingServletRequestParameterException exception) {
        return ProblemDetails.response(
            HttpStatus.BAD_REQUEST, "Malformed request.", "INVALID_REQUEST"
        );
    }

    /** An empty, inverted or too wide instant range. */
    @ExceptionHandler(InvalidSessionRangeException.class)
    ResponseEntity<ProblemDetail> invalidRange(InvalidSessionRangeException exception) {
        return ProblemDetails.response(
            HttpStatus.BAD_REQUEST,
            "The range must have from before to and span at most " + exception.getMaxDays() + " days.",
            exception.getErrorCode()
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
