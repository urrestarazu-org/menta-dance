package com.menta.virtual.infrastructure.web.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.menta.virtual.domain.exception.CourseNotDraftException;
import com.menta.virtual.domain.exception.CourseNotFoundException;
import com.menta.virtual.domain.exception.CourseNotOwnedException;
import com.menta.virtual.domain.exception.CourseNotPublishableException;
import com.menta.virtual.domain.exception.LessonNotFoundException;
import com.menta.virtual.domain.exception.ModuleNotFoundException;
import com.menta.virtual.domain.exception.ModuleReorderMismatchException;
import com.menta.virtual.domain.exception.ProfessorMismatchException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;

class VirtualCourseExceptionHandlerTest {

    private final VirtualCourseExceptionHandler handler = new VirtualCourseExceptionHandler();

    @Test
    void maps_course_not_found_to_404() {
        ResponseEntity<ProblemDetail> response = handler.courseNotFound(new CourseNotFoundException());

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
    void maps_professor_mismatch_to_400() {
        ResponseEntity<ProblemDetail> response = handler.professorMismatch(new ProfessorMismatchException());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().getProperties().get("code")).isEqualTo("PROFESSOR_MISMATCH");
    }

    @Test
    void maps_course_not_draft_to_409() {
        ResponseEntity<ProblemDetail> response = handler.courseNotDraft(new CourseNotDraftException());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().getProperties().get("code")).isEqualTo("COURSE_NOT_DRAFT");
    }

    @Test
    void maps_course_not_publishable_to_400() {
        ResponseEntity<ProblemDetail> response =
            handler.courseNotPublishable(new CourseNotPublishableException("sin módulos"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().getProperties().get("code")).isEqualTo("COURSE_NOT_PUBLISHABLE");
    }

    @Test
    void maps_module_not_found_to_404() {
        ResponseEntity<ProblemDetail> response = handler.moduleNotFound(new ModuleNotFoundException());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().getProperties().get("code")).isEqualTo("MODULE_NOT_FOUND");
    }

    @Test
    void maps_lesson_not_found_to_404() {
        ResponseEntity<ProblemDetail> response = handler.lessonNotFound(new LessonNotFoundException());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().getProperties().get("code")).isEqualTo("LESSON_NOT_FOUND");
    }

    @Test
    void maps_module_reorder_mismatch_to_400() {
        ResponseEntity<ProblemDetail> response = handler.reorderMismatch(new ModuleReorderMismatchException());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().getProperties().get("code")).isEqualTo("MODULE_REORDER_MISMATCH");
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
