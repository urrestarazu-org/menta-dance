package com.menta.physical.domain.exception;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class SessionAlreadyExistsExceptionTest {

    @Test
    void carries_a_stable_error_code() {
        assertThat(new SessionAlreadyExistsException(List.of(Instant.parse("2026-09-02T20:00:00Z"))).getErrorCode())
            .isEqualTo("SESSION_ALREADY_EXISTS");
    }

    @Test
    void exposes_the_conflicting_instants_sorted_ascending() {
        Instant first = Instant.parse("2026-09-02T20:00:00Z");
        Instant second = Instant.parse("2026-09-09T20:00:00Z");

        SessionAlreadyExistsException exception = new SessionAlreadyExistsException(List.of(second, first));

        assertThat(exception.getConflictingInstants()).containsExactly(first, second);
    }
}
