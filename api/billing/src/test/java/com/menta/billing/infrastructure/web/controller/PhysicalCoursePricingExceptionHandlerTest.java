package com.menta.billing.infrastructure.web.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.menta.billing.domain.exception.InvalidIndividualSurchargeException;
import com.menta.billing.domain.exception.PhysicalCourseNotFoundException;
import com.menta.billing.domain.exception.PhysicalCoursePricingNotFoundException;
import com.menta.billing.domain.exception.PricingNotOwnedException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;

class PhysicalCoursePricingExceptionHandlerTest {

    private final PhysicalCoursePricingExceptionHandler handler = new PhysicalCoursePricingExceptionHandler();

    @Test
    void maps_course_not_found_to_404() {
        ResponseEntity<ProblemDetail> response = handler.courseNotFound(new PhysicalCourseNotFoundException());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().getProperties().get("code")).isEqualTo("PHYSICAL_COURSE_NOT_FOUND");
    }

    @Test
    void maps_pricing_not_found_to_404() {
        ResponseEntity<ProblemDetail> response =
            handler.pricingNotFound(new PhysicalCoursePricingNotFoundException());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().getProperties().get("code")).isEqualTo("PHYSICAL_COURSE_PRICING_NOT_FOUND");
    }

    @Test
    void maps_not_owned_to_403() {
        ResponseEntity<ProblemDetail> response = handler.notOwned(new PricingNotOwnedException());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody().getProperties().get("code")).isEqualTo("PRICING_NOT_OWNED");
    }

    @Test
    void maps_invalid_surcharge_to_422() {
        ResponseEntity<ProblemDetail> response =
            handler.invalidSurcharge(new InvalidIndividualSurchargeException());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(response.getBody().getProperties().get("code")).isEqualTo("INVALID_INDIVIDUAL_SURCHARGE");
    }

    @Test
    void maps_malformed_request_to_400() {
        ResponseEntity<ProblemDetail> response = handler.malformedRequest(new IllegalArgumentException("bad"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().getProperties().get("code")).isEqualTo("INVALID_REQUEST");
    }

    @Test
    void maps_bean_validation_failures_to_400() {
        ResponseEntity<ProblemDetail> response =
            handler.invalidRequestBody(mock(MethodArgumentNotValidException.class));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().getProperties().get("code")).isEqualTo("INVALID_REQUEST");
    }
}
