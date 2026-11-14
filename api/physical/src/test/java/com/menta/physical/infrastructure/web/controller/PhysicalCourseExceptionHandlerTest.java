package com.menta.physical.infrastructure.web.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.menta.physical.domain.exception.CapacityBelowAssignedException;
import com.menta.physical.domain.exception.CourseHasActiveAssignmentsException;
import com.menta.physical.domain.exception.CourseNotFoundException;
import com.menta.physical.domain.exception.CourseNotOwnedException;
import com.menta.physical.domain.exception.EmptySessionBatchException;
import com.menta.physical.domain.exception.InvalidSessionRangeException;
import com.menta.physical.domain.exception.ProfessorMismatchException;
import com.menta.physical.domain.exception.ProfessorRequiredException;
import com.menta.physical.domain.exception.SessionAlreadyExistsException;
import com.menta.physical.domain.exception.SessionAlreadyOccurredException;
import com.menta.physical.domain.exception.SessionNotFoundException;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;

class PhysicalCourseExceptionHandlerTest {

    private final PhysicalCourseExceptionHandler handler = new PhysicalCourseExceptionHandler();

    @Test
    void maps_course_not_found_to_404() {
        ResponseEntity<ProblemDetail> response = handler.notFound(new CourseNotFoundException());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().getProperties().get("code")).isEqualTo("COURSE_NOT_FOUND");
    }

    @Test
    void maps_course_not_owned_to_403() {
        ResponseEntity<ProblemDetail> response = handler.notOwned(new CourseNotOwnedException());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody().getProperties().get("code")).isEqualTo("COURSE_NOT_OWNED");
    }

    @Test
    void maps_course_has_active_assignments_to_409() {
        ResponseEntity<ProblemDetail> response =
            handler.hasActiveAssignments(new CourseHasActiveAssignmentsException());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().getProperties().get("code")).isEqualTo("COURSE_HAS_ACTIVE_ASSIGNMENTS");
    }

    @Test
    void maps_professor_mismatch_to_400() {
        ResponseEntity<ProblemDetail> response = handler.professorMismatch(new ProfessorMismatchException());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().getProperties().get("code")).isEqualTo("PROFESSOR_MISMATCH");
    }

    @Test
    void maps_professor_required_to_400() {
        ResponseEntity<ProblemDetail> response = handler.professorRequired(new ProfessorRequiredException());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().getProperties().get("code")).isEqualTo("PROFESSOR_REQUIRED");
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
    }

    @Test
    void maps_session_already_exists_to_409_listing_the_conflicting_dates() {
        ResponseEntity<ProblemDetail> response = handler.sessionAlreadyExists(new SessionAlreadyExistsException(
            List.of(Instant.parse("2026-09-09T20:00:00Z"), Instant.parse("2026-09-02T20:00:00Z"))
        ));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().getProperties().get("code")).isEqualTo("SESSION_ALREADY_EXISTS");
        assertThat(response.getBody().getProperties().get("conflictingDates"))
            .isEqualTo(List.of("2026-09-02T20:00:00Z", "2026-09-09T20:00:00Z"));
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
    }

    @Test
    void maps_an_empty_session_batch_to_400() {
        ResponseEntity<ProblemDetail> response = handler.emptySessionBatch(new EmptySessionBatchException());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().getProperties().get("code")).isEqualTo("INVALID_REQUEST");
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
    }

    @Test
    void maps_session_not_found_to_404() {
        ResponseEntity<ProblemDetail> response = handler.sessionNotFound(new SessionNotFoundException());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().getProperties().get("code")).isEqualTo("SESSION_NOT_FOUND");
    }

    @Test
    void maps_capacity_below_assigned_to_409() {
        ResponseEntity<ProblemDetail> response =
            handler.capacityBelowAssigned(new CapacityBelowAssignedException());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().getProperties().get("code")).isEqualTo("CAPACITY_BELOW_ASSIGNED");
    }

    @Test
    void maps_session_already_occurred_to_409() {
        ResponseEntity<ProblemDetail> response =
            handler.sessionAlreadyOccurred(new SessionAlreadyOccurredException());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().getProperties().get("code")).isEqualTo("SESSION_ALREADY_OCCURRED");
    }

    @Test
    void maps_malformed_id_to_400() {
        ResponseEntity<ProblemDetail> response = handler.malformedId(new IllegalArgumentException("bad uuid"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().getProperties().get("code")).isEqualTo("INVALID_REQUEST");
    }

    @Test
    void maps_a_malformed_instant_parameter_to_400() {
        ResponseEntity<ProblemDetail> response =
            handler.malformedParameter(new DateTimeParseException("bad instant", "2026-13-40T00:00:00Z", 0));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().getProperties().get("code")).isEqualTo("INVALID_REQUEST");
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
    }

    @Test
    void maps_an_invalid_session_range_to_400() {
        ResponseEntity<ProblemDetail> response = handler.invalidRange(new InvalidSessionRangeException(7));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().getProperties().get("code")).isEqualTo("INVALID_REQUEST");
        assertThat(response.getBody().getDetail())
            .isEqualTo("The range must have from before to and span at most 7 days.");
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
    }

    @Test
    void maps_a_missing_request_parameter_to_400() {
        ResponseEntity<ProblemDetail> response =
            handler.missingParameter(new MissingServletRequestParameterException("from", "String"));

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
