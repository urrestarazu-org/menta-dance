package com.menta.physical.domain.exception;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class EmptySessionBatchExceptionTest {

    @Test
    void carries_the_generic_invalid_request_code() {
        assertThat(new EmptySessionBatchException().getErrorCode()).isEqualTo("INVALID_REQUEST");
    }
}
