package com.menta.physical.domain.exception;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SessionAlreadyOccurredExceptionTest {

    @Test
    void carries_a_stable_error_code() {
        assertThat(new SessionAlreadyOccurredException().getErrorCode()).isEqualTo("SESSION_ALREADY_OCCURRED");
    }
}
