package com.menta.app.catalog;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** RFC 9457 Problem Details mapping for the public catalog endpoints (#95). */
@RestControllerAdvice(annotations = PublicCatalogEndpoint.class)
public class CatalogExceptionHandler {

    private static final String RETRY_AFTER_DEGRADED_SECONDS = "30";

    @ExceptionHandler(CourseNotFoundException.class)
    ResponseEntity<ProblemDetail> notFound(CourseNotFoundException exception) {
        return ProblemDetails.response(
            HttpStatus.NOT_FOUND, "Course not found in any modality.", exception.getErrorCode()
        );
    }

    @ExceptionHandler(CatalogUpstreamException.class)
    ResponseEntity<ProblemDetail> upstreamDegraded(CatalogUpstreamException exception) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
            .header(HttpHeaders.RETRY_AFTER, RETRY_AFTER_DEGRADED_SECONDS)
            .contentType(MediaType.APPLICATION_PROBLEM_JSON)
            .body(ProblemDetails.body(
                HttpStatus.SERVICE_UNAVAILABLE, exception.getMessage(), exception.getErrorCode()
            ));
    }
}
