package com.menta.physical.infrastructure.web.controller;

import com.menta.physical.domain.exception.CapacityAssignmentRequiredException;
import com.menta.physical.domain.exception.CheckInAlreadyProcessingException;
import com.menta.physical.domain.exception.CheckInDegradedException;
import com.menta.physical.domain.exception.ExpiredQrCredentialException;
import com.menta.physical.domain.exception.InvalidDeviceTokenException;
import com.menta.physical.domain.exception.InvalidQrCredentialException;
import com.menta.physical.domain.exception.OutsideCheckInWindowException;
import com.menta.physical.domain.exception.SessionCancelledException;
import com.menta.physical.domain.exception.SessionNotFoundException;
import com.menta.physical.infrastructure.web.ProblemDetails;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** RFC 9457 Problem Details mapping for Physical's check-in endpoints (US-PHYSICAL-001). */
@RestControllerAdvice(annotations = PhysicalCheckInEndpoint.class)
public class PhysicalCheckInExceptionHandler {

    private static final String RETRY_AFTER_DEGRADED_SECONDS = "30";

    @ExceptionHandler(InvalidDeviceTokenException.class)
    ResponseEntity<ProblemDetail> invalidDeviceToken(InvalidDeviceTokenException exception) {
        return ProblemDetails.response(
            HttpStatus.UNAUTHORIZED, "The device token is invalid.", exception.getErrorCode()
        );
    }

    @ExceptionHandler(InvalidQrCredentialException.class)
    ResponseEntity<ProblemDetail> invalidQrCredential(InvalidQrCredentialException exception) {
        return ProblemDetails.response(
            HttpStatus.BAD_REQUEST,
            "The QR credential is malformed or does not match the target session.",
            exception.getErrorCode()
        );
    }

    @ExceptionHandler(ExpiredQrCredentialException.class)
    ResponseEntity<ProblemDetail> expiredQrCredential(ExpiredQrCredentialException exception) {
        return ProblemDetails.response(
            HttpStatus.GONE, "The QR credential has expired.", exception.getErrorCode()
        );
    }

    @ExceptionHandler(SessionNotFoundException.class)
    ResponseEntity<ProblemDetail> sessionNotFound(SessionNotFoundException exception) {
        return ProblemDetails.response(
            HttpStatus.NOT_FOUND, "Session not found.", exception.getErrorCode()
        );
    }

    @ExceptionHandler(OutsideCheckInWindowException.class)
    ResponseEntity<ProblemDetail> outsideCheckInWindow(OutsideCheckInWindowException exception) {
        return ProblemDetails.response(
            HttpStatus.FORBIDDEN, "Check-in is not open for this session.", exception.getErrorCode()
        );
    }

    @ExceptionHandler(SessionCancelledException.class)
    ResponseEntity<ProblemDetail> sessionCancelled(SessionCancelledException exception) {
        return ProblemDetails.response(
            HttpStatus.FORBIDDEN,
            "This session has been cancelled and no longer accepts check-ins.",
            exception.getErrorCode()
        );
    }

    @ExceptionHandler(CapacityAssignmentRequiredException.class)
    ResponseEntity<ProblemDetail> capacityAssignmentRequired(
        CapacityAssignmentRequiredException exception
    ) {
        return ProblemDetails.response(
            HttpStatus.FORBIDDEN,
            "Confirmed capacity assignment is required to check in.",
            exception.getErrorCode()
        );
    }

    @ExceptionHandler(CheckInAlreadyProcessingException.class)
    ResponseEntity<ProblemDetail> checkInAlreadyProcessing(
        CheckInAlreadyProcessingException exception
    ) {
        return ProblemDetails.response(
            HttpStatus.CONFLICT,
            "Another reader is currently processing this check-in.",
            exception.getErrorCode()
        );
    }

    @ExceptionHandler(CheckInDegradedException.class)
    ResponseEntity<ProblemDetail> checkInDegraded(CheckInDegradedException exception) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
            .header(HttpHeaders.RETRY_AFTER, RETRY_AFTER_DEGRADED_SECONDS)
            .contentType(MediaType.APPLICATION_PROBLEM_JSON)
            .body(ProblemDetails.body(
                HttpStatus.SERVICE_UNAVAILABLE,
                "Check-in is temporarily unavailable; retry after 30s.",
                exception.getErrorCode()
            ));
    }

    /**
     * A malformed {@code sessionId} path variable — {@link
     * com.menta.physical.domain.model.SessionId#of(String)} rejects it with
     * an {@code IllegalArgumentException} before either use case runs. Same
     * criterion as {@code PhysicalCourseExceptionHandler#malformedId}: never
     * a 500.
     */
    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<ProblemDetail> malformedId(IllegalArgumentException exception) {
        return ProblemDetails.response(
            HttpStatus.BAD_REQUEST, "Malformed request.", "INVALID_REQUEST"
        );
    }

    /**
     * Maps bean-validation failures (e.g. {@code type != "QR"}, blank
     * fields) to a generic RFC 9457 problem instead of falling through to
     * Spring's default handler, which would echo raw field messages back to
     * the door reader.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ProblemDetail> invalidRequestBody(MethodArgumentNotValidException exception) {
        return ProblemDetails.response(
            HttpStatus.BAD_REQUEST, "La solicitud contiene datos inválidos.", "INVALID_REQUEST"
        );
    }
}
