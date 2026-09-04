package com.menta.billing.infrastructure.web.controller;

import com.menta.billing.domain.exception.PaymentMethodNotAcceptedException;
import com.menta.billing.domain.exception.PaymentPreferenceUnavailableException;
import com.menta.billing.domain.exception.PlanNotAvailableException;
import com.menta.billing.domain.exception.SubscriptionAlreadyActiveException;
import com.menta.billing.domain.exception.SubscriptionNotFoundException;
import com.menta.billing.domain.exception.UserNotFoundException;
import com.menta.billing.domain.model.PaymentMethod;
import com.menta.billing.infrastructure.web.ProblemDetails;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
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

    /**
     * US-BILLING-011 escenario 2 / A5. Absent, not-ACTIVE and (on the admin route) not-admin all
     * map here — never a 403, so the response cannot be used to probe which is true.
     */
    @ExceptionHandler(SubscriptionNotFoundException.class)
    ResponseEntity<ProblemDetail> subscriptionNotFound(SubscriptionNotFoundException exception) {
        return ProblemDetails.response(
            HttpStatus.NOT_FOUND, "No se encontró una suscripción cancelable.", exception.getErrorCode()
        );
    }

    /**
     * D8 (US-BILLING-012): the target {@code userId} does not reference an existing user. This
     * check runs before the plan-availability and already-in-force checks (design.md A5).
     */
    @ExceptionHandler(UserNotFoundException.class)
    ResponseEntity<ProblemDetail> userNotFound(UserNotFoundException exception) {
        return ProblemDetails.response(
            HttpStatus.NOT_FOUND, "No se encontró un usuario para el userId indicado.", exception.getErrorCode()
        );
    }

    /**
     * US-BILLING-012 design A14. The automatic expiry sweep and a cancellation (self-service or
     * admin) can both read the same {@code ACTIVE} row; whichever commits second loses the race
     * instead of silently overwriting the other, including erasing a cancellation audit trail.
     * This mapping widens the HTTP contract of every route carrying {@code @SubscriptionEndpoint}
     * — {@code POST /subscriptions}, {@code DELETE /subscriptions/me}, {@code DELETE
     * /admin/.../{subscriptionId}} and {@code POST .../trial} — from an unhandled {@code 500} to
     * a deterministic {@code 409}; a retry reads the terminal state.
     */
    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    ResponseEntity<ProblemDetail> optimisticLockConflict(ObjectOptimisticLockingFailureException exception) {
        return ProblemDetails.response(
            HttpStatus.CONFLICT,
            "La suscripción fue modificada por otra operación. Reintentá la solicitud.",
            "SUBSCRIPTION_CONFLICT"
        );
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
