package com.menta.billing.infrastructure.web.controller;

import com.menta.billing.infrastructure.web.ProblemDetails;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

/**
 * RFC 9457 Problem Details mapping for the ways a billing request can fail to decode (#788).
 *
 * <p>Every billing controller carries one of the marker annotations listed below, and each marker
 * has its own domain-exception advice. Request decoding failures are not domain exceptions and are
 * the same for all of them, so one advice covers every marker instead of repeating the handlers in
 * each. Without it Spring Boot answers a bare {@code 400} ({@code {timestamp, status, error,
 * message, path}}) with no {@code code}, and, with {@code include-binding-errors: always}, raw
 * field messages. Each failure answers {@code 400 INVALID_REQUEST} with a generic detail that never
 * echoes field messages:</p>
 * <ul>
 *   <li>{@link MethodArgumentNotValidException} — a {@code @Valid} body that violates a
 *       constraint;</li>
 *   <li>{@link HttpMessageNotReadableException} — malformed JSON or a value Jackson cannot
 *       decode;</li>
 *   <li>{@link MissingServletRequestPartException} — a multipart upload without its required
 *       part, such as the proof of a bank transfer without {@code file}.</li>
 * </ul>
 *
 * <p>A new controller marker must be added to {@code annotations}; {@code
 * BillingRequestExceptionHandlerTest} fails for a controller that no marker here covers.</p>
 */
@RestControllerAdvice(annotations = {
    PaymentEndpoint.class,
    PhysicalPricingEndpoint.class,
    PhysicalPurchaseEndpoint.class,
    PhysicalQuoteEndpoint.class,
    PlanAdminEndpoint.class,
    PublicBillingEndpoint.class,
    SubscriptionEndpoint.class
})
public class BillingRequestExceptionHandler {

    private static final String INVALID_REQUEST = "INVALID_REQUEST";

    @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class})
    ResponseEntity<ProblemDetail> invalidBody() {
        return ProblemDetails.response(
            HttpStatus.BAD_REQUEST, "La solicitud contiene datos inválidos.", INVALID_REQUEST
        );
    }

    @ExceptionHandler(MissingServletRequestPartException.class)
    ResponseEntity<ProblemDetail> missingPart() {
        return ProblemDetails.response(
            HttpStatus.BAD_REQUEST, "Falta una parte requerida de la solicitud.", INVALID_REQUEST
        );
    }
}
