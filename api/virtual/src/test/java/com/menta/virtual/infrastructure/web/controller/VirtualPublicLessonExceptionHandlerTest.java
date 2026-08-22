package com.menta.virtual.infrastructure.web.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.menta.virtual.domain.exception.ForbiddenLessonAccessException;
import com.menta.virtual.domain.exception.LessonNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;

/**
 * Coverage for {@link VirtualPublicLessonExceptionHandler}. Distinct from
 * {@code VirtualCourseExceptionHandlerTest} because the public advice
 * chain maps the SAME {@link LessonNotFoundException} to 404 with a
 * Spanish detail ("Lección no encontrada"), while the management one
 * uses an English detail ("Lesson not found") — the change is small
 * but visible to the BFF / Android client, so we lock it.
 */
class VirtualPublicLessonExceptionHandlerTest {

    private final VirtualPublicLessonExceptionHandler handler = new VirtualPublicLessonExceptionHandler();

    @Test
    void maps_lesson_not_found_to_404_with_spanish_detail() {
        ResponseEntity<ProblemDetail> response = handler.lessonNotFound(new LessonNotFoundException());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().getDetail()).isEqualTo("Lección no encontrada.");
        assertThat(response.getBody().getProperties().get("code")).isEqualTo("LESSON_NOT_FOUND");
    }

    @Test
    void maps_forbidden_lesson_access_to_403_with_subscriptions_required_code() {
        ResponseEntity<ProblemDetail> response =
            handler.forbiddenLesson(new ForbiddenLessonAccessException());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody().getDetail()).contains("suscripción");
        assertThat(response.getBody().getProperties().get("code"))
            .isEqualTo("LESSON_FORBIDDEN_SUBSCRIPTION_REQUIRED");
    }

    @Test
    void maps_malformed_lesson_id_to_404_not_400_to_preserve_anti_enumeration() {
        // Anti-enumeration discipline: a malformed lessonId must look IDENTICAL
        // to a missing one — same status, same detail, same code. The former
        // VirtualCourseExceptionHandler maps IllegalArgumentException to 400 with
        // "INVALID_REQUEST"; the public advice chain does NOT inherit that.
        ResponseEntity<ProblemDetail> response = handler.malformedLessonId(new IllegalArgumentException("bad uuid"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().getDetail()).isEqualTo("Lección no encontrada.");
    }

    @Test
    void public_advice_chain_only_applies_to_public_controllers() {
        // Static structural guard: keep the chain tied to the public marker so a
        // future refactor cannot accidentally route management endpoints through
        // it. If a developer drops the annotations parameter, the marker is lost.
        assertThat(VirtualPublicLessonExceptionHandler.class.getAnnotation(
            org.springframework.web.bind.annotation.RestControllerAdvice.class
        ).annotations()).containsExactly(PublicVirtualEndpoint.class);
    }
}
