package com.menta.virtual.infrastructure.web.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.menta.virtual.domain.exception.CourseNotFoundException;
import com.menta.virtual.domain.exception.ForbiddenCourseProgressException;
import com.menta.virtual.domain.exception.ForbiddenLessonAccessException;
import com.menta.virtual.domain.exception.InvalidLessonPositionException;
import com.menta.virtual.domain.exception.LessonNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestControllerAdvice;

class VirtualStudentProgressExceptionHandlerTest {

    private final VirtualStudentProgressExceptionHandler handler = new VirtualStudentProgressExceptionHandler();

    @Test
    void maps_invalid_position_to_400() {
        ResponseEntity<ProblemDetail> response =
            handler.invalidPosition(new InvalidLessonPositionException(601, 600));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().getProperties().get("code")).isEqualTo("INVALID_LESSON_POSITION");
    }

    @Test
    void maps_forbidden_lesson_access_to_403() {
        ResponseEntity<ProblemDetail> response = handler.forbiddenLesson(new ForbiddenLessonAccessException());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody().getProperties().get("code"))
            .isEqualTo("LESSON_FORBIDDEN_SUBSCRIPTION_REQUIRED");
    }

    @Test
    void maps_lesson_not_found_to_404() {
        ResponseEntity<ProblemDetail> response = handler.lessonNotFound(new LessonNotFoundException());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().getDetail()).isEqualTo("Lección no encontrada.");
    }

    @Test
    void maps_malformed_id_to_404_not_400_to_preserve_anti_enumeration() {
        ResponseEntity<ProblemDetail> response = handler.malformedId(new IllegalArgumentException("bad uuid"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().getDetail()).isEqualTo("Lección no encontrada.");
    }

    @Test
    void maps_forbidden_course_progress_to_403() {
        ResponseEntity<ProblemDetail> response =
            handler.forbiddenCourseProgress(new ForbiddenCourseProgressException());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody().getProperties().get("code"))
            .isEqualTo("COURSE_PROGRESS_FORBIDDEN_SUBSCRIPTION_REQUIRED");
    }

    @Test
    void maps_course_not_found_to_404() {
        ResponseEntity<ProblemDetail> response = handler.courseNotFound(new CourseNotFoundException());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().getDetail()).isEqualTo("Curso no encontrado.");
    }

    @Test
    void advice_chain_only_applies_to_student_progress_controllers() {
        assertThat(VirtualStudentProgressExceptionHandler.class.getAnnotation(RestControllerAdvice.class)
            .annotations()).containsExactly(VirtualStudentEndpoint.class);
    }
}
