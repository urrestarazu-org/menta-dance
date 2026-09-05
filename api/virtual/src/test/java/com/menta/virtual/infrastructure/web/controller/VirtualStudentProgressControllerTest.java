package com.menta.virtual.infrastructure.web.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.menta.virtual.application.dto.LessonProgressView;
import com.menta.virtual.application.port.in.CompleteLessonUseCase;
import com.menta.virtual.application.port.in.GetLessonProgressUseCase;
import com.menta.virtual.application.port.in.SaveLessonProgressUseCase;
import com.menta.virtual.domain.exception.ForbiddenLessonAccessException;
import com.menta.virtual.domain.exception.LessonNotFoundException;
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
    private final VirtualStudentProgressController controller =
        new VirtualStudentProgressController(saveUseCase, getUseCase, completeUseCase);

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
}
