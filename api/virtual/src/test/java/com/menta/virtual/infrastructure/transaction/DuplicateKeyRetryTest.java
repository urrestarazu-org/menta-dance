package com.menta.virtual.infrastructure.transaction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

class DuplicateKeyRetryTest {

    @Test
    void retries_once_on_a_duplicate_key_collision() {
        AtomicInteger attempts = new AtomicInteger();

        String result = DuplicateKeyRetry.once(() -> {
            if (attempts.getAndIncrement() == 0) {
                throw new DataIntegrityViolationException("duplicate key");
            }
            return "winner";
        });

        assertThat(result).isEqualTo("winner");
        assertThat(attempts.get()).isEqualTo(2);
    }

    @Test
    void does_not_retry_any_other_exception_type() {
        AtomicInteger attempts = new AtomicInteger();

        assertThatThrownBy(() -> DuplicateKeyRetry.once(() -> {
            attempts.incrementAndGet();
            throw new IllegalStateException("boom");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(attempts.get()).isEqualTo(1);
    }
}
