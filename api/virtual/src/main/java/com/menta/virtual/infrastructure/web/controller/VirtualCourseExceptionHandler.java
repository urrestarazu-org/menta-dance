package com.menta.virtual.infrastructure.web.controller;

import com.menta.virtual.domain.exception.CourseNotDraftException;
import com.menta.virtual.domain.exception.CourseNotFoundException;
import com.menta.virtual.domain.exception.CourseNotOwnedException;
import com.menta.virtual.domain.exception.CourseNotPublishableException;
import com.menta.virtual.domain.exception.LessonNotFoundException;
import com.menta.virtual.domain.exception.ModuleNotFoundException;
import com.menta.virtual.domain.exception.ModuleReorderMismatchException;
import com.menta.virtual.domain.exception.ProfessorMismatchException;
import com.menta.virtual.infrastructure.web.ProblemDetails;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** RFC 9457 Problem Details mapping for Virtual's management endpoints (US-VIRTUAL-006). */
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

    /**
     * A malformed {@code courseId}/{@code moduleId}/{@code lessonId} path
     * variable, an invalid {@code category}/{@code level} enum value, or a
     * malformed {@code professorId} body field — never a 500.
     */
    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<ProblemDetail> malformedId(IllegalArgumentException exception) {
        return ProblemDetails.response(HttpStatus.BAD_REQUEST, "Malformed request.", "INVALID_REQUEST");
    }

    /**
     * Maps bean-validation failures to a generic RFC 9457 problem instead of
     * falling through to Spring's default handler, which would echo raw
     * field messages back to the client.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ProblemDetail> invalidRequestBody(MethodArgumentNotValidException exception) {
        return ProblemDetails.response(
            HttpStatus.BAD_REQUEST, "La solicitud contiene datos inválidos.", "INVALID_REQUEST"
        );
    }
}
