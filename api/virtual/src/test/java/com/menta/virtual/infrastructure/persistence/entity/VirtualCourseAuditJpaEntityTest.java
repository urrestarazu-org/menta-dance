package com.menta.virtual.infrastructure.persistence.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class VirtualCourseAuditJpaEntityTest {

    @Test
    void exposes_every_field_through_its_getters() {
        UUID id = UUID.randomUUID();
        UUID courseId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        Instant createdAt = Instant.now();

        VirtualCourseAuditJpaEntity entity = new VirtualCourseAuditJpaEntity(
            id, courseId, actorId, "CREATE_COURSE", null, "title=t", createdAt
        );

        assertThat(entity.getId()).isEqualTo(id);
        assertThat(entity.getCourseId()).isEqualTo(courseId);
        assertThat(entity.getActorId()).isEqualTo(actorId);
        assertThat(entity.getAction()).isEqualTo("CREATE_COURSE");
        assertThat(entity.getPreviousValue()).isNull();
        assertThat(entity.getNewValue()).isEqualTo("title=t");
        assertThat(entity.getCreatedAt()).isEqualTo(createdAt);
    }
}
