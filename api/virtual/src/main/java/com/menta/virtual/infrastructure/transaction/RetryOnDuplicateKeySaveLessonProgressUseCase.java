package com.menta.virtual.infrastructure.transaction;

import com.menta.virtual.application.dto.LessonProgressView;
import com.menta.virtual.application.port.in.SaveLessonProgressUseCase;
import java.util.UUID;

/**
 * Outermost retry decorator (design's "Upsert concurrency"): the loser of a concurrent first
 * insert hits the unique key and rolls back inside {@link TransactionalSaveLessonProgressUseCase};
 * retrying here, outside that transactional proxy, opens a fresh transaction that finds the
 * winner's row and updates it instead.
 */
public class RetryOnDuplicateKeySaveLessonProgressUseCase implements SaveLessonProgressUseCase {

    private final SaveLessonProgressUseCase delegate;

    public RetryOnDuplicateKeySaveLessonProgressUseCase(SaveLessonProgressUseCase delegate) {
        this.delegate = delegate;
    }

    @Override
    public LessonProgressView save(String lessonId, UUID actingUserId, int positionSeconds) {
        return DuplicateKeyRetry.once(() -> delegate.save(lessonId, actingUserId, positionSeconds));
    }
}
