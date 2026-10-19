package com.menta.billing.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class PlanCourseTest {

    @Test
    void wraps_a_course_id_by_value() {
        String courseId = UUID.randomUUID().toString();

        PlanCourse course = PlanCourse.of(courseId);

        assertThat(course.getCourseId()).isEqualTo(courseId);
    }

    @Test
    void rejects_a_null_course_id() {
        assertThatThrownBy(() -> PlanCourse.of(null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejects_a_blank_course_id() {
        assertThatThrownBy(() -> PlanCourse.of(" ")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void two_refs_to_the_same_course_id_are_equal() {
        String courseId = UUID.randomUUID().toString();

        assertThat(PlanCourse.of(courseId)).isEqualTo(PlanCourse.of(courseId));
        assertThat(PlanCourse.of(courseId).hashCode()).isEqualTo(PlanCourse.of(courseId).hashCode());
    }

    @Test
    void is_equal_to_itself() {
        PlanCourse course = PlanCourse.of("course-1");

        assertThat(course).isEqualTo(course);
    }

    @Test
    void is_not_equal_to_null_or_a_different_type() {
        PlanCourse course = PlanCourse.of("course-1");

        assertThat(course).isNotEqualTo(null);
        assertThat(course).isNotEqualTo("course-1");
    }
}
