package com.menta.virtual.infrastructure.e2e;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.menta.virtual.domain.model.CourseStatus;
import com.menta.virtual.infrastructure.persistence.entity.VirtualCourseJpaEntity;
import com.menta.virtual.infrastructure.persistence.entity.VirtualLessonJpaEntity;
import com.menta.virtual.infrastructure.persistence.entity.VirtualModuleJpaEntity;
import com.menta.virtual.infrastructure.persistence.repository.VirtualCourseJpaRepository;
import com.menta.virtual.infrastructure.persistence.repository.VirtualLessonJpaRepository;
import com.menta.virtual.infrastructure.persistence.repository.VirtualModuleJpaRepository;
import org.junit.jupiter.api.Test;

class E2eBunnyNetVirtualFixtureTest {

    @Test
    void seeds_the_unplanned_course_with_a_preview_and_a_protected_module_when_absent() {
        VirtualCourseJpaRepository courses = mock(VirtualCourseJpaRepository.class);
        VirtualModuleJpaRepository modules = mock(VirtualModuleJpaRepository.class);
        VirtualLessonJpaRepository lessons = mock(VirtualLessonJpaRepository.class);
        when(courses.existsById(any())).thenReturn(false);

        new E2eBunnyNetVirtualFixture(courses, modules, lessons).run(mock());

        verify(courses).save(argThat((VirtualCourseJpaEntity course) ->
            course.getId().equals(E2eBunnyNetVirtualFixture.UNPLANNED_COURSE_ID)
                && course.getStatus() == CourseStatus.PUBLISHED));
        verify(modules).save(argThat((VirtualModuleJpaEntity module) ->
            module.getCourseId().equals(E2eBunnyNetVirtualFixture.UNPLANNED_COURSE_ID)
                && module.isPreview()));
        verify(modules).save(argThat((VirtualModuleJpaEntity module) ->
            module.getCourseId().equals(E2eBunnyNetVirtualFixture.UNPLANNED_COURSE_ID)
                && !module.isPreview()));
        verify(lessons, times(2)).save(argThat((VirtualLessonJpaEntity lesson) ->
            lesson.getCourseId().equals(E2eBunnyNetVirtualFixture.UNPLANNED_COURSE_ID)
                && lesson.getVideoId() != null));
    }

    @Test
    void seeds_the_planned_course_with_only_a_protected_module_when_absent() {
        VirtualCourseJpaRepository courses = mock(VirtualCourseJpaRepository.class);
        VirtualModuleJpaRepository modules = mock(VirtualModuleJpaRepository.class);
        VirtualLessonJpaRepository lessons = mock(VirtualLessonJpaRepository.class);
        when(courses.existsById(any())).thenReturn(false);

        new E2eBunnyNetVirtualFixture(courses, modules, lessons).run(mock());

        verify(courses).save(argThat((VirtualCourseJpaEntity course) ->
            course.getId().equals(E2eBunnyNetVirtualFixture.PLANNED_COURSE_ID)
                && course.getStatus() == CourseStatus.PUBLISHED));
        verify(modules, times(1)).save(argThat((VirtualModuleJpaEntity module) ->
            module.getCourseId().equals(E2eBunnyNetVirtualFixture.PLANNED_COURSE_ID)));
        verify(modules).save(argThat((VirtualModuleJpaEntity module) ->
            module.getCourseId().equals(E2eBunnyNetVirtualFixture.PLANNED_COURSE_ID)
                && !module.isPreview()));
        verify(lessons, times(1)).save(argThat((VirtualLessonJpaEntity lesson) ->
            lesson.getCourseId().equals(E2eBunnyNetVirtualFixture.PLANNED_COURSE_ID)
                && lesson.getVideoId() != null));
    }

    @Test
    void does_not_duplicate_existing_fixture_courses() {
        VirtualCourseJpaRepository courses = mock(VirtualCourseJpaRepository.class);
        VirtualModuleJpaRepository modules = mock(VirtualModuleJpaRepository.class);
        VirtualLessonJpaRepository lessons = mock(VirtualLessonJpaRepository.class);
        when(courses.existsById(E2eBunnyNetVirtualFixture.UNPLANNED_COURSE_ID)).thenReturn(true);
        when(courses.existsById(E2eBunnyNetVirtualFixture.PLANNED_COURSE_ID)).thenReturn(true);

        new E2eBunnyNetVirtualFixture(courses, modules, lessons).run(mock());

        verify(courses, never()).save(any());
        verify(modules, never()).save(any());
        verify(lessons, never()).save(any());
    }
}
