package com.menta.virtual.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class CourseCategoryTest {

    @Test
    void rejects_null_or_blank() {
        assertThatThrownBy(() -> CourseCategory.of(null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CourseCategory.of("  ")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void accepts_and_returns_the_value() {
        assertThat(CourseCategory.of("tango").getValue()).isEqualTo("tango");
    }

    @Test
    void equal_when_the_value_matches() {
        CourseCategory a = CourseCategory.of("tango");
        CourseCategory b = CourseCategory.of("tango");

        assertThat(a).isEqualTo(a);
        assertThat(a).isEqualTo(b);
        assertThat(a).hasSameHashCodeAs(b);
    }

    @Test
    void not_equal_to_null_a_different_type_or_a_different_value() {
        CourseCategory tango = CourseCategory.of("tango");

        assertThat(tango).isNotEqualTo(null);
        assertThat(tango).isNotEqualTo("tango");
        assertThat(tango).isNotEqualTo(CourseCategory.of("salsa"));
    }

    @Test
    void to_string_is_the_raw_value() {
        assertThat(CourseCategory.of("tango").toString()).isEqualTo("tango");
    }
}
