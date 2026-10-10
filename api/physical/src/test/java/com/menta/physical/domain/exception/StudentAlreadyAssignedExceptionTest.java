package com.menta.physical.domain.exception;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class StudentAlreadyAssignedExceptionTest {

    @Test
    void carries_a_stable_error_code_distinct_from_the_capacity_one() {
        StudentAlreadyAssignedException exception = new StudentAlreadyAssignedException();

        assertThat(exception.getErrorCode()).isEqualTo("STUDENT_ALREADY_ASSIGNED");
        assertThat(exception.getErrorCode()).isNotEqualTo(new CapacityBelowAssignedException().getErrorCode());
    }
}
