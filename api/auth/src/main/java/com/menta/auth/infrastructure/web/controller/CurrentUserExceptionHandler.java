package com.menta.auth.infrastructure.web.controller;

import com.menta.auth.domain.exception.UserNotFoundException;
import com.menta.auth.infrastructure.web.ProblemDetails;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * RFC 9457 Problem Details mapping for the own-account endpoint.
 *
 * <p>A still-valid access token whose account was deleted no longer identifies anyone, so it answers exactly what
 * the security entry point answers for a missing or invalid token: {@code 401 AUTHENTICATION_REQUIRED}. A
 * {@code 404} would tell the client the account existed.
 */
@RestControllerAdvice(annotations = CurrentUserEndpoint.class)
public class CurrentUserExceptionHandler {

    /** Same detail and code as {@code ProblemJsonSecurityHandlers.authenticationEntryPoint}. */
    private static final String AUTHENTICATION_REQUIRED_DETAIL = "Authentication is required.";
    private static final String AUTHENTICATION_REQUIRED = "AUTHENTICATION_REQUIRED";

    @ExceptionHandler(UserNotFoundException.class)
    ResponseEntity<ProblemDetail> accountGone() {
        return ProblemDetails.response(
            HttpStatus.UNAUTHORIZED, AUTHENTICATION_REQUIRED_DETAIL, AUTHENTICATION_REQUIRED
        );
    }
}
