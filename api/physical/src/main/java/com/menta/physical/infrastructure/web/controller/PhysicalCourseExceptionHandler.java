package com.menta.physical.infrastructure.web.controller;

import com.menta.physical.domain.exception.CapacityBelowAssignedException;
import com.menta.physical.domain.exception.CourseHasActiveAssignmentsException;
import com.menta.physical.domain.exception.CourseNotFoundException;
import com.menta.physical.domain.exception.CourseNotOwnedException;
import com.menta.physical.domain.exception.EmptySessionBatchException;
import com.menta.physical.domain.exception.InvalidSessionRangeException;
import com.menta.physical.domain.exception.ProfessorMismatchException;
import com.menta.physical.domain.exception.ProfessorRequiredException;
import com.menta.physical.domain.exception.SessionAlreadyExistsException;
import com.menta.physical.domain.exception.SessionAlreadyOccurredException;
import com.menta.physical.domain.exception.SessionNotFoundException;
import com.menta.physical.infrastructure.web.ProblemDetails;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
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

    @ExceptionHandler(ProfessorRequiredException.class)
    ResponseEntity<ProblemDetail> professorRequired(ProfessorRequiredException exception) {
        return ProblemDetails.response(
            HttpStatus.BAD_REQUEST, "professorId is required when an administrator creates a course.",
            exception.getErrorCode()
        );
    }

    /**
     * A session creation that would repeat an instant the course already has: lists the conflicting
     * instants, ISO-8601 in UTC, under {@code conflictingDates} (#743).
     */
    @ExceptionHandler(SessionAlreadyExistsException.class)
    ResponseEntity<ProblemDetail> sessionAlreadyExists(SessionAlreadyExistsException exception) {
        ResponseEntity<ProblemDetail> response = ProblemDetails.response(
            HttpStatus.CONFLICT,
            "The course already has a session at one or more of the requested dates.",
            exception.getErrorCode()
        );
        response.getBody().setProperty(
            "conflictingDates", exception.getConflictingInstants().stream().map(Instant::toString).toList()
        );
        return response;
    }

    /** A batch whose range is inverted or holds no date on the course's day of week (#743). */
    @ExceptionHandler(EmptySessionBatchException.class)
    ResponseEntity<ProblemDetail> emptySessionBatch(EmptySessionBatchException exception) {
        return ProblemDetails.response(
            HttpStatus.BAD_REQUEST,
            "The range must have fromDate not after toDate and include a date on the course's day of week.",
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

    /** A query parameter that is not a valid ISO instant, e.g. {@code from} of the staff range listing (#355). */
    @ExceptionHandler(DateTimeParseException.class)
    ResponseEntity<ProblemDetail> malformedParameter(DateTimeParseException exception) {
        return ProblemDetails.response(
            HttpStatus.BAD_REQUEST, "Malformed request.", "INVALID_REQUEST"
        );
    }

    /** A required query parameter that is absent, e.g. {@code to} of the staff range listing (#355). */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    ResponseEntity<ProblemDetail> missingParameter(MissingServletRequestParameterException exception) {
        return ProblemDetails.response(
            HttpStatus.BAD_REQUEST, "Malformed request.", "INVALID_REQUEST"
        );
    }

    /** An empty, inverted or too wide instant range of the staff session listing (#355). */
    @ExceptionHandler(InvalidSessionRangeException.class)
    ResponseEntity<ProblemDetail> invalidRange(InvalidSessionRangeException exception) {
        return ProblemDetails.response(
            HttpStatus.BAD_REQUEST,
            "The range must have from before to and span at most " + exception.getMaxDays() + " days.",
            exception.getErrorCode()
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
