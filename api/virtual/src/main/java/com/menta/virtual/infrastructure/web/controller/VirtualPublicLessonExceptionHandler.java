package com.menta.virtual.infrastructure.web.controller;

import com.menta.virtual.domain.exception.ForbiddenLessonAccessException;
import com.menta.virtual.domain.exception.LessonNotFoundException;
import com.menta.virtual.infrastructure.web.ProblemDetails;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * RFC 9457 Problem Details mapping for Virtual's public read endpoints
 * (US-VIRTUAL-003). Sealed off from the management advice chain
 * ({@code VirtualCourseExceptionHandler}, keyed on
 * {@code VirtualManagementEndpoint}) because the public chain needs a
 * different {@link IllegalArgumentException} policy: there is no
 * meaningful 400 for a malformed {@code lessonId} on the public surface
 * — that would let an attacker enumerate pre-burn UUIDs. Same shape as
 * {@code api:app}'s {@code CatalogExceptionHandler}.
 *
 * <p>This advice introduces {@code 403} as an explicit outcome of the
 * Virtual surface for the first time — prior to #48 only
 * {@link com.menta.virtual.domain.exception.CourseNotOwnedException}
 * produced 403, and only on the management prefix. The new
 * {@link ForbiddenLessonAccessException} is reached when an anonymous
 * caller hits a premium lesson, and the resulting RFC 9457 problem
 * carries the {@code LESSON_FORBIDDEN_SUBSCRIPTION_REQUIRED} code so
 * the BFF can decide whether to redirect to /login or to /plans.</p>
 */
@RestControllerAdvice(annotations = PublicVirtualEndpoint.class)
public class VirtualPublicLessonExceptionHandler {

    @ExceptionHandler(LessonNotFoundException.class)
    ResponseEntity<ProblemDetail> lessonNotFound(LessonNotFoundException exception) {
        return ProblemDetails.response(HttpStatus.NOT_FOUND, "Lección no encontrada.", exception.getErrorCode());
    }

    @ExceptionHandler(ForbiddenLessonAccessException.class)
    ResponseEntity<ProblemDetail> forbiddenLesson(ForbiddenLessonAccessException exception) {
        return ProblemDetails.response(
            HttpStatus.FORBIDDEN,
            "Esta lección requiere una suscripción activa",
            exception.getErrorCode()
        );
    }

    /**
     * Anti-enumeration: a malformed {@code lessonId} path variable
     * surfaces as a {@code LessonNotFoundException} → 404, exactly the
     * same response a well-formed-but-missing id would produce.
     * Collapsing the two cases into one response is the entire point
     * of this advice chain — separate it from the management one by
     * all means necessary.
     */
    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<ProblemDetail> malformedLessonId(IllegalArgumentException exception) {
        return ProblemDetails.response(
            HttpStatus.NOT_FOUND, "Lección no encontrada.", LessonNotFoundException.class.getSimpleName().toUpperCase()
        );
    }
}
