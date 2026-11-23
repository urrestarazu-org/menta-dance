package com.menta.virtual.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class VirtualLessonTest {

    private static VirtualLesson lesson(String videoId) {
        return VirtualLesson.create(ModuleId.generate(), CourseId.generate(), "t", "d", videoId, 10, false, 0);
    }

    @Test
    void rejects_a_negative_duration() {
        assertThatThrownBy(() -> new VirtualLesson(
            LessonId.generate(), ModuleId.generate(), CourseId.generate(), "t", "d", "v", -1, false, 0
        )).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("durationMinutes");
    }

    @Test
    void rejects_a_negative_order() {
        assertThatThrownBy(() -> new VirtualLesson(
            LessonId.generate(), ModuleId.generate(), CourseId.generate(), "t", "d", "v", 10, false, -1
        )).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("order");
    }

    private static VirtualLesson hydrated(String videoId) {
        return new VirtualLesson(
            LessonId.generate(), ModuleId.generate(), CourseId.generate(), "t", "d", videoId, 10, false, 0
        );
    }

    @Test
    void is_complete_requires_a_non_blank_video_id() {
        assertThat(lesson("bunny-123").isComplete()).isTrue();
        assertThat(lesson(null).isComplete()).isFalse();
        assertThat(hydrated("").isComplete()).isFalse();
        assertThat(hydrated("   ").isComplete()).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "\t\n"})
    void create_rejects_a_blank_video_id(String blank) {
        assertThatThrownBy(() -> lesson(blank))
            .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("videoId");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "\t\n"})
    void with_video_id_rejects_a_blank_video_id_instead_of_clearing_the_video(String blank) {
        VirtualLesson lesson = lesson("bunny-123");

        assertThatThrownBy(() -> lesson.withVideoId(blank))
            .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("videoId");
        assertThat(lesson.getVideoId()).isEqualTo("bunny-123");
    }

    @Test
    void a_stored_lesson_with_a_blank_or_missing_video_id_still_hydrates_and_can_be_edited() {
        VirtualLesson blank = hydrated("");
        VirtualLesson missing = hydrated(null);

        assertThat(blank.getVideoId()).isEmpty();
        assertThat(blank.withTitle("nuevo").getVideoId()).isEmpty();
        assertThat(missing.withOrder(3).getVideoId()).isNull();
        assertThat(blank.withVideoId("bunny-123").isComplete()).isTrue();
    }

    @Test
    void create_generates_a_fresh_id() {
        ModuleId moduleId = ModuleId.generate();
        CourseId courseId = CourseId.generate();

        VirtualLesson created = VirtualLesson.create(moduleId, courseId, "t", "d", "v", 10, true, 2);

        assertThat(created.getId()).isNotNull();
        assertThat(created.getModuleId()).isEqualTo(moduleId);
        assertThat(created.getCourseId()).isEqualTo(courseId);
        assertThat(created.isFree()).isTrue();
        assertThat(created.getOrder()).isEqualTo(2);
    }

    @Test
    void with_methods_return_new_instances_with_only_that_field_changed() {
        VirtualLesson lesson = lesson("bunny-123");

        assertThat(lesson.withTitle("nuevo").getTitle()).isEqualTo("nuevo");
        assertThat(lesson.withDescription("nueva").getDescription()).isEqualTo("nueva");
        assertThat(lesson.withVideoId("otro-video").getVideoId()).isEqualTo("otro-video");
        assertThat(lesson.withDurationMinutes(20).getDurationMinutes()).isEqualTo(20);
        assertThat(lesson.withFree(true).isFree()).isTrue();
        assertThat(lesson.withOrder(5).getOrder()).isEqualTo(5);
    }
}
