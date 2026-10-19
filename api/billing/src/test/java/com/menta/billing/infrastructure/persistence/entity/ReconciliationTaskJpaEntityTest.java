package com.menta.billing.infrastructure.persistence.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ReconciliationTaskJpaEntityTest {

    @Test
    void exposes_every_field_through_its_getters() {
        UUID id = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        Instant createdAt = Instant.now();

        ReconciliationTaskJpaEntity entity =
            new ReconciliationTaskJpaEntity(id, paymentId, "mp-1", "mismatch", createdAt);

        assertThat(entity.getId()).isEqualTo(id);
        assertThat(entity.getPaymentId()).isEqualTo(paymentId);
        assertThat(entity.getProviderPaymentId()).isEqualTo("mp-1");
        assertThat(entity.getReason()).isEqualTo("mismatch");
        assertThat(entity.getCreatedAt()).isEqualTo(createdAt);
    }

    @Test
    void allows_a_null_payment_id_when_no_local_payment_matches() {
        ReconciliationTaskJpaEntity entity =
            new ReconciliationTaskJpaEntity(UUID.randomUUID(), null, "mp-2", "no local payment", Instant.now());

        assertThat(entity.getPaymentId()).isNull();
    }
}
