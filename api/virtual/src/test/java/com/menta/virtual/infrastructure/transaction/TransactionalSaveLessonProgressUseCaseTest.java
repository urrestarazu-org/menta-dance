package com.menta.virtual.infrastructure.transaction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.menta.virtual.application.dto.LessonProgressView;
import com.menta.virtual.application.port.in.SaveLessonProgressUseCase;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TransactionalSaveLessonProgressUseCaseTest {

    private final SaveLessonProgressUseCase delegate = mock(SaveLessonProgressUseCase.class);
    private final TransactionalSaveLessonProgressUseCase decorator =
        new TransactionalSaveLessonProgressUseCase(delegate);

    @Test
    void delegates_the_save_call() {
        String lessonId = UUID.randomUUID().toString();
        UUID userId = UUID.randomUUID();
        LessonProgressView view = new LessonProgressView(lessonId, 300, false, (Instant) null);
        when(delegate.save(lessonId, userId, 300)).thenReturn(view);

        LessonProgressView actual = decorator.save(lessonId, userId, 300);

        assertThat(actual).isEqualTo(view);
        verify(delegate).save(lessonId, userId, 300);
    }
}
