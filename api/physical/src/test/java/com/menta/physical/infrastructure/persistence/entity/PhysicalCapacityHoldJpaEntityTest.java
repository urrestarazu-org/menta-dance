package com.menta.physical.infrastructure.persistence.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PhysicalCapacityHoldJpaEntityTest {

    @Test
    void exposes_every_field_through_its_getters() {
        UUID id = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        Instant expiresAt = Instant.now().plusSeconds(300);
        Instant createdAt = Instant.now();

        PhysicalCapacityHoldJpaEntity entity =
            new PhysicalCapacityHoldJpaEntity(id, sessionId, expiresAt, createdAt);

        assertThat(entity.getId()).isEqualTo(id);
        assertThat(entity.getSessionId()).isEqualTo(sessionId);
        assertThat(entity.getExpiresAt()).isEqualTo(expiresAt);
        assertThat(entity.getCreatedAt()).isEqualTo(createdAt);
    }
}
