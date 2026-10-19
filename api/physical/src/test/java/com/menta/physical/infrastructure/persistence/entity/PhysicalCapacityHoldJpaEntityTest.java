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
        UUID paymentId = UUID.randomUUID();
        Instant expiresAt = Instant.now().plusSeconds(300);
        Instant convertedAt = Instant.now();
        Instant createdAt = Instant.now();

        PhysicalCapacityHoldJpaEntity entity =
            new PhysicalCapacityHoldJpaEntity(id, sessionId, paymentId, expiresAt, convertedAt, createdAt);

        assertThat(entity.getId()).isEqualTo(id);
        assertThat(entity.getSessionId()).isEqualTo(sessionId);
        assertThat(entity.getPaymentId()).isEqualTo(paymentId);
        assertThat(entity.getExpiresAt()).isEqualTo(expiresAt);
        assertThat(entity.getConvertedAt()).isEqualTo(convertedAt);
        assertThat(entity.getCreatedAt()).isEqualTo(createdAt);
    }

    @Test
    void converted_at_may_be_null_for_an_active_hold() {
        PhysicalCapacityHoldJpaEntity entity = new PhysicalCapacityHoldJpaEntity(
            UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), Instant.now().plusSeconds(300), null,
            Instant.now()
        );

        assertThat(entity.getConvertedAt()).isNull();
    }
}
