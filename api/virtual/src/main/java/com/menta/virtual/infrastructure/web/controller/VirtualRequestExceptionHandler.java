package com.menta.virtual.infrastructure.web.controller;

import com.menta.virtual.infrastructure.web.ProblemDetails;
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
 * RFC 9457 Problem Details mapping for the ways a virtual request body can fail to decode (#790).
 *
 * <p>Every virtual controller carries one of the marker annotations listed below, and each marker
 * has its own domain-exception advice. Request decoding failures are not domain exceptions and are
 * the same for all of them, so one advice covers every marker instead of repeating the handlers in
 * each. Without it Spring Boot answers a bare {@code 400} ({@code {timestamp, status, error,
 * message, path}}) with no {@code code}. Each failure answers {@code 400 INVALID_REQUEST} with a
 * generic detail that never echoes field messages:</p>
 * <ul>
 *   <li>{@link MethodArgumentNotValidException} — a {@code @Valid} body that violates a
 *       constraint;</li>
 *   <li>{@link HttpMessageNotReadableException} — malformed JSON, a value Jackson cannot decode,
 *       or a missing body.</li>
 * </ul>
 *
 * <p>It runs first ({@link Ordered#HIGHEST_PRECEDENCE}) on purpose: Spring resolves a handler per
 * advice by looking at the exception and then at its cause chain, so a domain advice with an
 * {@link IllegalArgumentException} handler would otherwise win for an unreadable body whose cause
 * is an {@code IllegalArgumentException} (the student and public advices map that to the
 * anti-enumeration {@code 404}).</p>
 *
 * <p>A new controller marker must be added to {@code annotations}; {@code
 * VirtualRequestExceptionHandlerTest} fails for a controller that no marker here covers.</p>
 */
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(annotations = {
    PublicVirtualEndpoint.class,
    VirtualManagementEndpoint.class,
    VirtualStudentEndpoint.class
})
public class VirtualRequestExceptionHandler {

    @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class})
    ResponseEntity<ProblemDetail> invalidBody() {
        return ProblemDetails.response(
            HttpStatus.BAD_REQUEST, "La solicitud contiene datos inválidos.", "INVALID_REQUEST"
        );
    }
}
