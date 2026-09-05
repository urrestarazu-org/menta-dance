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

    public static <T> T once(Supplier<T> attempt) {
        try {
            return attempt.get();
        } catch (DataIntegrityViolationException collision) {
            return attempt.get();
        }
    }
}
