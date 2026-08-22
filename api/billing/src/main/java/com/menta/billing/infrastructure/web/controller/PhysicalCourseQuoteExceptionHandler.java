package com.menta.billing.infrastructure.web.controller;

import com.menta.billing.domain.exception.IndividualSurchargeTooSmallException;
import com.menta.billing.domain.exception.NoScheduledSessionsException;
import com.menta.billing.domain.exception.PhysicalCoursePricingNotFoundException;
import com.menta.billing.domain.exception.PhysicalSessionNotFoundException;
import com.menta.billing.domain.exception.SelectedSessionNotAllowedException;
import com.menta.billing.domain.exception.SelectedSessionRequiredException;
import com.menta.billing.infrastructure.web.ProblemDetails;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** RFC 9457 Problem Details mapping for the physical course quote endpoint (US-BILLING-006). */
@RestControllerAdvice(annotations = PhysicalQuoteEndpoint.class)
public class PhysicalCourseQuoteExceptionHandler {

    @ExceptionHandler(PhysicalCoursePricingNotFoundException.class)
    ResponseEntity<ProblemDetail> pricingNotFound(PhysicalCoursePricingNotFoundException exception) {
        return ProblemDetails.response(
            HttpStatus.NOT_FOUND, "Todavía no se publicó un precio para este curso.", exception.getErrorCode()
        );
    }

    @ExceptionHandler(PhysicalSessionNotFoundException.class)
    ResponseEntity<ProblemDetail> sessionNotFound(PhysicalSessionNotFoundException exception) {
        return ProblemDetails.response(
            HttpStatus.NOT_FOUND, "La sesión seleccionada no existe en el período cotizado.",
            exception.getErrorCode()
        );
    }

    @ExceptionHandler(NoScheduledSessionsException.class)
    ResponseEntity<ProblemDetail> noScheduledSessions(NoScheduledSessionsException exception) {
        return ProblemDetails.response(
            HttpStatus.UNPROCESSABLE_ENTITY, "No hay sesiones programadas para este período.",
            exception.getErrorCode()
        );
    }

    @ExceptionHandler(IndividualSurchargeTooSmallException.class)
    ResponseEntity<ProblemDetail> surchargeTooSmall(IndividualSurchargeTooSmallException exception) {
        return ProblemDetails.response(
            HttpStatus.UNPROCESSABLE_ENTITY,
            "El recargo individual no genera una diferencia de precio significativa respecto del precio sin "
                + "recargo.",
            exception.getErrorCode()
        );
    }

    @ExceptionHandler(SelectedSessionRequiredException.class)
    ResponseEntity<ProblemDetail> selectedSessionRequired(SelectedSessionRequiredException exception) {
        return ProblemDetails.response(
            HttpStatus.UNPROCESSABLE_ENTITY, "Debés indicar una sesión para una cotización individual.",
            exception.getErrorCode()
        );
    }

    @ExceptionHandler(SelectedSessionNotAllowedException.class)
    ResponseEntity<ProblemDetail> selectedSessionNotAllowed(SelectedSessionNotAllowedException exception) {
        return ProblemDetails.response(
            HttpStatus.UNPROCESSABLE_ENTITY, "No debés indicar una sesión para una cotización mensual.",
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
