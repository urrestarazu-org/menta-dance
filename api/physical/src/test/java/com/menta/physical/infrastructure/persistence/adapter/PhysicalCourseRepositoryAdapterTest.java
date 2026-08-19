package com.menta.physical.infrastructure.persistence.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.menta.physical.domain.model.CourseId;
import com.menta.physical.domain.model.CourseStatus;
import com.menta.physical.domain.model.PhysicalCourse;
import com.menta.physical.domain.model.PhysicalCourseLevel;
import com.menta.physical.infrastructure.persistence.entity.PhysicalCourseJpaEntity;
import com.menta.physical.infrastructure.persistence.repository.PhysicalCourseJpaRepository;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class PhysicalCourseRepositoryAdapterTest {

    private final PhysicalCourseJpaRepository courseRepository = mock(PhysicalCourseJpaRepository.class);
    private final PhysicalCourseRepositoryAdapter adapter = new PhysicalCourseRepositoryAdapter(courseRepository);

    private static PhysicalCourseJpaEntity entity(UUID id, CourseStatus status, Instant now) {
        return new PhysicalCourseJpaEntity(
            id, "Salsa inicial", "Curso de salsa", UUID.randomUUID(), "María García", "TUESDAY",
            LocalTime.of(19, 0), 60, "BEGINNER", 20, status, now, now
        );
    }

    @Test
    void returns_an_empty_list_when_there_are_no_courses() {
        when(courseRepository.findByStatusAfterCursor(any(), any(), any())).thenReturn(List.of());

        assertThat(adapter.findActive(null, 10)).isEmpty();
    }

    @Test
    void maps_active_courses_ordered_by_cursor() {
        UUID id = UUID.randomUUID();
        PhysicalCourseJpaEntity entity = entity(id, CourseStatus.ACTIVE, Instant.now());
        when(courseRepository.findByStatusAfterCursor(any(), any(), any())).thenReturn(List.of(entity));

        List<PhysicalCourse> result = adapter.findActive(CourseId.generate(), 10);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getId().getValue()).isEqualTo(id);
    }

    @Test
    void find_active_by_id_returns_the_course_when_active() {
        UUID id = UUID.randomUUID();
        PhysicalCourseJpaEntity entity = entity(id, CourseStatus.ACTIVE, Instant.now());
        when(courseRepository.findById(id)).thenReturn(Optional.of(entity));

        Optional<PhysicalCourse> result = adapter.findActiveById(CourseId.of(id));

        assertThat(result).isPresent();
        assertThat(result.get().getId().getValue()).isEqualTo(id);
    }

    @Test
    void find_active_by_id_returns_empty_when_the_course_does_not_exist() {
        UUID id = UUID.randomUUID();
        when(courseRepository.findById(id)).thenReturn(Optional.empty());

        assertThat(adapter.findActiveById(CourseId.of(id))).isEmpty();
    }

    @Test
    void find_active_by_id_returns_empty_when_the_course_is_inactive() {
        UUID id = UUID.randomUUID();
        PhysicalCourseJpaEntity entity = entity(id, CourseStatus.INACTIVE, Instant.now());
        when(courseRepository.findById(id)).thenReturn(Optional.of(entity));

        assertThat(adapter.findActiveById(CourseId.of(id))).isEmpty();
    }

    @Test
    void find_by_id_returns_a_course_regardless_of_status() {
        UUID id = UUID.randomUUID();
        PhysicalCourseJpaEntity entity = entity(id, CourseStatus.INACTIVE, Instant.now());
        when(courseRepository.findById(id)).thenReturn(Optional.of(entity));

        assertThat(adapter.findById(CourseId.of(id))).isPresent();
    }

    @Test
    void find_by_id_returns_empty_when_the_course_does_not_exist() {
        UUID id = UUID.randomUUID();
        when(courseRepository.findById(id)).thenReturn(Optional.empty());

        assertThat(adapter.findById(CourseId.of(id))).isEmpty();
    }

    @Test
    void find_all_maps_every_course() {
        PhysicalCourseJpaEntity entity = entity(UUID.randomUUID(), CourseStatus.ACTIVE, Instant.now());
        when(courseRepository.findAll()).thenReturn(List.of(entity));

        assertThat(adapter.findAll()).hasSize(1);
    }

    @Test
    void find_by_professor_id_maps_every_course_owned_by_that_professor() {
        UUID professorId = UUID.randomUUID();
        PhysicalCourseJpaEntity entity = entity(UUID.randomUUID(), CourseStatus.ACTIVE, Instant.now());
        when(courseRepository.findByProfessorId(professorId)).thenReturn(List.of(entity));

        assertThat(adapter.findByProfessorId(professorId)).hasSize(1);
    }

    @Test
    void save_preserves_the_existing_row_s_created_at_on_update() {
        UUID id = UUID.randomUUID();
        Instant originalCreatedAt = Instant.now().minusSeconds(3600);
        PhysicalCourseJpaEntity existing = entity(id, CourseStatus.ACTIVE, originalCreatedAt);
        when(courseRepository.findById(id)).thenReturn(Optional.of(existing));
        when(courseRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        PhysicalCourse course = new PhysicalCourse(
            CourseId.of(id), "Salsa avanzada", "desc", UUID.randomUUID(), "María García",
            DayOfWeek.TUESDAY, LocalTime.of(19, 0), 60, PhysicalCourseLevel.BEGINNER, 20, CourseStatus.ACTIVE
        );

        adapter.save(course);

        ArgumentCaptor<PhysicalCourseJpaEntity> captor = ArgumentCaptor.forClass(PhysicalCourseJpaEntity.class);
        verify(courseRepository).save(captor.capture());
        assertThat(captor.getValue().getCreatedAt()).isEqualTo(originalCreatedAt);
    }

    @Test
    void save_uses_now_as_created_at_for_a_brand_new_course() {
        when(courseRepository.findById(any())).thenReturn(Optional.empty());
        when(courseRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        PhysicalCourse course = PhysicalCourse.create(
            "Salsa inicial", "desc", UUID.randomUUID(), "María García", DayOfWeek.TUESDAY,
            LocalTime.of(19, 0), 60, PhysicalCourseLevel.BEGINNER, 20
        );

        PhysicalCourse saved = adapter.save(course);

        assertThat(saved.getId()).isEqualTo(course.getId());
    }
}
