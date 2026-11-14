package com.menta.virtual.infrastructure.demo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.menta.shared.demo.DemoIds;
import com.menta.shared.demo.DemoSeedOrder;
import com.menta.virtual.domain.model.CourseStatus;
import com.menta.virtual.infrastructure.cdn.BunnyNetProperties;
import com.menta.virtual.infrastructure.cdn.local.LocalBunnyNetSignatureService;
import com.menta.virtual.infrastructure.persistence.entity.VirtualCourseJpaEntity;
import com.menta.virtual.infrastructure.persistence.entity.VirtualLessonJpaEntity;
import com.menta.virtual.infrastructure.persistence.entity.VirtualModuleJpaEntity;
import com.menta.virtual.infrastructure.persistence.repository.VirtualCourseJpaRepository;
import com.menta.virtual.infrastructure.persistence.repository.VirtualLessonJpaRepository;
import com.menta.virtual.infrastructure.persistence.repository.VirtualModuleJpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class DemoVirtualCatalogFixtureTest {

    private final VirtualCourseJpaRepository courses = mock(VirtualCourseJpaRepository.class);
    private final VirtualModuleJpaRepository modules = mock(VirtualModuleJpaRepository.class);
    private final VirtualLessonJpaRepository lessons = mock(VirtualLessonJpaRepository.class);
    private final DemoVirtualCatalogFixture fixture = new DemoVirtualCatalogFixture(courses, modules, lessons, "");

    @Test
    void first_run_saves_two_published_courses_owned_by_the_instructor() {
        fixture.run(mock());

        ArgumentCaptor<VirtualCourseJpaEntity> captor = ArgumentCaptor.forClass(VirtualCourseJpaEntity.class);
        verify(courses, times(2)).save(captor.capture());
        List<VirtualCourseJpaEntity> saved = captor.getAllValues();
        assertThat(saved).extracting(VirtualCourseJpaEntity::getId)
            .containsExactly(DemoIds.VIRTUAL_SENSUAL_COURSE_ID, DemoIds.VIRTUAL_TRADITIONAL_COURSE_ID);
        assertThat(saved).extracting(VirtualCourseJpaEntity::getTitle)
            .containsExactly("Bachata Sensual", "Bachata Tradicional");
        assertThat(saved).allSatisfy(course -> {
            assertThat(course.getStatus()).isEqualTo(CourseStatus.PUBLISHED);
            assertThat(course.getProfessorId()).isEqualTo(DemoIds.INSTRUCTOR_USER_ID);
        });
    }

    @Test
    void first_run_saves_a_preview_module_with_a_free_lesson_and_a_restricted_module_per_course() {
        fixture.run(mock());

        ArgumentCaptor<VirtualModuleJpaEntity> moduleCaptor = ArgumentCaptor.forClass(VirtualModuleJpaEntity.class);
        verify(modules, times(4)).save(moduleCaptor.capture());
        ArgumentCaptor<VirtualLessonJpaEntity> lessonCaptor = ArgumentCaptor.forClass(VirtualLessonJpaEntity.class);
        verify(lessons, times(4)).save(lessonCaptor.capture());
        List<VirtualModuleJpaEntity> savedModules = moduleCaptor.getAllValues();
        List<VirtualLessonJpaEntity> savedLessons = lessonCaptor.getAllValues();

        for (UUID courseId : List.of(DemoIds.VIRTUAL_SENSUAL_COURSE_ID, DemoIds.VIRTUAL_TRADITIONAL_COURSE_ID)) {
            List<VirtualModuleJpaEntity> courseModules =
                savedModules.stream().filter(module -> module.getCourseId().equals(courseId)).toList();
            assertThat(courseModules).extracting(VirtualModuleJpaEntity::isPreview).containsExactly(true, false);
            List<VirtualLessonJpaEntity> courseLessons =
                savedLessons.stream().filter(lesson -> lesson.getCourseId().equals(courseId)).toList();
            assertThat(courseLessons).extracting(VirtualLessonJpaEntity::isFree).containsExactly(true, false);
            assertThat(courseLessons).extracting(VirtualLessonJpaEntity::getModuleId)
                .containsExactly(courseModules.get(0).getId(), courseModules.get(1).getId());
        }
        assertThat(savedModules).extracting(VirtualModuleJpaEntity::getId).doesNotHaveDuplicates();
        assertThat(savedLessons).extracting(VirtualLessonJpaEntity::getId).doesNotHaveDuplicates();
    }

    @Test
    void seeded_video_references_resolve_with_the_local_adapter() {
        fixture.run(mock());

        ArgumentCaptor<VirtualLessonJpaEntity> captor = ArgumentCaptor.forClass(VirtualLessonJpaEntity.class);
        verify(lessons, times(4)).save(captor.capture());
        BunnyNetProperties properties = new BunnyNetProperties();
        properties.setPullZoneHostname("vz-demo.b-cdn.net");
        properties.setVideoLibraryId("1");
        LocalBunnyNetSignatureService signer = new LocalBunnyNetSignatureService(properties);
        assertThat(captor.getAllValues()).allSatisfy(lesson -> {
            assertThat(lesson.getVideoId()).isNotBlank();
            assertThat(signer.generateSignedUrl(lesson.getVideoId(), 1_000L)).contains(lesson.getVideoId());
        });
    }

    @Test
    void first_run_without_a_configured_video_id_keeps_the_label_references_for_every_lesson() {
        fixture.run(mock());

        ArgumentCaptor<VirtualLessonJpaEntity> captor = ArgumentCaptor.forClass(VirtualLessonJpaEntity.class);
        verify(lessons, times(4)).save(captor.capture());
        assertThat(captor.getAllValues()).extracting(VirtualLessonJpaEntity::getVideoId)
            .containsExactly("demo-sensual-free", "demo-sensual-restricted",
                "demo-traditional-free", "demo-traditional-restricted");
    }

    @Test
    void first_run_with_a_configured_video_id_uses_it_for_the_free_lessons_only() {
        DemoVirtualCatalogFixture configured = new DemoVirtualCatalogFixture(courses, modules, lessons, " vid-123 ");

        configured.run(mock());

        ArgumentCaptor<VirtualLessonJpaEntity> captor = ArgumentCaptor.forClass(VirtualLessonJpaEntity.class);
        verify(lessons, times(4)).save(captor.capture());
        assertThat(captor.getAllValues()).filteredOn(VirtualLessonJpaEntity::isFree)
            .hasSize(2).extracting(VirtualLessonJpaEntity::getVideoId).containsOnly("vid-123");
        assertThat(captor.getAllValues()).filteredOn(lesson -> !lesson.isFree())
            .extracting(VirtualLessonJpaEntity::getVideoId)
            .containsExactly("demo-sensual-restricted", "demo-traditional-restricted");
    }

    @Test
    void existing_free_lessons_get_the_configured_video_id_when_it_differs() {
        allExist();
        UUID sensualFree = UUID.fromString("de000000-0000-4000-8000-0000000002a1");
        UUID traditionalFree = UUID.fromString("de000000-0000-4000-8000-0000000002b1");
        VirtualLessonJpaEntity first = existingFreeLesson(sensualFree, "demo-sensual-free");
        VirtualLessonJpaEntity second = existingFreeLesson(traditionalFree, "demo-traditional-free");
        when(lessons.findById(sensualFree)).thenReturn(Optional.of(first));
        when(lessons.findById(traditionalFree)).thenReturn(Optional.of(second));

        new DemoVirtualCatalogFixture(courses, modules, lessons, "vid-123").run(mock());

        verify(lessons).save(first);
        verify(lessons).save(second);
        verify(lessons, times(2)).save(any());
        assertThat(first.getVideoId()).isEqualTo("vid-123");
        assertThat(second.getVideoId()).isEqualTo("vid-123");
        assertThat(first.getTitle()).isEqualTo("Basic steps (free)");
        verify(courses, never()).save(any());
        verify(modules, never()).save(any());
    }

    @Test
    void existing_free_lessons_already_pointing_at_the_configured_video_id_are_left_alone() {
        allExist();
        UUID sensualFree = UUID.fromString("de000000-0000-4000-8000-0000000002a1");
        UUID traditionalFree = UUID.fromString("de000000-0000-4000-8000-0000000002b1");
        when(lessons.findById(sensualFree)).thenReturn(Optional.of(existingFreeLesson(sensualFree, "vid-123")));
        when(lessons.findById(traditionalFree))
            .thenReturn(Optional.of(existingFreeLesson(traditionalFree, "vid-123")));

        new DemoVirtualCatalogFixture(courses, modules, lessons, "vid-123").run(mock());

        verify(lessons, never()).save(any());
    }

    @Test
    void without_a_configured_video_id_existing_lessons_are_never_loaded_or_touched() {
        allExist();

        fixture.run(mock());

        verify(lessons, never()).findById(any());
        verify(lessons, never()).save(any());
    }

    @Test
    void a_configured_video_id_never_touches_restricted_lessons() {
        allExist();

        new DemoVirtualCatalogFixture(courses, modules, lessons, "vid-123").run(mock());

        verify(lessons, never()).findById(UUID.fromString("de000000-0000-4000-8000-0000000002a2"));
        verify(lessons, never()).findById(UUID.fromString("de000000-0000-4000-8000-0000000002b2"));
    }

    private void allExist() {
        when(courses.existsById(any())).thenReturn(true);
        when(modules.existsById(any())).thenReturn(true);
        when(lessons.existsById(any())).thenReturn(true);
    }

    private static VirtualLessonJpaEntity existingFreeLesson(UUID id, String videoId) {
        return new VirtualLessonJpaEntity(
            id, UUID.randomUUID(), UUID.randomUUID(), "Basic steps (free)", "Demo lesson.", videoId, 5, true, 0);
    }

    @Test
    void second_run_saves_nothing_when_everything_exists() {
        when(courses.existsById(any())).thenReturn(true);
        when(modules.existsById(any())).thenReturn(true);
        when(lessons.existsById(any())).thenReturn(true);

        fixture.run(mock());

        verify(courses, never()).save(any());
        verify(modules, never()).save(any());
        verify(lessons, never()).save(any());
    }

    @Test
    void partial_database_creates_only_the_missing_entities() {
        when(courses.existsById(any())).thenReturn(true);
        when(modules.existsById(any())).thenReturn(true);
        when(lessons.existsById(any())).thenReturn(true);
        when(courses.existsById(DemoIds.VIRTUAL_TRADITIONAL_COURSE_ID)).thenReturn(false);

        fixture.run(mock());

        verify(courses, times(1)).save(any());
        verify(modules, never()).save(any());
        verify(lessons, never()).save(any());
    }

    @Test
    void order_is_the_shared_content_order() {
        assertThat(fixture.getOrder()).isEqualTo(DemoSeedOrder.CONTENT);
    }
}
