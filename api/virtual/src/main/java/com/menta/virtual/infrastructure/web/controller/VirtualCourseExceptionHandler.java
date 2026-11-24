package com.menta.virtual.infrastructure.web.controller;

import com.menta.virtual.domain.exception.CourseHasProgressException;
import com.menta.virtual.domain.exception.CourseInUseException;
import com.menta.virtual.domain.exception.CourseNotDraftException;
import com.menta.virtual.domain.exception.CourseNotFoundException;
import com.menta.virtual.domain.exception.CourseNotOwnedException;
import com.menta.virtual.domain.exception.CourseNotPublishableException;
import com.menta.virtual.domain.exception.LessonNotFoundException;
import com.menta.virtual.domain.exception.ModuleNotFoundException;
import com.menta.virtual.domain.exception.ModuleReorderMismatchException;
import com.menta.virtual.domain.exception.ProfessorMismatchException;
import com.menta.virtual.domain.exception.VideoLibraryUnavailableException;
import com.menta.virtual.infrastructure.web.ProblemDetails;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * RFC 9457 Problem Details mapping for Virtual's management endpoints (US-VIRTUAL-006). Request
 * decoding failures (a body that is unreadable, empty or fails bean validation) are answered by
 * {@link VirtualRequestExceptionHandler}.
 */
@RestControllerAdvice(annotations = VirtualManagementEndpoint.class)
public class VirtualCourseExceptionHandler {

    @ExceptionHandler(CourseNotFoundException.class)
    ResponseEntity<ProblemDetail> courseNotFound(CourseNotFoundException exception) {
        return ProblemDetails.response(HttpStatus.NOT_FOUND, "Course not found.", exception.getErrorCode());
    }

    @ExceptionHandler(CourseNotOwnedException.class)
    ResponseEntity<ProblemDetail> notOwned(CourseNotOwnedException exception) {
        return ProblemDetails.response(
            HttpStatus.FORBIDDEN, "You do not own this course.", exception.getErrorCode()
        );
    }

    @ExceptionHandler(ProfessorMismatchException.class)
    ResponseEntity<ProblemDetail> professorMismatch(ProfessorMismatchException exception) {
        return ProblemDetails.response(
            HttpStatus.BAD_REQUEST, "professorId must match the authenticated instructor.",
            exception.getErrorCode()
        );
    }

    @ExceptionHandler(CourseNotDraftException.class)
    ResponseEntity<ProblemDetail> courseNotDraft(CourseNotDraftException exception) {
        return ProblemDetails.response(
            HttpStatus.CONFLICT, "Only a DRAFT course can be deleted.", exception.getErrorCode()
        );
    }

    @ExceptionHandler(CourseHasProgressException.class)
    ResponseEntity<ProblemDetail> courseHasProgress(CourseHasProgressException exception) {
        return ProblemDetails.response(
            HttpStatus.CONFLICT, "El curso no se puede borrar porque ya tiene progreso de alumnos.",
            exception.getErrorCode()
        );
    }

    @ExceptionHandler(CourseInUseException.class)
    ResponseEntity<ProblemDetail> courseInUse(CourseInUseException exception) {
        return ProblemDetails.response(
            HttpStatus.CONFLICT, "El curso no se puede borrar porque está incluido en un plan o en una suscripción.",
            exception.getErrorCode()
        );
    }

    @ExceptionHandler(CourseNotPublishableException.class)
    ResponseEntity<ProblemDetail> courseNotPublishable(CourseNotPublishableException exception) {
        return ProblemDetails.response(HttpStatus.BAD_REQUEST, exception.getMessage(), exception.getErrorCode());
    }

    @ExceptionHandler(ModuleNotFoundException.class)
    ResponseEntity<ProblemDetail> moduleNotFound(ModuleNotFoundException exception) {
        return ProblemDetails.response(HttpStatus.NOT_FOUND, "Module not found.", exception.getErrorCode());
    }

    @ExceptionHandler(LessonNotFoundException.class)
    ResponseEntity<ProblemDetail> lessonNotFound(LessonNotFoundException exception) {
        return ProblemDetails.response(HttpStatus.NOT_FOUND, "Lesson not found.", exception.getErrorCode());
    }

    @ExceptionHandler(ModuleReorderMismatchException.class)
    ResponseEntity<ProblemDetail> reorderMismatch(ModuleReorderMismatchException exception) {
        return ProblemDetails.response(HttpStatus.BAD_REQUEST, exception.getMessage(), exception.getErrorCode());
    }

    /** The video library cannot answer (unreachable, timeout, error or rejected key); the reason stays in the log. */
    @ExceptionHandler(VideoLibraryUnavailableException.class)
    ResponseEntity<ProblemDetail> videoLibraryUnavailable(VideoLibraryUnavailableException exception) {
        return ProblemDetails.response(
            HttpStatus.SERVICE_UNAVAILABLE,
            "La biblioteca de videos no está disponible en este momento. Intentá de nuevo en unos minutos.",
            exception.getErrorCode()
        );
    }

    /** A query parameter that is not of the expected type, such as {@code page=abc}. */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ResponseEntity<ProblemDetail> malformedParameter(MethodArgumentTypeMismatchException exception) {
        return ProblemDetails.response(HttpStatus.BAD_REQUEST, "Malformed request.", "INVALID_REQUEST");
    }

    /**
     * A malformed {@code courseId}/{@code moduleId}/{@code lessonId} path
     * variable, an invalid {@code category}/{@code level} enum value, or a
     * malformed {@code professorId} body field — never a 500.
     */
    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<ProblemDetail> malformedId(IllegalArgumentException exception) {
        return ProblemDetails.response(HttpStatus.BAD_REQUEST, "Malformed request.", "INVALID_REQUEST");
    }
}
