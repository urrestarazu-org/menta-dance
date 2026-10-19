package com.menta.virtual.infrastructure.transaction;

import java.util.function.Supplier;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * Retries a write exactly once when it collides with a concurrent first insert on
 * {@code uq_virtual_lesson_progress_user_lesson} (design's "Upsert concurrency" section). Any
 * other exception type propagates immediately, uncaught.
 */
public final class DuplicateKeyRetry {

    private DuplicateKeyRetry() {
    }

    /**
     * Runs the attempt and, if it fails with a {@link DataIntegrityViolationException}, runs it
     * once more; a second failure propagates.
     */
    public static <T> T once(Supplier<T> attempt) {
        try {
            return attempt.get();
        } catch (DataIntegrityViolationException collision) {
            return attempt.get();
        }
    }
}
