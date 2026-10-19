package com.menta.physical.domain.exception;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CourseNotOwnedExceptionTest {

    @Test
    void carries_a_stable_error_code() {
        assertThat(new CourseNotOwnedException().getErrorCode()).isEqualTo("COURSE_NOT_OWNED");
    }
}
