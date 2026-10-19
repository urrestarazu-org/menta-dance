package com.menta.virtual.infrastructure.web.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.menta.virtual.application.dto.CourseProgressView;
import com.menta.virtual.application.dto.LessonProgressView;
import com.menta.virtual.application.port.in.CompleteLessonUseCase;
import com.menta.virtual.application.port.in.GetCourseProgressUseCase;
import com.menta.virtual.application.port.in.GetLessonProgressUseCase;
import com.menta.virtual.application.port.in.SaveLessonProgressUseCase;
import com.menta.virtual.domain.exception.CourseNotFoundException;
import com.menta.virtual.domain.exception.ForbiddenCourseProgressException;
import com.menta.virtual.domain.exception.ForbiddenLessonAccessException;
import com.menta.virtual.domain.exception.LessonNotFoundException;
import com.menta.virtual.infrastructure.web.dto.CourseProgressResponse;
import com.menta.virtual.infrastructure.web.dto.LessonProgressResponse;
import com.menta.virtual.infrastructure.web.dto.SaveLessonProgressRequest;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

class VirtualStudentProgressControllerTest {

    private final SaveLessonProgressUseCase saveUseCase = mock(SaveLessonProgressUseCase.class);
    private final GetLessonProgressUseCase getUseCase = mock(GetLessonProgressUseCase.class);
    private final CompleteLessonUseCase completeUseCase = mock(CompleteLessonUseCase.class);
    private final GetCourseProgressUseCase getCourseProgressUseCase = mock(GetCourseProgressUseCase.class);
    private final VirtualStudentProgressController controller = new VirtualStudentProgressController(
        saveUseCase, getUseCase, completeUseCase, getCourseProgressUseCase
    );

    private static Authentication authOf(UUID userId) {
        return new UsernamePasswordAuthenticationToken(
            userId.toString(), null, List.of(new SimpleGrantedAuthority("ROLE_STUDENT"))
        );
    }

    @Test
    void save_delegates_to_the_use_case_with_the_token_subject_as_acting_user() {
        UUID userId = UUID.randomUUID();
        String lessonId = UUID.randomUUID().toString();
        LessonProgressView view = new LessonProgressView(lessonId, 300, false, null);
        when(saveUseCase.save(lessonId, userId, 300)).thenReturn(view);

        ResponseEntity<LessonProgressResponse> response =
            controller.save(lessonId, new SaveLessonProgressRequest(300), authOf(userId));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().positionSeconds()).isEqualTo(300);
    }

    @Test
    void get_returns_the_use_cases_view() {
        UUID userId = UUID.randomUUID();
        String lessonId = UUID.randomUUID().toString();
        LessonProgressView view = new LessonProgressView(lessonId, 0, false, null);
        when(getUseCase.get(lessonId, userId)).thenReturn(Optional.of(view));

        ResponseEntity<LessonProgressResponse> response = controller.get(lessonId, authOf(userId));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().completed()).isFalse();
    }

    @Test
    void get_throws_not_found_when_the_use_case_returns_empty() {
        UUID userId = UUID.randomUUID();
        String lessonId = UUID.randomUUID().toString();
        when(getUseCase.get(lessonId, userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> controller.get(lessonId, authOf(userId)))
            .isInstanceOf(LessonNotFoundException.class);
    }

    @Test
    void complete_delegates_to_the_use_case() {
        UUID userId = UUID.randomUUID();
        String lessonId = UUID.randomUUID().toString();
        LessonProgressView view = new LessonProgressView(lessonId, 120, true, Instant.now());
        when(completeUseCase.complete(lessonId, userId)).thenReturn(view);

        ResponseEntity<LessonProgressResponse> response = controller.complete(lessonId, authOf(userId));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().completed()).isTrue();
    }

    @Test
    void denial_propagates_from_the_use_case_unchanged() {
        UUID userId = UUID.randomUUID();
        String lessonId = UUID.randomUUID().toString();
        when(saveUseCase.save(lessonId, userId, 0)).thenThrow(new ForbiddenLessonAccessException());

        assertThatThrownBy(() -> controller.save(lessonId, new SaveLessonProgressRequest(0), authOf(userId)))
            .isInstanceOf(ForbiddenLessonAccessException.class);
    }

    @Test
    void course_progress_returns_the_use_cases_assembled_view() {
        UUID userId = UUID.randomUUID();
        String courseId = UUID.randomUUID().toString();
        CourseProgressView.ResumeLesson resume =
            new CourseProgressView.ResumeLesson(UUID.randomUUID().toString(), UUID.randomUUID().toString(), 30, false);
        CourseProgressView view = new CourseProgressView(courseId, 1, 4, 25, resume);
        when(getCourseProgressUseCase.get(courseId, userId)).thenReturn(view);

        ResponseEntity<CourseProgressResponse> response = controller.courseProgress(courseId, authOf(userId));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().completedLessons()).isEqualTo(1);
        assertThat(response.getBody().totalLessons()).isEqualTo(4);
        assertThat(response.getBody().percentage()).isEqualTo(25);
        assertThat(response.getBody().resumeLesson().lessonId()).isEqualTo(resume.lessonId());
    }

    @Test
    void course_progress_zero_lesson_course_returns_a_zeroed_body_not_a_thrown_signal() {
        UUID userId = UUID.randomUUID();
        String courseId = UUID.randomUUID().toString();
        CourseProgressView view = new CourseProgressView(courseId, 0, 0, 0, null);
        when(getCourseProgressUseCase.get(courseId, userId)).thenReturn(view);

        ResponseEntity<CourseProgressResponse> response = controller.courseProgress(courseId, authOf(userId));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().totalLessons()).isZero();
        assertThat(response.getBody().resumeLesson()).isNull();
    }

    @Test
    void course_progress_denial_propagates_from_the_use_case_unchanged() {
        UUID userId = UUID.randomUUID();
        String courseId = UUID.randomUUID().toString();
        when(getCourseProgressUseCase.get(courseId, userId)).thenThrow(new ForbiddenCourseProgressException());

        assertThatThrownBy(() -> controller.courseProgress(courseId, authOf(userId)))
            .isInstanceOf(ForbiddenCourseProgressException.class);
    }

    @Test
    void course_progress_unknown_course_propagates_not_found_unchanged() {
        UUID userId = UUID.randomUUID();
        String courseId = UUID.randomUUID().toString();
        when(getCourseProgressUseCase.get(courseId, userId)).thenThrow(new CourseNotFoundException());

        assertThatThrownBy(() -> controller.courseProgress(courseId, authOf(userId)))
            .isInstanceOf(CourseNotFoundException.class);
    }
}
