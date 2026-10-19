package com.menta.virtual.infrastructure.transaction;

import com.menta.virtual.application.dto.LessonProgressView;
import com.menta.virtual.application.port.in.CompleteLessonUseCase;
import java.util.UUID;

/** Outermost retry decorator — see {@link RetryOnDuplicateKeySaveLessonProgressUseCase} for the rationale. */
public class RetryOnDuplicateKeyCompleteLessonUseCase implements CompleteLessonUseCase {

    private final CompleteLessonUseCase delegate;

    public RetryOnDuplicateKeyCompleteLessonUseCase(CompleteLessonUseCase delegate) {
        this.delegate = delegate;
    }

    @Override
    public LessonProgressView complete(String lessonId, UUID actingUserId) {
        return DuplicateKeyRetry.once(() -> delegate.complete(lessonId, actingUserId));
    }
}
