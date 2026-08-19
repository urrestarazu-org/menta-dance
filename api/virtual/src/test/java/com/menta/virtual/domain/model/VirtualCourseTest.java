package com.menta.virtual.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class VirtualCourseTest {

    private static final UUID PROFESSOR_ID = UUID.randomUUID();

    private static VirtualCourse published() {
        return new VirtualCourse(
            CourseId.generate(), "Tango Básico", "Aprendé los pasos fundamentales", "Descripción larga",
            PROFESSOR_ID, "https://cdn/tango.jpg", CourseCategory.of("tango"), CourseLevel.BEGINNER,
            true, CourseStatus.PUBLISHED, 5, 20, 150
        );
    }

    @Test
    void rejects_a_null_id() {
        assertThatThrownBy(() -> new VirtualCourse(
            null, "t", "d", "desc", PROFESSOR_ID, "i", CourseCategory.of("tango"), CourseLevel.BEGINNER,
            false, CourseStatus.PUBLISHED, 0, 0, 0
        )).isInstanceOf(NullPointerException.class).hasMessageContaining("id");
    }

    @Test
    void rejects_a_null_title() {
        assertThatThrownBy(() -> new VirtualCourse(
            CourseId.generate(), null, "d", "desc", PROFESSOR_ID, "i", CourseCategory.of("tango"),
            CourseLevel.BEGINNER, false, CourseStatus.PUBLISHED, 0, 0, 0
        )).isInstanceOf(NullPointerException.class).hasMessageContaining("title");
    }

    @Test
    void rejects_a_null_professor_id() {
        assertThatThrownBy(() -> new VirtualCourse(
            CourseId.generate(), "t", "d", "desc", null, "i", CourseCategory.of("tango"), CourseLevel.BEGINNER,
            false, CourseStatus.PUBLISHED, 0, 0, 0
        )).isInstanceOf(NullPointerException.class).hasMessageContaining("professorId");
    }

    @Test
    void rejects_a_negative_module_count() {
        assertThatThrownBy(() -> new VirtualCourse(
            CourseId.generate(), "t", "d", "desc", PROFESSOR_ID, "i", CourseCategory.of("tango"),
            CourseLevel.BEGINNER, false, CourseStatus.PUBLISHED, -1, 0, 0
        )).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("moduleCount");
    }

    @Test
    void rejects_a_negative_lesson_count() {
        assertThatThrownBy(() -> new VirtualCourse(
            CourseId.generate(), "t", "d", "desc", PROFESSOR_ID, "i", CourseCategory.of("tango"),
            CourseLevel.BEGINNER, false, CourseStatus.PUBLISHED, 0, -1, 0
        )).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("lessonCount");
    }

    @Test
    void rejects_a_negative_total_duration() {
        assertThatThrownBy(() -> new VirtualCourse(
            CourseId.generate(), "t", "d", "desc", PROFESSOR_ID, "i", CourseCategory.of("tango"),
            CourseLevel.BEGINNER, false, CourseStatus.PUBLISHED, 0, 0, -1
        )).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("totalDurationMinutes");
    }

    @Test
    void is_published_reflects_the_status() {
        VirtualCourse published = published();
        VirtualCourse draft = new VirtualCourse(
            CourseId.generate(), "t", "d", "desc", PROFESSOR_ID, "i", CourseCategory.of("tango"),
            CourseLevel.BEGINNER, false, CourseStatus.DRAFT, 0, 0, 0
        );

        assertThat(published.isPublished()).isTrue();
        assertThat(draft.isPublished()).isFalse();
        assertThat(draft.isDraft()).isTrue();
        assertThat(published.isDraft()).isFalse();
    }

    @Test
    void exposes_every_field_via_its_getter() {
        VirtualCourse course = published();

        assertThat(course.getTitle()).isEqualTo("Tango Básico");
        assertThat(course.getShortDescription()).isEqualTo("Aprendé los pasos fundamentales");
        assertThat(course.getDescription()).isEqualTo("Descripción larga");
        assertThat(course.getProfessorId()).isEqualTo(PROFESSOR_ID);
        assertThat(course.getImageUrl()).isEqualTo("https://cdn/tango.jpg");
        assertThat(course.getCategory()).isEqualTo(CourseCategory.of("tango"));
        assertThat(course.getLevel()).isEqualTo(CourseLevel.BEGINNER);
        assertThat(course.isPremium()).isTrue();
        assertThat(course.getStatus()).isEqualTo(CourseStatus.PUBLISHED);
        assertThat(course.getModuleCount()).isEqualTo(5);
        assertThat(course.getLessonCount()).isEqualTo(20);
        assertThat(course.getTotalDurationMinutes()).isEqualTo(150);
    }

    @Test
    void is_owned_by_compares_the_professor_id() {
        VirtualCourse course = published();

        assertThat(course.isOwnedBy(PROFESSOR_ID)).isTrue();
        assertThat(course.isOwnedBy(UUID.randomUUID())).isFalse();
    }

    @Test
    void create_starts_as_draft_with_a_fresh_id_and_zero_content() {
        VirtualCourse course = VirtualCourse.create(
            "t", "short", "long", PROFESSOR_ID, "i", CourseCategory.of("salsa"), CourseLevel.ADVANCED
        );

        assertThat(course.getId()).isNotNull();
        assertThat(course.getStatus()).isEqualTo(CourseStatus.DRAFT);
        assertThat(course.isPremium()).isFalse();
        assertThat(course.getModuleCount()).isZero();
        assertThat(course.getLessonCount()).isZero();
        assertThat(course.getTotalDurationMinutes()).isZero();
    }

    @Test
    void with_methods_return_a_new_instance_with_only_that_field_changed() {
        VirtualCourse course = published();

        assertThat(course.withTitle("nuevo").getTitle()).isEqualTo("nuevo");
        assertThat(course.withShortDescription("nueva").getShortDescription()).isEqualTo("nueva");
        assertThat(course.withDescription("nueva larga").getDescription()).isEqualTo("nueva larga");
        assertThat(course.withImageUrl("nueva-url").getImageUrl()).isEqualTo("nueva-url");
        assertThat(course.withCategory(CourseCategory.of("bachata")).getCategory())
            .isEqualTo(CourseCategory.of("bachata"));
        assertThat(course.withLevel(CourseLevel.ADVANCED).getLevel()).isEqualTo(CourseLevel.ADVANCED);
        assertThat(course.withPremium(false).isPremium()).isFalse();
    }

    @Test
    void publish_and_unpublish_toggle_the_status() {
        VirtualCourse draft = VirtualCourse.create(
            "t", "short", "long", PROFESSOR_ID, "i", CourseCategory.of("salsa"), CourseLevel.ADVANCED
        );

        VirtualCourse publishedCourse = draft.publish();
        assertThat(publishedCourse.getStatus()).isEqualTo(CourseStatus.PUBLISHED);

        VirtualCourse unpublished = publishedCourse.unpublish();
        assertThat(unpublished.getStatus()).isEqualTo(CourseStatus.DRAFT);
    }
}
