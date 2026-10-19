package com.menta.physical.infrastructure.persistence.entity;

import static org.assertj.core.api.Assertions.assertThat;

import com.menta.shared.physical.AssignmentType;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PhysicalCapacityAssignmentJpaEntityTest {

    @Test
    void exposes_every_field_through_its_getters() {
        UUID id = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        UUID studentId = UUID.randomUUID();
        Instant createdAt = Instant.now();

        PhysicalCapacityAssignmentJpaEntity entity =
            new PhysicalCapacityAssignmentJpaEntity(
                id, sessionId, studentId, AssignmentType.INDIVIDUAL, createdAt
            );

        assertThat(entity.getId()).isEqualTo(id);
        assertThat(entity.getSessionId()).isEqualTo(sessionId);
        assertThat(entity.getStudentId()).isEqualTo(studentId);
        assertThat(entity.getAssignmentType()).isEqualTo(AssignmentType.INDIVIDUAL);
        assertThat(entity.getCreatedAt()).isEqualTo(createdAt);
    }
}
