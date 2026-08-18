package com.menta.billing.domain.exception;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PlanNotFoundExceptionTest {

    @Test
    void carries_a_stable_error_code() {
        PlanNotFoundException exception = new PlanNotFoundException();

        assertThat(exception.getErrorCode()).isEqualTo("PLAN_NOT_FOUND");
    }
}
