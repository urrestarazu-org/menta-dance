package com.menta.physical.infrastructure.web.controller;

import com.menta.physical.domain.exception.DeviceAlreadyRevokedException;
import com.menta.physical.domain.exception.DeviceNotFoundException;
import com.menta.physical.domain.exception.DeviceRevokedException;
import com.menta.physical.infrastructure.web.ProblemDetails;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * RFC 9457 Problem Details mapping for Physical's device-registry endpoints (#44,
 * US-PHYSICAL-007, design C6). {@code 409}, not {@code 422}, for both revoked-state rejections —
 * this module maps every "well-formed request, wrong resource state" case to {@code CONFLICT}
 * ({@code CourseHasActiveAssignmentsException}, {@code CapacityBelowAssignedException}, {@code
 * SessionAlreadyOccurredException} are all {@code 409}); re-revoking and rotating-after-revoke are
 * exactly that shape.
 */
@RestControllerAdvice(annotations = PhysicalDeviceEndpoint.class)
public class PhysicalDeviceExceptionHandler {

    @ExceptionHandler(DeviceNotFoundException.class)
    ResponseEntity<ProblemDetail> deviceNotFound(DeviceNotFoundException exception) {
        return ProblemDetails.response(HttpStatus.NOT_FOUND, "Device not found.", exception.getErrorCode());
    }

    @ExceptionHandler(DeviceAlreadyRevokedException.class)
    ResponseEntity<ProblemDetail> deviceAlreadyRevoked(DeviceAlreadyRevokedException exception) {
        return ProblemDetails.response(
            HttpStatus.CONFLICT, "Device is already revoked.", exception.getErrorCode()
        );
    }

    @ExceptionHandler(DeviceRevokedException.class)
    ResponseEntity<ProblemDetail> deviceRevoked(DeviceRevokedException exception) {
        return ProblemDetails.response(
            HttpStatus.CONFLICT, "Device is revoked and cannot be rotated.", exception.getErrorCode()
        );
    }

    /** A malformed {@code {deviceId}} path variable — never a 500. */
    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<ProblemDetail> malformedId(IllegalArgumentException exception) {
        return ProblemDetails.response(HttpStatus.BAD_REQUEST, "Malformed request.", "INVALID_REQUEST");
    }

    /**
     * Maps bean-validation failures to a generic RFC 9457 problem instead of falling through to
     * Spring's default handler, which would echo raw field messages back to the client.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ProblemDetail> invalidRequestBody(MethodArgumentNotValidException exception) {
        return ProblemDetails.response(
            HttpStatus.BAD_REQUEST, "La solicitud contiene datos inválidos.", "INVALID_REQUEST"
        );
    }
}
