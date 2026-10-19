package com.menta.physical.domain.exception;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class InvalidDeviceTokenExceptionTest {

    @Test
    void carries_a_stable_error_code() {
        assertThat(new InvalidDeviceTokenException().getErrorCode())
            .isEqualTo("INVALID_DEVICE_TOKEN");
    }
}
