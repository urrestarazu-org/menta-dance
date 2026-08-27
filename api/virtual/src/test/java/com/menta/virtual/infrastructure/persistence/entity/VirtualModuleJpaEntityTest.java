package com.menta.virtual.infrastructure.persistence.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class VirtualModuleJpaEntityTest {

    @Test
    void exposes_every_field_through_its_getters() {
        UUID id = UUID.randomUUID();
        UUID courseId = UUID.randomUUID();

        VirtualModuleJpaEntity entity = new VirtualModuleJpaEntity(id, courseId, "Módulo 1", true, 2);

        assertThat(entity.getId()).isEqualTo(id);
        assertThat(entity.getCourseId()).isEqualTo(courseId);
        assertThat(entity.getTitle()).isEqualTo("Módulo 1");
        assertThat(entity.isPreview()).isTrue();
        assertThat(entity.getDisplayOrder()).isEqualTo(2);
    }
}
