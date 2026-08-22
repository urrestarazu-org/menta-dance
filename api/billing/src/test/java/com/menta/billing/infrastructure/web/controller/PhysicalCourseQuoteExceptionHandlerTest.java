package com.menta.billing.infrastructure.web.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.menta.billing.domain.exception.IndividualSurchargeTooSmallException;
import com.menta.billing.domain.exception.NoScheduledSessionsException;
import com.menta.billing.domain.exception.PhysicalCoursePricingNotFoundException;
import com.menta.billing.domain.exception.PhysicalSessionNotFoundException;
import com.menta.billing.domain.exception.SelectedSessionNotAllowedException;
import com.menta.billing.domain.exception.SelectedSessionRequiredException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;

class PhysicalCourseQuoteExceptionHandlerTest {

    private final PhysicalCourseQuoteExceptionHandler handler = new PhysicalCourseQuoteExceptionHandler();

    @Test
    void maps_pricing_not_found_to_404() {
        ResponseEntity<ProblemDetail> response =
            handler.pricingNotFound(new PhysicalCoursePricingNotFoundException());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().getProperties().get("code")).isEqualTo("PHYSICAL_COURSE_PRICING_NOT_FOUND");
    }

    @Test
    void maps_session_not_found_to_404() {
        ResponseEntity<ProblemDetail> response = handler.sessionNotFound(new PhysicalSessionNotFoundException());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().getProperties().get("code")).isEqualTo("PHYSICAL_SESSION_NOT_FOUND");
    }

    @Test
    void maps_no_scheduled_sessions_to_422() {
        ResponseEntity<ProblemDetail> response = handler.noScheduledSessions(new NoScheduledSessionsException());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(response.getBody().getProperties().get("code")).isEqualTo("NO_SCHEDULED_SESSIONS");
    }

    @Test
    void maps_surcharge_too_small_to_422() {
        ResponseEntity<ProblemDetail> response =
            handler.surchargeTooSmall(new IndividualSurchargeTooSmallException());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(response.getBody().getProperties().get("code")).isEqualTo("INDIVIDUAL_SURCHARGE_TOO_SMALL");
    }

    @Test
    void maps_selected_session_required_to_422() {
        ResponseEntity<ProblemDetail> response =
            handler.selectedSessionRequired(new SelectedSessionRequiredException());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(response.getBody().getProperties().get("code")).isEqualTo("SELECTED_SESSION_REQUIRED");
    }

    @Test
    void maps_selected_session_not_allowed_to_422() {
        ResponseEntity<ProblemDetail> response =
            handler.selectedSessionNotAllowed(new SelectedSessionNotAllowedException());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(response.getBody().getProperties().get("code")).isEqualTo("SELECTED_SESSION_NOT_ALLOWED");
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
