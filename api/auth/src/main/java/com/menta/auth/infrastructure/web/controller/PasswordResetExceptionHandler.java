package com.menta.auth.infrastructure.web.controller;

import com.menta.auth.domain.exception.PasswordResetRateLimitedException;
import com.menta.auth.domain.exception.PasswordResetTokenAlreadyUsedException;
import com.menta.auth.domain.exception.PasswordResetTokenExpiredException;
import com.menta.auth.domain.exception.PasswordResetTokenNotFoundException;
import com.menta.auth.domain.exception.SamePasswordException;
import com.menta.auth.domain.exception.WeakPasswordException;
import com.menta.auth.domain.model.PasswordPolicyViolation;
import com.menta.auth.infrastructure.web.ProblemDetails;

import java.util.EnumMap;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * RFC 9457 Problem Details mapping for the public forgot-password / reset-password endpoints.
 *
 * <p>Unlike {@link ActivationExceptionHandler}, this handler does NOT collapse every failure into
 * one generic code. A reset token is a high-entropy secret handed only to the account holder's
 * inbox — not enumerable like an email — so distinguishing "not found" (404) from "expired" (410)
 * from "already used" (410) leaks nothing about the account, and gives the caller an actionable
 * reason (US-AUTH-006 escenarios 2-4). The forgot-password side keeps the anti-enumeration
 * discipline: {@link com.menta.auth.application.usecase.RequestPasswordResetUseCaseImpl} already
 * returns the identical, uniform result for every email, so no exception it can throw ever reveals
 * account state.</p>
 */
@RestControllerAdvice(annotations = PublicPasswordResetEndpoint.class)
public class PasswordResetExceptionHandler {

    private static final Map<PasswordPolicyViolation, String> VIOLATION_MESSAGES = new EnumMap<>(
        Map.of(
            PasswordPolicyViolation.TOO_SHORT, "al menos 8 caracteres",
            PasswordPolicyViolation.MISSING_UPPERCASE, "al menos una mayúscula",
            PasswordPolicyViolation.MISSING_DIGIT, "al menos un número"
        )
    );

    @ExceptionHandler(PasswordResetRateLimitedException.class)
    ResponseEntity<ProblemDetail> rateLimited(PasswordResetRateLimitedException exception) {
        long seconds = Math.max(1, exception.getRetryAfter().toSeconds());
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
            .header(HttpHeaders.RETRY_AFTER, Long.toString(seconds))
            .contentType(MediaType.APPLICATION_PROBLEM_JSON)
            .body(ProblemDetails.body(
                HttpStatus.TOO_MANY_REQUESTS,
                "Demasiados intentos; reintentá más tarde.",
                exception.getErrorCode()
            ));
    }

    @ExceptionHandler(PasswordResetTokenNotFoundException.class)
    ResponseEntity<ProblemDetail> tokenNotFound(PasswordResetTokenNotFoundException exception) {
        return ProblemDetails.response(
            HttpStatus.NOT_FOUND, "El enlace de recuperación es inválido.", exception.getErrorCode()
        );
    }

    @ExceptionHandler(PasswordResetTokenExpiredException.class)
    ResponseEntity<ProblemDetail> tokenExpired(PasswordResetTokenExpiredException exception) {
        return ProblemDetails.response(
            HttpStatus.GONE, "El enlace de recuperación expiró. Solicitá uno nuevo.", exception.getErrorCode()
        );
    }

    @ExceptionHandler(PasswordResetTokenAlreadyUsedException.class)
    ResponseEntity<ProblemDetail> tokenAlreadyUsed(PasswordResetTokenAlreadyUsedException exception) {
        return ProblemDetails.response(
            HttpStatus.GONE, "Este enlace ya fue utilizado.", exception.getErrorCode()
        );
    }

    @ExceptionHandler(WeakPasswordException.class)
    ResponseEntity<ProblemDetail> weakPassword(WeakPasswordException exception) {
        String violations = exception.getViolations().stream()
            .map(VIOLATION_MESSAGES::get)
            .collect(Collectors.joining(", "));
        return ProblemDetails.response(
            HttpStatus.BAD_REQUEST,
            "La contraseña no cumple los requisitos: " + violations + ".",
            exception.getErrorCode()
        );
    }

    @ExceptionHandler(SamePasswordException.class)
    ResponseEntity<ProblemDetail> samePassword(SamePasswordException exception) {
        return ProblemDetails.response(
            HttpStatus.BAD_REQUEST,
            "La nueva contraseña debe ser diferente a la actual.",
            exception.getErrorCode()
        );
    }

    /**
     * Maps bean-validation failures (e.g. malformed email, blank fields) to a generic RFC 9457
     * problem instead of falling through to Spring's default error handler, which would echo the
     * raw field messages back to the client.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ProblemDetail> invalidRequestBody(MethodArgumentNotValidException exception) {
        return ProblemDetails.response(
            HttpStatus.BAD_REQUEST, "La solicitud contiene datos inválidos.", "INVALID_REQUEST"
        );
    }
}
