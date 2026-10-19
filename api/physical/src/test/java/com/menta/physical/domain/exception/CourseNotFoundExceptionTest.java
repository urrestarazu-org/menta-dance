package com.menta.physical.domain.exception;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CourseNotFoundExceptionTest {

    @Test
    void carries_a_stable_error_code() {
        assertThat(new CourseNotFoundException().getErrorCode()).isEqualTo("COURSE_NOT_FOUND");
    }
}
