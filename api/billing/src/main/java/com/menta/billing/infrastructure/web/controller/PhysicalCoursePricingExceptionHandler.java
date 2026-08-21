package com.menta.billing.infrastructure.web.controller;

import com.menta.billing.domain.exception.InvalidIndividualSurchargeException;
import com.menta.billing.domain.exception.PhysicalCourseNotFoundException;
import com.menta.billing.domain.exception.PhysicalCoursePricingNotFoundException;
import com.menta.billing.domain.exception.PricingNotOwnedException;
import com.menta.billing.infrastructure.web.ProblemDetails;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** RFC 9457 Problem Details mapping for the physical course pricing endpoints (US-BILLING-009). */
@RestControllerAdvice(annotations = PhysicalPricingEndpoint.class)
public class PhysicalCoursePricingExceptionHandler {

    @ExceptionHandler(PhysicalCourseNotFoundException.class)
    ResponseEntity<ProblemDetail> courseNotFound(PhysicalCourseNotFoundException exception) {
        return ProblemDetails.response(
            HttpStatus.NOT_FOUND, "El curso presencial no existe.", exception.getErrorCode()
        );
    }

    @ExceptionHandler(PhysicalCoursePricingNotFoundException.class)
    ResponseEntity<ProblemDetail> pricingNotFound(PhysicalCoursePricingNotFoundException exception) {
        return ProblemDetails.response(
            HttpStatus.NOT_FOUND, "Todavía no se publicó un precio para este curso.", exception.getErrorCode()
        );
    }

    @ExceptionHandler(PricingNotOwnedException.class)
    ResponseEntity<ProblemDetail> notOwned(PricingNotOwnedException exception) {
        return ProblemDetails.response(
            HttpStatus.FORBIDDEN, "No sos el profesor responsable de este curso.", exception.getErrorCode()
        );
    }

    @ExceptionHandler(InvalidIndividualSurchargeException.class)
    ResponseEntity<ProblemDetail> invalidSurcharge(InvalidIndividualSurchargeException exception) {
        return ProblemDetails.response(
            HttpStatus.UNPROCESSABLE_ENTITY, "El recargo individual debe ser mayor a cero.",
            exception.getErrorCode()
        );
    }

    /** A malformed body field — never a 500. */
    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<ProblemDetail> malformedRequest(IllegalArgumentException exception) {
        return ProblemDetails.response(HttpStatus.BAD_REQUEST, "La solicitud es inválida.", "INVALID_REQUEST");
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
