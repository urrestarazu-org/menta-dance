package com.menta.physical.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.DayOfWeek;
import java.time.LocalTime;
import org.junit.jupiter.api.Test;

class PhysicalCourseTest {

    private static PhysicalCourse course(CourseStatus status) {
        return new PhysicalCourse(
            CourseId.generate(), "Salsa inicial", "María García",
            DayOfWeek.TUESDAY, LocalTime.of(19, 0), PhysicalCourseLevel.BEGINNER, 20, status
        );
    }

    @Test
    void is_active_reflects_status() {
        assertThat(course(CourseStatus.ACTIVE).isActive()).isTrue();
        assertThat(course(CourseStatus.INACTIVE).isActive()).isFalse();
    }

    @Test
    void rejects_negative_capacity() {
        assertThatThrownBy(() -> new PhysicalCourse(
            CourseId.generate(), "Salsa inicial", "María García",
            DayOfWeek.TUESDAY, LocalTime.of(19, 0), PhysicalCourseLevel.BEGINNER, -1, CourseStatus.ACTIVE
        )).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("capacity");
    }

    @Test
    void rejects_null_required_fields() {
        assertThatThrownBy(() -> new PhysicalCourse(
            null, "t", "p", DayOfWeek.MONDAY, LocalTime.NOON, PhysicalCourseLevel.BEGINNER, 1, CourseStatus.ACTIVE
        )).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new PhysicalCourse(
            CourseId.generate(), null, "p", DayOfWeek.MONDAY, LocalTime.NOON,
            PhysicalCourseLevel.BEGINNER, 1, CourseStatus.ACTIVE
        )).isInstanceOf(NullPointerException.class);
    }

    @Test
    void exposes_all_fields() {
        PhysicalCourse course = course(CourseStatus.ACTIVE);

        assertThat(course.getTitle()).isEqualTo("Salsa inicial");
        assertThat(course.getProfessorName()).isEqualTo("María García");
        assertThat(course.getDayOfWeek()).isEqualTo(DayOfWeek.TUESDAY);
        assertThat(course.getStartTime()).isEqualTo(LocalTime.of(19, 0));
        assertThat(course.getLevel()).isEqualTo(PhysicalCourseLevel.BEGINNER);
        assertThat(course.getCapacity()).isEqualTo(20);
        assertThat(course.getStatus()).isEqualTo(CourseStatus.ACTIVE);
    }
}
