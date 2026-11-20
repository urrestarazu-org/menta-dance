package com.menta.auth.infrastructure.web.controller;

import com.menta.auth.infrastructure.web.ProblemDetails;
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
 * RFC 9457 Problem Details mapping for the ways the body of a public auth request can fail to decode (#791).
 *
 * <p>The public registration, activation and password-reset controllers each carry a marker annotation with its own
 * domain-exception advice. Request decoding failures are not domain exceptions and are the same for all of them, so
 * one advice covers every marker instead of repeating the handlers in each. Without it Spring Boot answers a bare
 * {@code 400} ({@code {timestamp, status, error, message, path}}) with no {@code code}. Each failure answers
 * {@code 400 INVALID_REQUEST} with a generic detail that never echoes field messages:</p>
 * <ul>
 *   <li>{@link MethodArgumentNotValidException} — a {@code @Valid} body that violates a constraint;</li>
 *   <li>{@link HttpMessageNotReadableException} — malformed JSON, a value Jackson cannot decode (an unknown
 *       {@code role}, for instance), or a missing body.</li>
 * </ul>
 *
 * <p>It is deliberately not applied to the other auth controllers: {@code AuthController} answers
 * {@code INVALID_AUTH_REQUEST} for login and refresh, and {@link AdminUserExceptionHandler} already maps these
 * failures for the administration endpoints.</p>
 *
 * <p>It runs first ({@link Ordered#HIGHEST_PRECEDENCE}) on purpose: Spring resolves a handler per advice by looking
 * at the exception and then at its cause chain, so any other advice with an {@link IllegalArgumentException} (or
 * wider) handler would otherwise win for an unreadable body whose cause is an {@code IllegalArgumentException}.
 * A controller-local handler always wins over every advice, which is why {@code UserController} and
 * {@code ActivationController}, whose local {@code IllegalArgumentException} handlers give that exception its own
 * meaning, declare a local handler for {@link HttpMessageNotReadableException} that answers through
 * {@link #invalidRequest()}.</p>
 *
 * <p>A new public controller marker must be added to {@code annotations}; {@code AuthRequestExceptionHandlerTest}
 * fails for a controller that reads a body and is covered by neither this advice nor another listed there.</p>
 */
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(annotations = {PublicActivationEndpoint.class, PublicPasswordResetEndpoint.class})
public class AuthRequestExceptionHandler {

    static final String INVALID_REQUEST_DETAIL = "La solicitud contiene datos inválidos.";
    static final String INVALID_REQUEST = "INVALID_REQUEST";

    @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class})
    ResponseEntity<ProblemDetail> invalidBody() {
        return invalidRequest();
    }

    /** The one answer every decoding failure gets, also used by the controllers' local handlers. */
    static ResponseEntity<ProblemDetail> invalidRequest() {
        return ProblemDetails.response(HttpStatus.BAD_REQUEST, INVALID_REQUEST_DETAIL, INVALID_REQUEST);
    }
}
