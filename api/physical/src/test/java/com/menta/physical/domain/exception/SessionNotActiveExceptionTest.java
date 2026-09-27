package com.menta.physical.domain.exception;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SessionNotActiveExceptionTest {

    @Test
    void carries_a_stable_error_code() {
        assertThat(new SessionNotActiveException().getErrorCode())
            .isEqualTo("SESSION_NOT_ACTIVE");
    }
}
