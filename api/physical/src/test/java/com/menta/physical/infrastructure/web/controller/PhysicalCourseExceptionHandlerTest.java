package com.menta.physical.infrastructure.web.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.menta.physical.domain.exception.CourseHasActiveAssignmentsException;
import com.menta.physical.domain.exception.CourseNotFoundException;
import com.menta.physical.domain.exception.CourseNotOwnedException;
import com.menta.physical.domain.exception.ProfessorMismatchException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;

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
    void maps_malformed_id_to_400() {
        ResponseEntity<ProblemDetail> response = handler.malformedId(new IllegalArgumentException("bad uuid"));

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
