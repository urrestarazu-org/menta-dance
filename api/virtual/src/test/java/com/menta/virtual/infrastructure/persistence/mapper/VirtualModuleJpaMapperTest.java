package com.menta.virtual.infrastructure.persistence.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.menta.virtual.domain.model.VirtualModule;
import com.menta.virtual.infrastructure.persistence.entity.VirtualModuleJpaEntity;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class VirtualModuleJpaMapperTest {

    @Test
    void round_trips_every_field() {
        UUID id = UUID.randomUUID();
        UUID courseId = UUID.randomUUID();
        VirtualModuleJpaEntity entity = new VirtualModuleJpaEntity(id, courseId, "Módulo 1", 3);

        VirtualModule domain = VirtualModuleJpaMapper.toDomain(entity);

        assertThat(domain.getId().getValue()).isEqualTo(id);
        assertThat(domain.getCourseId().getValue()).isEqualTo(courseId);
        assertThat(domain.getTitle()).isEqualTo("Módulo 1");
        assertThat(domain.getOrder()).isEqualTo(3);

        VirtualModuleJpaEntity roundTripped = VirtualModuleJpaMapper.toEntity(domain);
        assertThat(roundTripped.getId()).isEqualTo(id);
        assertThat(roundTripped.getCourseId()).isEqualTo(courseId);
        assertThat(roundTripped.getDisplayOrder()).isEqualTo(3);
    }
}
