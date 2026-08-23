package com.menta.physical.infrastructure.web.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.menta.physical.domain.exception.CapacityAssignmentRequiredException;
import com.menta.physical.domain.exception.CheckInAlreadyProcessingException;
import com.menta.physical.domain.exception.CheckInDegradedException;
import com.menta.physical.domain.exception.ExpiredQrCredentialException;
import com.menta.physical.domain.exception.InvalidDeviceTokenException;
import com.menta.physical.domain.exception.InvalidQrCredentialException;
import com.menta.physical.domain.exception.OutsideCheckInWindowException;
import com.menta.physical.domain.exception.SessionCancelledException;
import com.menta.physical.domain.exception.SessionNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;

class PhysicalCheckInExceptionHandlerTest {

    private final PhysicalCheckInExceptionHandler handler = new PhysicalCheckInExceptionHandler();

    @Test
    void maps_invalid_device_token_to_401() {
        ResponseEntity<ProblemDetail> response =
            handler.invalidDeviceToken(new InvalidDeviceTokenException());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(codeOf(response)).isEqualTo("INVALID_DEVICE_TOKEN");
    }

    @Test
    void maps_invalid_qr_credential_to_400() {
        ResponseEntity<ProblemDetail> response =
            handler.invalidQrCredential(new InvalidQrCredentialException());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(codeOf(response)).isEqualTo("INVALID_QR_CREDENTIAL");
    }

    @Test
    void maps_expired_qr_credential_to_410() {
        ResponseEntity<ProblemDetail> response =
            handler.expiredQrCredential(new ExpiredQrCredentialException());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.GONE);
        assertThat(codeOf(response)).isEqualTo("EXPIRED_QR_CREDENTIAL");
    }

    @Test
    void maps_session_not_found_to_404() {
        ResponseEntity<ProblemDetail> response =
            handler.sessionNotFound(new SessionNotFoundException());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(codeOf(response)).isEqualTo("SESSION_NOT_FOUND");
    }

    @Test
    void maps_outside_check_in_window_to_403() {
        ResponseEntity<ProblemDetail> response =
            handler.outsideCheckInWindow(new OutsideCheckInWindowException());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(codeOf(response)).isEqualTo("OUTSIDE_CHECK_IN_WINDOW");
    }

    @Test
    void maps_session_cancelled_to_403() {
        ResponseEntity<ProblemDetail> response =
            handler.sessionCancelled(new SessionCancelledException());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(codeOf(response)).isEqualTo("SESSION_CANCELLED");
    }

    @Test
    void maps_capacity_assignment_required_to_403() {
        ResponseEntity<ProblemDetail> response =
            handler.capacityAssignmentRequired(new CapacityAssignmentRequiredException());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(codeOf(response)).isEqualTo("CAPACITY_ASSIGNMENT_REQUIRED");
    }

    @Test
    void maps_check_in_already_processing_to_409() {
        ResponseEntity<ProblemDetail> response =
            handler.checkInAlreadyProcessing(new CheckInAlreadyProcessingException());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(codeOf(response)).isEqualTo("ALREADY_PROCESSING");
    }

    @Test
    void maps_check_in_degraded_to_503_with_retry_after() {
        ResponseEntity<ProblemDetail> response =
            handler.checkInDegraded(new CheckInDegradedException());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getHeaders().getFirst(HttpHeaders.RETRY_AFTER)).isEqualTo("30");
        assertThat(codeOf(response)).isEqualTo("CHECK_IN_DEGRADED");
    }

    @Test
    void maps_malformed_id_to_400() {
        ResponseEntity<ProblemDetail> response =
            handler.malformedId(new IllegalArgumentException("bad uuid"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(codeOf(response)).isEqualTo("INVALID_REQUEST");
    }

    @Test
    void maps_bean_validation_failures_to_400() {
        ResponseEntity<ProblemDetail> response =
            handler.invalidRequestBody(mock(MethodArgumentNotValidException.class));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(codeOf(response)).isEqualTo("INVALID_REQUEST");
    }

    private static Object codeOf(ResponseEntity<ProblemDetail> response) {
        return response.getBody().getProperties().get("code");
    }
}
