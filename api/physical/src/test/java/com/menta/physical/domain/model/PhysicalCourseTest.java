package com.menta.physical.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PhysicalCourseTest {

    private static final UUID PROFESSOR_ID = UUID.randomUUID();

    private static PhysicalCourse course(CourseStatus status) {
        return new PhysicalCourse(
            CourseId.generate(), "Salsa inicial", "Curso de salsa para nivel inicial", PROFESSOR_ID,
            "María García", DayOfWeek.TUESDAY, LocalTime.of(19, 0), 60, PhysicalCourseLevel.BEGINNER, 20,
            status
        );
    }

    @Test
    void is_active_reflects_status() {
        assertThat(course(CourseStatus.ACTIVE).isActive()).isTrue();
        assertThat(course(CourseStatus.INACTIVE).isActive()).isFalse();
    }

    @Test
    void rejects_non_positive_capacity() {
        assertThatThrownBy(() -> new PhysicalCourse(
            CourseId.generate(), "Salsa inicial", "desc", PROFESSOR_ID, "María García",
            DayOfWeek.TUESDAY, LocalTime.of(19, 0), 60, PhysicalCourseLevel.BEGINNER, 0, CourseStatus.ACTIVE
        )).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("capacity");
    }

    @Test
    void rejects_non_positive_duration() {
        assertThatThrownBy(() -> new PhysicalCourse(
            CourseId.generate(), "Salsa inicial", "desc", PROFESSOR_ID, "María García",
            DayOfWeek.TUESDAY, LocalTime.of(19, 0), 0, PhysicalCourseLevel.BEGINNER, 20, CourseStatus.ACTIVE
        )).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("durationMinutes");
    }

    @Test
    void rejects_null_required_fields() {
        assertThatThrownBy(() -> new PhysicalCourse(
            null, "t", "d", PROFESSOR_ID, "p", DayOfWeek.MONDAY, LocalTime.NOON, 60,
            PhysicalCourseLevel.BEGINNER, 1, CourseStatus.ACTIVE
        )).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new PhysicalCourse(
            CourseId.generate(), null, "d", PROFESSOR_ID, "p", DayOfWeek.MONDAY, LocalTime.NOON, 60,
            PhysicalCourseLevel.BEGINNER, 1, CourseStatus.ACTIVE
        )).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new PhysicalCourse(
            CourseId.generate(), "t", "d", null, "p", DayOfWeek.MONDAY, LocalTime.NOON, 60,
            PhysicalCourseLevel.BEGINNER, 1, CourseStatus.ACTIVE
        )).isInstanceOf(NullPointerException.class);
    }

    @Test
    void exposes_all_fields() {
        PhysicalCourse course = course(CourseStatus.ACTIVE);

        assertThat(course.getTitle()).isEqualTo("Salsa inicial");
        assertThat(course.getDescription()).isEqualTo("Curso de salsa para nivel inicial");
        assertThat(course.getProfessorId()).isEqualTo(PROFESSOR_ID);
        assertThat(course.getProfessorName()).isEqualTo("María García");
        assertThat(course.getDayOfWeek()).isEqualTo(DayOfWeek.TUESDAY);
        assertThat(course.getStartTime()).isEqualTo(LocalTime.of(19, 0));
        assertThat(course.getDurationMinutes()).isEqualTo(60);
        assertThat(course.getLevel()).isEqualTo(PhysicalCourseLevel.BEGINNER);
        assertThat(course.getCapacity()).isEqualTo(20);
        assertThat(course.getStatus()).isEqualTo(CourseStatus.ACTIVE);
    }

    @Test
    void create_generates_a_fresh_active_course() {
        PhysicalCourse created = PhysicalCourse.create(
            "Salsa inicial", "desc", PROFESSOR_ID, "María García", DayOfWeek.TUESDAY,
            LocalTime.of(19, 0), 60, PhysicalCourseLevel.BEGINNER, 20
        );

        assertThat(created.getId()).isNotNull();
        assertThat(created.getStatus()).isEqualTo(CourseStatus.ACTIVE);
    }

    @Test
    void is_owned_by_compares_professor_id() {
        PhysicalCourse course = course(CourseStatus.ACTIVE);

        assertThat(course.isOwnedBy(PROFESSOR_ID)).isTrue();
        assertThat(course.isOwnedBy(UUID.randomUUID())).isFalse();
    }

    @Test
    void with_title_replaces_only_the_title() {
        PhysicalCourse updated = course(CourseStatus.ACTIVE).withTitle("Salsa avanzada");

        assertThat(updated.getTitle()).isEqualTo("Salsa avanzada");
        assertThat(updated.getCapacity()).isEqualTo(20);
    }

    @Test
    void with_description_replaces_only_the_description() {
        PhysicalCourse updated = course(CourseStatus.ACTIVE).withDescription("Nueva descripción");

        assertThat(updated.getDescription()).isEqualTo("Nueva descripción");
        assertThat(updated.getTitle()).isEqualTo("Salsa inicial");
    }

    @Test
    void with_schedule_replaces_day_time_and_duration_together() {
        PhysicalCourse updated =
            course(CourseStatus.ACTIVE).withSchedule(DayOfWeek.FRIDAY, LocalTime.of(21, 0), 90);

        assertThat(updated.getDayOfWeek()).isEqualTo(DayOfWeek.FRIDAY);
        assertThat(updated.getStartTime()).isEqualTo(LocalTime.of(21, 0));
        assertThat(updated.getDurationMinutes()).isEqualTo(90);
    }

    @Test
    void with_level_replaces_only_the_level() {
        PhysicalCourse updated = course(CourseStatus.ACTIVE).withLevel(PhysicalCourseLevel.ADVANCED);

        assertThat(updated.getLevel()).isEqualTo(PhysicalCourseLevel.ADVANCED);
    }

    @Test
    void with_capacity_never_touches_other_fields() {
        PhysicalCourse original = course(CourseStatus.ACTIVE);
        PhysicalCourse updated = original.withCapacity(30);

        assertThat(updated.getCapacity()).isEqualTo(30);
        assertThat(updated.getTitle()).isEqualTo(original.getTitle());
        assertThat(updated.getDayOfWeek()).isEqualTo(original.getDayOfWeek());
    }

    @Test
    void with_status_replaces_only_the_status() {
        PhysicalCourse updated = course(CourseStatus.ACTIVE).withStatus(CourseStatus.INACTIVE);

        assertThat(updated.getStatus()).isEqualTo(CourseStatus.INACTIVE);
        assertThat(updated.isActive()).isFalse();
    }
}
