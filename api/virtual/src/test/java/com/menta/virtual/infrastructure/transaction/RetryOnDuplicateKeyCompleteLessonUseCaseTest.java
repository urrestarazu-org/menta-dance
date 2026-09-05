package com.menta.virtual.infrastructure.transaction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.menta.virtual.application.dto.LessonProgressView;
import com.menta.virtual.application.port.in.CompleteLessonUseCase;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

class RetryOnDuplicateKeyCompleteLessonUseCaseTest {

    private final CompleteLessonUseCase delegate = mock(CompleteLessonUseCase.class);
    private final RetryOnDuplicateKeyCompleteLessonUseCase decorator =
        new RetryOnDuplicateKeyCompleteLessonUseCase(delegate);

    @Test
    void retries_the_complete_call_once_after_a_duplicate_key_collision() {
        String lessonId = UUID.randomUUID().toString();
        UUID userId = UUID.randomUUID();
        LessonProgressView view = new LessonProgressView(lessonId, 120, true, Instant.now());
        when(delegate.complete(lessonId, userId))
            .thenThrow(new DataIntegrityViolationException("collision"))
            .thenReturn(view);

        LessonProgressView actual = decorator.complete(lessonId, userId);

        assertThat(actual).isEqualTo(view);
    }
}
