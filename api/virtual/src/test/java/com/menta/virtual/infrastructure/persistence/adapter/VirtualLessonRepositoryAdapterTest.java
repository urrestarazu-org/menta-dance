package com.menta.virtual.infrastructure.persistence.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.menta.virtual.domain.model.CourseId;
import com.menta.virtual.domain.model.LessonId;
import com.menta.virtual.domain.model.ModuleId;
import com.menta.virtual.domain.model.VirtualLesson;
import com.menta.virtual.infrastructure.persistence.entity.VirtualLessonJpaEntity;
import com.menta.virtual.infrastructure.persistence.repository.VirtualLessonJpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class VirtualLessonRepositoryAdapterTest {

    private final VirtualLessonJpaRepository jpaRepository = mock(VirtualLessonJpaRepository.class);
    private final VirtualLessonRepositoryAdapter adapter = new VirtualLessonRepositoryAdapter(jpaRepository);

    private static VirtualLessonJpaEntity entity(UUID id, UUID moduleId, UUID courseId) {
        return new VirtualLessonJpaEntity(id, moduleId, courseId, "L1", "d", "v", 10, false, 0);
    }

    @Test
    void find_by_id_maps_the_entity() {
        UUID id = UUID.randomUUID();
        UUID moduleId = UUID.randomUUID();
        UUID courseId = UUID.randomUUID();
        when(jpaRepository.findById(id)).thenReturn(Optional.of(entity(id, moduleId, courseId)));

        assertThat(adapter.findById(LessonId.of(id))).isPresent();
    }

    @Test
    void find_by_id_returns_empty_when_missing() {
        UUID id = UUID.randomUUID();
        when(jpaRepository.findById(id)).thenReturn(Optional.empty());

        assertThat(adapter.findById(LessonId.of(id))).isEmpty();
    }

    @Test
    void find_by_module_id_returns_ordered_lessons() {
        UUID moduleId = UUID.randomUUID();
        UUID courseId = UUID.randomUUID();
        when(jpaRepository.findByModuleIdOrderByDisplayOrderAsc(moduleId))
            .thenReturn(List.of(entity(UUID.randomUUID(), moduleId, courseId)));

        assertThat(adapter.findByModuleId(ModuleId.of(moduleId))).hasSize(1);
    }

    @Test
    void find_by_course_id_returns_every_lesson_of_the_course() {
        UUID moduleId = UUID.randomUUID();
        UUID courseId = UUID.randomUUID();
        when(jpaRepository.findByCourseId(courseId)).thenReturn(List.of(entity(UUID.randomUUID(), moduleId, courseId)));

        assertThat(adapter.findByCourseId(CourseId.of(courseId))).hasSize(1);
    }

    @Test
    void count_by_module_id_delegates_to_the_repository() {
        UUID moduleId = UUID.randomUUID();
        when(jpaRepository.countByModuleId(moduleId)).thenReturn(3L);

        assertThat(adapter.countByModuleId(ModuleId.of(moduleId))).isEqualTo(3);
    }

    @Test
    void save_delegates_and_maps_back() {
        VirtualLesson lesson = VirtualLesson.create(ModuleId.generate(), CourseId.generate(), "L1", "d", "v", 10, false, 0);
        when(jpaRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        assertThat(adapter.save(lesson).getTitle()).isEqualTo("L1");
    }
}
