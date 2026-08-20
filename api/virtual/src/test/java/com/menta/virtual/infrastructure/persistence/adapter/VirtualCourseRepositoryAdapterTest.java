package com.menta.virtual.infrastructure.persistence.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.menta.virtual.domain.model.CourseId;
import com.menta.virtual.domain.model.CourseStatus;
import com.menta.virtual.domain.model.VirtualCourse;
import com.menta.virtual.infrastructure.persistence.entity.VirtualCourseJpaEntity;
import com.menta.virtual.infrastructure.persistence.repository.LessonAggregateProjection;
import com.menta.virtual.infrastructure.persistence.repository.ModuleCountProjection;
import com.menta.virtual.infrastructure.persistence.repository.VirtualCourseJpaRepository;
import com.menta.virtual.infrastructure.persistence.repository.VirtualLessonJpaRepository;
import com.menta.virtual.infrastructure.persistence.repository.VirtualModuleJpaRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class VirtualCourseRepositoryAdapterTest {

    private final VirtualCourseJpaRepository courseRepository = mock(VirtualCourseJpaRepository.class);
    private final VirtualModuleJpaRepository moduleRepository = mock(VirtualModuleJpaRepository.class);
    private final VirtualLessonJpaRepository lessonRepository = mock(VirtualLessonJpaRepository.class);

    private final VirtualCourseRepositoryAdapter adapter =
        new VirtualCourseRepositoryAdapter(courseRepository, moduleRepository, lessonRepository);

    private static VirtualCourseJpaEntity aCourseEntity(UUID id) {
        return aCourseEntity(id, CourseStatus.PUBLISHED);
    }

    private static VirtualCourseJpaEntity aCourseEntity(UUID id, CourseStatus status) {
        Instant now = Instant.now();
        return new VirtualCourseJpaEntity(
            id, "Tango Básico", "desc", "descripción larga", UUID.randomUUID(), "https://cdn/img.jpg", "tango",
            "BEGINNER", false, status, now, now
        );
    }

    @Test
    void returns_an_empty_list_without_querying_aggregates_when_there_are_no_courses() {
        when(courseRepository.findByStatusAfterCursor(any(), any(), any())).thenReturn(List.of());

        List<VirtualCourse> result = adapter.findPublished(null, 10);

        assertThat(result).isEmpty();
    }

    @Test
    void joins_courses_with_their_module_and_lesson_aggregates_in_memory() {
        UUID withAggregates = UUID.randomUUID();
        UUID withoutAggregates = UUID.randomUUID();
        when(courseRepository.findByStatusAfterCursor(any(), any(), any()))
            .thenReturn(List.of(aCourseEntity(withAggregates), aCourseEntity(withoutAggregates)));

        ModuleCountProjection moduleCount = mock(ModuleCountProjection.class);
        when(moduleCount.getCourseId()).thenReturn(withAggregates);
        when(moduleCount.getModuleCount()).thenReturn(2L);
        when(moduleRepository.countByCourseIdIn(anyList())).thenReturn(List.of(moduleCount));

        LessonAggregateProjection lessonAggregate = mock(LessonAggregateProjection.class);
        when(lessonAggregate.getCourseId()).thenReturn(withAggregates);
        when(lessonAggregate.getLessonCount()).thenReturn(5L);
        when(lessonAggregate.getTotalDurationMinutes()).thenReturn(75L);
        when(lessonRepository.aggregateByCourseIdIn(anyList())).thenReturn(List.of(lessonAggregate));

        List<VirtualCourse> result = adapter.findPublished(CourseId.generate(), 10);

        VirtualCourse courseWithAggregates =
            result.stream().filter(c -> c.getId().getValue().equals(withAggregates)).findFirst().orElseThrow();
        assertThat(courseWithAggregates.getModuleCount()).isEqualTo(2);
        assertThat(courseWithAggregates.getLessonCount()).isEqualTo(5);
        assertThat(courseWithAggregates.getTotalDurationMinutes()).isEqualTo(75);

        VirtualCourse courseWithoutAggregates =
            result.stream().filter(c -> c.getId().getValue().equals(withoutAggregates)).findFirst().orElseThrow();
        assertThat(courseWithoutAggregates.getModuleCount()).isZero();
        assertThat(courseWithoutAggregates.getLessonCount()).isZero();
        assertThat(courseWithoutAggregates.getTotalDurationMinutes()).isZero();
    }

    @Test
    void find_published_by_id_returns_the_course_with_its_aggregates() {
        UUID id = UUID.randomUUID();
        when(courseRepository.findById(id)).thenReturn(Optional.of(aCourseEntity(id)));

        ModuleCountProjection moduleCount = mock(ModuleCountProjection.class);
        when(moduleCount.getCourseId()).thenReturn(id);
        when(moduleCount.getModuleCount()).thenReturn(3L);
        when(moduleRepository.countByCourseIdIn(List.of(id))).thenReturn(List.of(moduleCount));

        LessonAggregateProjection lessonAggregate = mock(LessonAggregateProjection.class);
        when(lessonAggregate.getCourseId()).thenReturn(id);
        when(lessonAggregate.getLessonCount()).thenReturn(9L);
        when(lessonAggregate.getTotalDurationMinutes()).thenReturn(120L);
        when(lessonRepository.aggregateByCourseIdIn(List.of(id))).thenReturn(List.of(lessonAggregate));

        Optional<VirtualCourse> result = adapter.findPublishedById(CourseId.of(id));

        assertThat(result).isPresent();
        assertThat(result.get().getModuleCount()).isEqualTo(3);
        assertThat(result.get().getLessonCount()).isEqualTo(9);
        assertThat(result.get().getTotalDurationMinutes()).isEqualTo(120);
    }

    @Test
    void find_published_by_id_returns_empty_when_the_course_does_not_exist() {
        UUID id = UUID.randomUUID();
        when(courseRepository.findById(id)).thenReturn(Optional.empty());

        assertThat(adapter.findPublishedById(CourseId.of(id))).isEmpty();
    }

    @Test
    void find_published_by_id_returns_empty_when_the_course_is_not_published() {
        UUID id = UUID.randomUUID();
        when(courseRepository.findById(id)).thenReturn(Optional.of(aCourseEntity(id, CourseStatus.DRAFT)));

        assertThat(adapter.findPublishedById(CourseId.of(id))).isEmpty();
    }

    @Test
    void find_by_id_is_unfiltered_by_status() {
        UUID id = UUID.randomUUID();
        when(courseRepository.findById(id)).thenReturn(Optional.of(aCourseEntity(id, CourseStatus.DRAFT)));

        Optional<VirtualCourse> result = adapter.findById(CourseId.of(id));

        assertThat(result).isPresent();
        assertThat(result.get().getStatus()).isEqualTo(CourseStatus.DRAFT);
    }

    @Test
    void find_all_maps_every_course_regardless_of_status() {
        when(courseRepository.findAll()).thenReturn(
            List.of(aCourseEntity(UUID.randomUUID(), CourseStatus.DRAFT), aCourseEntity(UUID.randomUUID()))
        );

        assertThat(adapter.findAll()).hasSize(2);
    }

    @Test
    void find_by_professor_id_maps_every_owned_course() {
        UUID professorId = UUID.randomUUID();
        when(courseRepository.findByProfessorId(professorId))
            .thenReturn(List.of(aCourseEntity(UUID.randomUUID(), CourseStatus.DRAFT)));

        assertThat(adapter.findByProfessorId(professorId)).hasSize(1);
    }

    @Test
    void save_preserves_created_at_of_an_existing_row_on_update() {
        UUID professorId = UUID.randomUUID();
        VirtualCourse course = VirtualCourse.create(
            "t", "s", "d", professorId, "i", com.menta.virtual.domain.model.CourseCategory.of("tango"),
            com.menta.virtual.domain.model.CourseLevel.BEGINNER
        );
        Instant originalCreatedAt = Instant.now().minusSeconds(3600);
        when(courseRepository.findById(course.getId().getValue()))
            .thenReturn(Optional.of(new VirtualCourseJpaEntity(
                course.getId().getValue(), "old", "s", "d", professorId, "i", "tango", "BEGINNER", false,
                CourseStatus.DRAFT, originalCreatedAt, originalCreatedAt
            )));
        when(courseRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        adapter.save(course);

        org.mockito.ArgumentCaptor<VirtualCourseJpaEntity> captor =
            org.mockito.ArgumentCaptor.forClass(VirtualCourseJpaEntity.class);
        org.mockito.Mockito.verify(courseRepository).save(captor.capture());
        assertThat(captor.getValue().getCreatedAt()).isEqualTo(originalCreatedAt);
    }

    @Test
    void save_uses_now_as_created_at_for_a_brand_new_course() {
        UUID professorId = UUID.randomUUID();
        VirtualCourse course = VirtualCourse.create(
            "t", "s", "d", professorId, "i", com.menta.virtual.domain.model.CourseCategory.of("tango"),
            com.menta.virtual.domain.model.CourseLevel.BEGINNER
        );
        when(courseRepository.findById(course.getId().getValue())).thenReturn(Optional.empty());
        when(courseRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        VirtualCourse saved = adapter.save(course);

        assertThat(saved.getId()).isEqualTo(course.getId());
    }

    @Test
    void delete_delegates_to_the_jpa_repository() {
        UUID id = UUID.randomUUID();

        adapter.delete(CourseId.of(id));

        org.mockito.Mockito.verify(courseRepository).deleteById(id);
    }
}
