package com.menta.physical.infrastructure.persistence.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PhysicalSessionJpaEntityTest {

    @Test
    void exposes_every_field_through_its_getters() {
        UUID id = UUID.randomUUID();
        UUID courseId = UUID.randomUUID();
        Instant scheduledAt = Instant.parse("2026-08-25T22:00:00Z");

        PhysicalSessionJpaEntity entity = new PhysicalSessionJpaEntity(
            id, courseId, scheduledAt, 20, "SCHEDULED", "Clase especial"
        );

        assertThat(entity.getId()).isEqualTo(id);
        assertThat(entity.getCourseId()).isEqualTo(courseId);
        assertThat(entity.getScheduledAt()).isEqualTo(scheduledAt);
        assertThat(entity.getCapacity()).isEqualTo(20);
        assertThat(entity.getStatus()).isEqualTo("SCHEDULED");
        assertThat(entity.getNotes()).isEqualTo("Clase especial");
    }
}
