package com.menta.virtual.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class VirtualCourseTest {

    private static VirtualCourse published() {
        return new VirtualCourse(
            CourseId.generate(), "Tango Básico", "Aprendé los pasos fundamentales",
            "https://cdn/tango.jpg", CourseCategory.of("tango"), CourseLevel.BEGINNER,
            true, CourseStatus.PUBLISHED, 5, 20, 150
        );
    }

    @Test
    void rejects_a_null_id() {
        assertThatThrownBy(() -> new VirtualCourse(
            null, "t", "d", "i", CourseCategory.of("tango"), CourseLevel.BEGINNER,
            false, CourseStatus.PUBLISHED, 0, 0, 0
        )).isInstanceOf(NullPointerException.class).hasMessageContaining("id");
    }

    @Test
    void rejects_a_null_title() {
        assertThatThrownBy(() -> new VirtualCourse(
            CourseId.generate(), null, "d", "i", CourseCategory.of("tango"), CourseLevel.BEGINNER,
            false, CourseStatus.PUBLISHED, 0, 0, 0
        )).isInstanceOf(NullPointerException.class).hasMessageContaining("title");
    }

    @Test
    void rejects_a_negative_module_count() {
        assertThatThrownBy(() -> new VirtualCourse(
            CourseId.generate(), "t", "d", "i", CourseCategory.of("tango"), CourseLevel.BEGINNER,
            false, CourseStatus.PUBLISHED, -1, 0, 0
        )).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("moduleCount");
    }

    @Test
    void rejects_a_negative_lesson_count() {
        assertThatThrownBy(() -> new VirtualCourse(
            CourseId.generate(), "t", "d", "i", CourseCategory.of("tango"), CourseLevel.BEGINNER,
            false, CourseStatus.PUBLISHED, 0, -1, 0
        )).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("lessonCount");
    }

    @Test
    void rejects_a_negative_total_duration() {
        assertThatThrownBy(() -> new VirtualCourse(
            CourseId.generate(), "t", "d", "i", CourseCategory.of("tango"), CourseLevel.BEGINNER,
            false, CourseStatus.PUBLISHED, 0, 0, -1
        )).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("totalDurationMinutes");
    }

    @Test
    void is_published_reflects_the_status() {
        VirtualCourse published = published();
        VirtualCourse draft = new VirtualCourse(
            CourseId.generate(), "t", "d", "i", CourseCategory.of("tango"), CourseLevel.BEGINNER,
            false, CourseStatus.DRAFT, 0, 0, 0
        );

        assertThat(published.isPublished()).isTrue();
        assertThat(draft.isPublished()).isFalse();
    }

    @Test
    void exposes_every_field_via_its_getter() {
        VirtualCourse course = published();

        assertThat(course.getTitle()).isEqualTo("Tango Básico");
        assertThat(course.getShortDescription()).isEqualTo("Aprendé los pasos fundamentales");
        assertThat(course.getImageUrl()).isEqualTo("https://cdn/tango.jpg");
        assertThat(course.getCategory()).isEqualTo(CourseCategory.of("tango"));
        assertThat(course.getLevel()).isEqualTo(CourseLevel.BEGINNER);
        assertThat(course.isPremium()).isTrue();
        assertThat(course.getStatus()).isEqualTo(CourseStatus.PUBLISHED);
        assertThat(course.getModuleCount()).isEqualTo(5);
        assertThat(course.getLessonCount()).isEqualTo(20);
        assertThat(course.getTotalDurationMinutes()).isEqualTo(150);
    }
}
