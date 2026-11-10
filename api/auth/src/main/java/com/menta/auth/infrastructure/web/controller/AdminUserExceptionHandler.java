package com.menta.auth.infrastructure.web.controller;

import com.menta.auth.domain.exception.LastAdminCannotBeDemotedException;
import com.menta.auth.domain.exception.UserNotFoundException;
import com.menta.auth.infrastructure.web.ProblemDetails;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * RFC 9457 Problem Details mapping for the user administration endpoints. Messages are generic on purpose: they
 * never echo the input or any internal detail.
 *
 * <p>Every malformed request, whatever the cause (bean validation, unreadable body or role, bad UUID, bad paging),
 * collapses into one {@code INVALID_REQUEST} problem.
 */
@RestControllerAdvice(annotations = AdminUserEndpoint.class)
public class AdminUserExceptionHandler {

    private static final String INVALID_REQUEST = "INVALID_REQUEST";

    @ExceptionHandler(UserNotFoundException.class)
    ResponseEntity<ProblemDetail> userNotFound(UserNotFoundException exception) {
        return ProblemDetails.response(HttpStatus.NOT_FOUND, "El usuario no existe.", exception.getErrorCode());
    }

    @ExceptionHandler(LastAdminCannotBeDemotedException.class)
    ResponseEntity<ProblemDetail> lastAdmin(LastAdminCannotBeDemotedException exception) {
        return ProblemDetails.response(
            HttpStatus.CONFLICT,
            "No se puede quitar el rol de administrador al último administrador activo.",
            exception.getErrorCode()
        );
    }

    @ExceptionHandler({
        MethodArgumentNotValidException.class,
        HttpMessageNotReadableException.class,
        MethodArgumentTypeMismatchException.class,
        IllegalArgumentException.class
    })
    ResponseEntity<ProblemDetail> invalidRequest() {
        return ProblemDetails.response(
            HttpStatus.BAD_REQUEST, "La solicitud contiene datos inválidos.", INVALID_REQUEST
        );
    }
}
