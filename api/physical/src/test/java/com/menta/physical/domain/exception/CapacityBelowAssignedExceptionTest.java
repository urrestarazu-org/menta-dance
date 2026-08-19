package com.menta.physical.domain.exception;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CapacityBelowAssignedExceptionTest {

    @Test
    void carries_a_stable_error_code() {
        assertThat(new CapacityBelowAssignedException().getErrorCode()).isEqualTo("CAPACITY_BELOW_ASSIGNED");
    }
}
