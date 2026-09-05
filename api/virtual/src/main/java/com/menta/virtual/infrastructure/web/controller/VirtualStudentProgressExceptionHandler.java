package com.menta.virtual.infrastructure.web.controller;

import com.menta.virtual.domain.exception.CourseNotFoundException;
import com.menta.virtual.domain.exception.ForbiddenCourseProgressException;
import com.menta.virtual.domain.exception.ForbiddenLessonAccessException;
import com.menta.virtual.domain.exception.InvalidLessonPositionException;
import com.menta.virtual.domain.exception.LessonNotFoundException;
import com.menta.virtual.infrastructure.web.ProblemDetails;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * RFC 9457 Problem Details mapping for Virtual's student-progress endpoints (US-VIRTUAL-005).
 * {@link InvalidLessonPositionException} is declared first — it is never an
 * {@code IllegalArgumentException} subtype, so an out-of-range position always reaches its own
 * 400 branch rather than the anti-enumeration 404 below it.
 */
@RestControllerAdvice(annotations = VirtualStudentEndpoint.class)
public class VirtualStudentProgressExceptionHandler {

    @ExceptionHandler(InvalidLessonPositionException.class)
    ResponseEntity<ProblemDetail> invalidPosition(InvalidLessonPositionException exception) {
        return ProblemDetails.response(HttpStatus.BAD_REQUEST, "Posición inválida.", exception.getErrorCode());
    }

    @ExceptionHandler(ForbiddenLessonAccessException.class)
    ResponseEntity<ProblemDetail> forbiddenLesson(ForbiddenLessonAccessException exception) {
        return ProblemDetails.response(
            HttpStatus.FORBIDDEN, "Esta lección requiere una suscripción activa", exception.getErrorCode()
        );
    }

    @ExceptionHandler(LessonNotFoundException.class)
    ResponseEntity<ProblemDetail> lessonNotFound(LessonNotFoundException exception) {
        return ProblemDetails.response(HttpStatus.NOT_FOUND, "Lección no encontrada.", exception.getErrorCode());
    }

    /**
     * Course-progress aggregate (Slice 3). Unlike {@link ForbiddenLessonAccessException}, there
     * is no free/preview exception on this path (design.md decision 5).
     */
    @ExceptionHandler(ForbiddenCourseProgressException.class)
    ResponseEntity<ProblemDetail> forbiddenCourseProgress(ForbiddenCourseProgressException exception) {
        return ProblemDetails.response(
            HttpStatus.FORBIDDEN, "Este curso requiere una suscripción activa", exception.getErrorCode()
        );
    }

    @ExceptionHandler(CourseNotFoundException.class)
    ResponseEntity<ProblemDetail> courseNotFound(CourseNotFoundException exception) {
        return ProblemDetails.response(HttpStatus.NOT_FOUND, "Curso no encontrado.", exception.getErrorCode());
    }

    /** Anti-enumeration: a malformed path id collapses into the same 404 a missing row would produce. */
    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<ProblemDetail> malformedId(IllegalArgumentException exception) {
        String code = LessonNotFoundException.class.getSimpleName().toUpperCase(java.util.Locale.ROOT);
        return ProblemDetails.response(HttpStatus.NOT_FOUND, "Lección no encontrada.", code);
    }
}
