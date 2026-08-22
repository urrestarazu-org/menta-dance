package com.menta.billing.infrastructure.web.controller;

import com.menta.billing.domain.exception.PaymentMethodNotAcceptedException;
import com.menta.billing.domain.exception.PaymentPreferenceUnavailableException;
import com.menta.billing.domain.exception.PlanNotAvailableException;
import com.menta.billing.domain.exception.SubscriptionAlreadyActiveException;
import com.menta.billing.domain.model.PaymentMethod;
import com.menta.billing.infrastructure.web.ProblemDetails;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** RFC 9457 Problem Details mapping for the subscription checkout endpoint (US-BILLING-010). */
@RestControllerAdvice(annotations = SubscriptionEndpoint.class)
public class SubscriptionExceptionHandler {

    /**
     * Escenario 4. One status for "does not exist" and "is INACTIVE" alike:
     * telling them apart would turn the endpoint into a plan-id oracle.
     */
    @ExceptionHandler(PlanNotAvailableException.class)
    ResponseEntity<ProblemDetail> planNotAvailable(PlanNotAvailableException exception) {
        return ProblemDetails.response(
            HttpStatus.UNPROCESSABLE_ENTITY, "El plan no está disponible para suscribirse.",
            exception.getErrorCode()
        );
    }

    /** Escenario 4b — the response names what the plan does accept. */
    @ExceptionHandler(PaymentMethodNotAcceptedException.class)
    ResponseEntity<ProblemDetail> paymentMethodNotAccepted(PaymentMethodNotAcceptedException exception) {
        ProblemDetail problemDetail = ProblemDetails.body(
            HttpStatus.UNPROCESSABLE_ENTITY,
            "El plan no acepta el método de pago solicitado.", exception.getErrorCode()
        );
        problemDetail.setProperty("acceptedPaymentMethods", acceptedNames(exception));
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
            .contentType(org.springframework.http.MediaType.APPLICATION_PROBLEM_JSON)
            .body(problemDetail);
    }

    /** Escenario 3 — reports the current subscription's expiry when it has one. */
    @ExceptionHandler(SubscriptionAlreadyActiveException.class)
    ResponseEntity<ProblemDetail> subscriptionAlreadyActive(SubscriptionAlreadyActiveException exception) {
        ProblemDetail problemDetail = ProblemDetails.body(
            HttpStatus.CONFLICT, "Ya tenés una suscripción vigente.", exception.getErrorCode()
        );
        exception.getCurrentEndDate()
            .ifPresent(endDate -> problemDetail.setProperty("currentEndDate", endDate.toString()));
        return ResponseEntity.status(HttpStatus.CONFLICT)
            .contentType(org.springframework.http.MediaType.APPLICATION_PROBLEM_JSON)
            .body(problemDetail);
    }

    /** The provider could not open a checkout — nothing was written and nothing was charged. */
    @ExceptionHandler(PaymentPreferenceUnavailableException.class)
    ResponseEntity<ProblemDetail> preferenceUnavailable(PaymentPreferenceUnavailableException exception) {
        return ProblemDetails.response(
            HttpStatus.SERVICE_UNAVAILABLE,
            "No pudimos iniciar el pago en este momento. Intentá de nuevo en unos minutos.",
            exception.getErrorCode()
        );
    }

    /** A malformed body field — for instance a {@code planId} that is not a UUID. Never a 500. */
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

    private static List<String> acceptedNames(PaymentMethodNotAcceptedException exception) {
        return exception.getAcceptedPaymentMethods().stream().map(PaymentMethod::name).sorted().toList();
    }
}
