package com.menta.billing.infrastructure.web.controller;

import com.menta.billing.domain.exception.WebhookSignatureInvalidException;
import com.menta.billing.domain.exception.WebhookTimestampExpiredException;
import com.menta.billing.infrastructure.web.ProblemDetails;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** RFC 9457 Problem Details mapping for the webhook endpoint — both failures are 401, never trusted or persisted. */
@RestControllerAdvice(annotations = PublicBillingEndpoint.class)
public class WebhookExceptionHandler {

    @ExceptionHandler(WebhookSignatureInvalidException.class)
    ResponseEntity<ProblemDetail> invalidSignature(WebhookSignatureInvalidException exception) {
        return ProblemDetails.response(
            HttpStatus.UNAUTHORIZED, "Webhook signature verification failed.", exception.getErrorCode()
        );
    }

    @ExceptionHandler(WebhookTimestampExpiredException.class)
    ResponseEntity<ProblemDetail> expiredTimestamp(WebhookTimestampExpiredException exception) {
        return ProblemDetails.response(
            HttpStatus.UNAUTHORIZED, "Webhook timestamp is too old.", exception.getErrorCode()
        );
    }
}
