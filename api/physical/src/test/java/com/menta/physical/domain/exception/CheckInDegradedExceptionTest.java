package com.menta.physical.domain.exception;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CheckInDegradedExceptionTest {

    @Test
    void carries_a_stable_error_code() {
        assertThat(new CheckInDegradedException().getErrorCode()).isEqualTo("CHECK_IN_DEGRADED");
    }
}
