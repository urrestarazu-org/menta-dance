package com.menta.physical.infrastructure.web.controller;

import com.menta.physical.domain.exception.CapacityBelowAssignedException;
import com.menta.physical.domain.exception.CourseHasActiveAssignmentsException;
import com.menta.physical.domain.exception.CourseNotFoundException;
import com.menta.physical.domain.exception.CourseNotOwnedException;
import com.menta.physical.domain.exception.ProfessorMismatchException;
import com.menta.physical.domain.exception.SessionAlreadyOccurredException;
import com.menta.physical.domain.exception.SessionNotFoundException;
import com.menta.physical.infrastructure.web.ProblemDetails;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** RFC 9457 Problem Details mapping for Physical's management endpoints (US-PHYSICAL-005, US-PHYSICAL-006). */
@RestControllerAdvice(annotations = PhysicalManagementEndpoint.class)
public class PhysicalCourseExceptionHandler {

    @ExceptionHandler(CourseNotFoundException.class)
    ResponseEntity<ProblemDetail> notFound(CourseNotFoundException exception) {
        return ProblemDetails.response(
            HttpStatus.NOT_FOUND, "Course not found.", exception.getErrorCode()
        );
    }

    @ExceptionHandler(CourseNotOwnedException.class)
    ResponseEntity<ProblemDetail> notOwned(CourseNotOwnedException exception) {
        return ProblemDetails.response(
            HttpStatus.FORBIDDEN, "You do not own this course.", exception.getErrorCode()
        );
    }

    @ExceptionHandler(CourseHasActiveAssignmentsException.class)
    ResponseEntity<ProblemDetail> hasActiveAssignments(
        CourseHasActiveAssignmentsException exception
    ) {
        return ProblemDetails.response(
            HttpStatus.CONFLICT,
            "Course has future sessions with confirmed assignments and cannot be deactivated.",
            exception.getErrorCode()
        );
    }

    @ExceptionHandler(ProfessorMismatchException.class)
    ResponseEntity<ProblemDetail> professorMismatch(ProfessorMismatchException exception) {
        return ProblemDetails.response(
            HttpStatus.BAD_REQUEST, "professorId must match the authenticated instructor.",
            exception.getErrorCode()
        );
    }

    @ExceptionHandler(SessionNotFoundException.class)
    ResponseEntity<ProblemDetail> sessionNotFound(SessionNotFoundException exception) {
        return ProblemDetails.response(
            HttpStatus.NOT_FOUND, "Session not found.", exception.getErrorCode()
        );
    }

    @ExceptionHandler(CapacityBelowAssignedException.class)
    ResponseEntity<ProblemDetail> capacityBelowAssigned(CapacityBelowAssignedException exception) {
        return ProblemDetails.response(
            HttpStatus.CONFLICT,
            "New capacity is below the number of already assigned spots.",
            exception.getErrorCode()
        );
    }

    @ExceptionHandler(SessionAlreadyOccurredException.class)
    ResponseEntity<ProblemDetail> sessionAlreadyOccurred(SessionAlreadyOccurredException exception) {
        return ProblemDetails.response(
            HttpStatus.CONFLICT,
            "Session has already occurred and cannot be modified.",
            exception.getErrorCode()
        );
    }

    /**
     * A malformed {@code courseId}/{@code sessionId} path variable or
     * {@code professorId} body field — never a 500.
     */
    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<ProblemDetail> malformedId(IllegalArgumentException exception) {
        return ProblemDetails.response(
            HttpStatus.BAD_REQUEST, "Malformed request.", "INVALID_REQUEST"
        );
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
