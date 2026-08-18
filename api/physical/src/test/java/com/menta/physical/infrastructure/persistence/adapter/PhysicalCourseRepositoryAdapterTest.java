package com.menta.physical.infrastructure.persistence.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.menta.physical.domain.model.CourseId;
import com.menta.physical.domain.model.CourseStatus;
import com.menta.physical.domain.model.PhysicalCourse;
import com.menta.physical.infrastructure.persistence.entity.PhysicalCourseJpaEntity;
import com.menta.physical.infrastructure.persistence.repository.PhysicalCourseJpaRepository;
import java.time.Instant;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PhysicalCourseRepositoryAdapterTest {

    private final PhysicalCourseJpaRepository courseRepository = mock(PhysicalCourseJpaRepository.class);
    private final PhysicalCourseRepositoryAdapter adapter = new PhysicalCourseRepositoryAdapter(courseRepository);

    @Test
    void returns_an_empty_list_when_there_are_no_courses() {
        when(courseRepository.findByStatusAfterCursor(any(), any(), any())).thenReturn(List.of());

        assertThat(adapter.findActive(null, 10)).isEmpty();
    }

    @Test
    void maps_active_courses_ordered_by_cursor() {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        PhysicalCourseJpaEntity entity = new PhysicalCourseJpaEntity(
            id, "Salsa inicial", "María García", "TUESDAY", LocalTime.of(19, 0),
            "BEGINNER", 20, CourseStatus.ACTIVE, now, now
        );
        when(courseRepository.findByStatusAfterCursor(any(), any(), any())).thenReturn(List.of(entity));

        List<PhysicalCourse> result = adapter.findActive(CourseId.generate(), 10);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getId().getValue()).isEqualTo(id);
    }

    @Test
    void find_active_by_id_returns_the_course_when_active() {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        PhysicalCourseJpaEntity entity = new PhysicalCourseJpaEntity(
            id, "Salsa inicial", "María García", "TUESDAY", LocalTime.of(19, 0),
            "BEGINNER", 20, CourseStatus.ACTIVE, now, now
        );
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
        Instant now = Instant.now();
        PhysicalCourseJpaEntity entity = new PhysicalCourseJpaEntity(
            id, "Salsa inicial", "María García", "TUESDAY", LocalTime.of(19, 0),
            "BEGINNER", 20, CourseStatus.INACTIVE, now, now
        );
        when(courseRepository.findById(id)).thenReturn(Optional.of(entity));

        assertThat(adapter.findActiveById(CourseId.of(id))).isEmpty();
    }
}
