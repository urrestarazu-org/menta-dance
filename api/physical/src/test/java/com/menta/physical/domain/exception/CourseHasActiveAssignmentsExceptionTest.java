package com.menta.physical.domain.exception;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CourseHasActiveAssignmentsExceptionTest {

    @Test
    void carries_a_stable_error_code() {
        assertThat(new CourseHasActiveAssignmentsException().getErrorCode())
            .isEqualTo("COURSE_HAS_ACTIVE_ASSIGNMENTS");
    }
}
