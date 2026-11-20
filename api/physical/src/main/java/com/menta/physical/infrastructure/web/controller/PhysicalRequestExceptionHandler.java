package com.menta.physical.infrastructure.web.controller;

import com.menta.physical.infrastructure.web.ProblemDetails;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * RFC 9457 Problem Details mapping for the ways a physical request body can fail to decode (#792).
 *
 * <p>Every physical controller that reads a body carries one of the marker annotations listed below, and each marker
 * has its own domain-exception advice. Request decoding failures are not domain exceptions and are the same for all
 * of them, so one advice covers every marker instead of repeating the handlers in each. Without it Spring Boot
 * answers a bare {@code 400} ({@code {timestamp, status, error, message, path}}) with no {@code code}. Each failure
 * answers {@code 400 INVALID_REQUEST} with a generic detail that never echoes field messages:</p>
 * <ul>
 *   <li>{@link MethodArgumentNotValidException} — a {@code @Valid} body that violates a constraint;</li>
 *   <li>{@link HttpMessageNotReadableException} — malformed JSON, a value Jackson cannot decode (an unknown
 *       {@code level} or {@code status}, a malformed date), or a missing body.</li>
 * </ul>
 *
 * <p>It runs first ({@link Ordered#HIGHEST_PRECEDENCE}) on purpose: Spring resolves a handler per advice by looking
 * at the exception and then at its cause chain, so a domain advice would otherwise win for an unreadable body whose
 * cause it handles. The three domain advices map {@link IllegalArgumentException} (a malformed id) to
 * {@code "Malformed request."}, and {@link PhysicalCourseExceptionHandler} also maps the
 * {@link java.time.format.DateTimeParseException} behind a malformed date. No physical controller declares a local
 * {@code @ExceptionHandler}, which would win over every advice.</p>
 *
 * <p>{@link PhysicalAttendanceEndpoint} is not listed: its controllers only serve {@code GET} routes. A new
 * controller marker that reads a body must be added to {@code annotations}; {@code
 * PhysicalRequestExceptionHandlerTest} fails for a controller that reads a body and no marker here covers.</p>
 */
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(annotations = {
    PhysicalManagementEndpoint.class,
    PhysicalDeviceEndpoint.class,
    PhysicalCheckInEndpoint.class
})
public class PhysicalRequestExceptionHandler {

    static final String INVALID_REQUEST_DETAIL = "La solicitud contiene datos inválidos.";
    static final String INVALID_REQUEST = "INVALID_REQUEST";

    @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class})
    ResponseEntity<ProblemDetail> invalidBody() {
        return invalidRequest();
    }

    /** The one answer every decoding failure gets. */
    static ResponseEntity<ProblemDetail> invalidRequest() {
        return ProblemDetails.response(HttpStatus.BAD_REQUEST, INVALID_REQUEST_DETAIL, INVALID_REQUEST);
    }
}
