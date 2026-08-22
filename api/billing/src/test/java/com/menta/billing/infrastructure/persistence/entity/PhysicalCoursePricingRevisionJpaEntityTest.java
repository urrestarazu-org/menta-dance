package com.menta.billing.infrastructure.persistence.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PhysicalCoursePricingRevisionJpaEntityTest {

    @Test
    void exposes_every_field_through_its_getters() {
        UUID id = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        Instant occurredAt = Instant.now();

        PhysicalCoursePricingRevisionJpaEntity entity = new PhysicalCoursePricingRevisionJpaEntity(
            id, "course-1", actorId, "ajuste de temporada", 2, "previous", "new", occurredAt
        );

        assertThat(entity.getId()).isEqualTo(id);
        assertThat(entity.getCourseId()).isEqualTo("course-1");
        assertThat(entity.getActorId()).isEqualTo(actorId);
        assertThat(entity.getReason()).isEqualTo("ajuste de temporada");
        assertThat(entity.getVersion()).isEqualTo(2);
        assertThat(entity.getPreviousValue()).isEqualTo("previous");
        assertThat(entity.getNewValue()).isEqualTo("new");
        assertThat(entity.getOccurredAt()).isEqualTo(occurredAt);
    }

    @Test
    void previous_value_may_be_null_for_a_first_version() {
        PhysicalCoursePricingRevisionJpaEntity entity = new PhysicalCoursePricingRevisionJpaEntity(
            UUID.randomUUID(), "course-1", UUID.randomUUID(), "motivo", 1, null, "new", Instant.now()
        );

        assertThat(entity.getPreviousValue()).isNull();
    }
}
