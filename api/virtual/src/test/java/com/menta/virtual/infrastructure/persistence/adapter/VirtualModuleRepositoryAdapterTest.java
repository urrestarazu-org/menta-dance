package com.menta.virtual.infrastructure.persistence.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.menta.virtual.domain.model.CourseId;
import com.menta.virtual.domain.model.ModuleId;
import com.menta.virtual.domain.model.VirtualModule;
import com.menta.virtual.infrastructure.persistence.entity.VirtualModuleJpaEntity;
import com.menta.virtual.infrastructure.persistence.repository.VirtualModuleJpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class VirtualModuleRepositoryAdapterTest {

    private final VirtualModuleJpaRepository jpaRepository = mock(VirtualModuleJpaRepository.class);
    private final VirtualModuleRepositoryAdapter adapter = new VirtualModuleRepositoryAdapter(jpaRepository);

    @Test
    void find_by_id_maps_the_entity() {
        UUID id = UUID.randomUUID();
        UUID courseId = UUID.randomUUID();
        when(jpaRepository.findById(id)).thenReturn(Optional.of(new VirtualModuleJpaEntity(id, courseId, "M1", 0)));

        Optional<VirtualModule> result = adapter.findById(ModuleId.of(id));

        assertThat(result).isPresent();
        assertThat(result.get().getTitle()).isEqualTo("M1");
    }

    @Test
    void find_by_id_returns_empty_when_missing() {
        UUID id = UUID.randomUUID();
        when(jpaRepository.findById(id)).thenReturn(Optional.empty());

        assertThat(adapter.findById(ModuleId.of(id))).isEmpty();
    }

    @Test
    void find_by_course_id_returns_ordered_modules() {
        UUID courseId = UUID.randomUUID();
        when(jpaRepository.findByCourseIdOrderByDisplayOrderAsc(courseId)).thenReturn(List.of(
            new VirtualModuleJpaEntity(UUID.randomUUID(), courseId, "M1", 0)
        ));

        assertThat(adapter.findByCourseId(CourseId.of(courseId))).hasSize(1);
    }

    @Test
    void count_by_course_id_delegates_to_the_repository() {
        UUID courseId = UUID.randomUUID();
        when(jpaRepository.countByCourseId(courseId)).thenReturn(4L);

        assertThat(adapter.countByCourseId(CourseId.of(courseId))).isEqualTo(4);
    }

    @Test
    void save_delegates_and_maps_back() {
        VirtualModule module = VirtualModule.create(CourseId.generate(), "M1", 0);
        when(jpaRepository.save(org.mockito.ArgumentMatchers.any())).thenAnswer(inv -> inv.getArgument(0));

        VirtualModule saved = adapter.save(module);

        assertThat(saved.getTitle()).isEqualTo("M1");
    }

    @Test
    void save_all_delegates_to_the_repository() {
        VirtualModule module = VirtualModule.create(CourseId.generate(), "M1", 0);

        adapter.saveAll(List.of(module));

        verify(jpaRepository).saveAll(anyList());
    }
}
