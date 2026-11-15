package com.menta.billing.infrastructure.web.controller;

import com.menta.billing.infrastructure.web.ProblemDetails;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * RFC 9457 Problem Details mapping for plan administration (#762).
 *
 * <p>Every way a create request can be wrong answers the same {@code 400 INVALID_REQUEST} the
 * other billing handlers use, with a generic detail that never echoes field messages:</p>
 * <ul>
 *   <li>{@link MethodArgumentNotValidException} — a missing field or a value outside the column
 *       bounds;</li>
 *   <li>{@link HttpMessageNotReadableException} — malformed JSON or a value Jackson cannot decode,
 *       such as an unknown {@code status} or payment method. Without this mapping Spring answers
 *       an empty {@code 400} with no {@code code};</li>
 *   <li>{@link IllegalArgumentException} — a rule of the use case or of the domain {@code Plan}:
 *       a price that is not positive, a duration that is not positive, a currency that is not
 *       ISO-4217, no payment method or a blank course id.</li>
 * </ul>
 */
@RestControllerAdvice(annotations = PlanAdminEndpoint.class)
public class PlanAdminExceptionHandler {

    @ExceptionHandler({
        MethodArgumentNotValidException.class,
        HttpMessageNotReadableException.class,
        IllegalArgumentException.class
    })
    ResponseEntity<ProblemDetail> invalidRequest() {
        return ProblemDetails.response(
            HttpStatus.BAD_REQUEST, "La solicitud contiene datos inválidos.", "INVALID_REQUEST"
        );
    }
}
