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
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class DemoVirtualCatalogFixtureTest {

    private final VirtualCourseJpaRepository courses = mock(VirtualCourseJpaRepository.class);
    private final VirtualModuleJpaRepository modules = mock(VirtualModuleJpaRepository.class);
    private final VirtualLessonJpaRepository lessons = mock(VirtualLessonJpaRepository.class);
    private final DemoVirtualCatalogFixture fixture = new DemoVirtualCatalogFixture(courses, modules, lessons);

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
