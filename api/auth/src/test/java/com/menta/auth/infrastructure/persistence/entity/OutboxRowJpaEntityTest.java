package com.menta.auth.infrastructure.persistence.entity;

import static org.assertj.core.api.Assertions.assertThat;

import com.menta.shared.outbox.OutboxStatus;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class OutboxRowJpaEntityTest {

    private static final Instant CREATED_AT = Instant.parse("2026-08-16T12:00:00Z");
    private static final Instant NEXT_RETRY_AT = Instant.parse("2026-08-16T12:05:00Z");
    private static final Instant PROCESSED_AT = Instant.parse("2026-08-16T12:10:00Z");

    @Test
    void exposes_every_field_supplied_to_the_full_constructor() {
        OutboxRowJpaEntity entity = new OutboxRowJpaEntity(
            "01ARZ3NDEKTSV4RRFFQ69G5FAV", "auth.AuthUserLoggedIn", "aggregate-1", "{}",
            OutboxStatus.PENDING, 0, null, NEXT_RETRY_AT, CREATED_AT, null
        );

        assertThat(entity.getEventId()).isEqualTo("01ARZ3NDEKTSV4RRFFQ69G5FAV");
        assertThat(entity.getEventType()).isEqualTo("auth.AuthUserLoggedIn");
        assertThat(entity.getAggregateId()).isEqualTo("aggregate-1");
        assertThat(entity.getPayload()).isEqualTo("{}");
        assertThat(entity.getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(entity.getAttempts()).isZero();
        assertThat(entity.getLastError()).isNull();
        assertThat(entity.getNextRetryAt()).isEqualTo(NEXT_RETRY_AT);
        assertThat(entity.getCreatedAt()).isEqualTo(CREATED_AT);
        assertThat(entity.getProcessedAt()).isNull();
    }

    @Test
    void forceId_assigns_the_generated_pk_without_the_generator() {
        OutboxRowJpaEntity entity = new OutboxRowJpaEntity(
            "01ARZ3NDEKTSV4RRFFQ69G5FAV", "auth.AuthUserLoggedIn", "aggregate-1", "{}",
            OutboxStatus.PENDING, 0, null, null, CREATED_AT, null
        );

        entity.forceId(42L);

        assertThat(entity.getId()).isEqualTo(42L);
    }

    @Test
    void every_setter_mutates_its_matching_field() {
        OutboxRowJpaEntity entity = new OutboxRowJpaEntity(
            "01ARZ3NDEKTSV4RRFFQ69G5FAV", "auth.AuthUserLoggedIn", "aggregate-1", "{}",
            OutboxStatus.PENDING, 0, null, null, CREATED_AT, null
        );

        entity.setStatus(OutboxStatus.COMPLETED);
        entity.setAttempts(3);
        entity.setLastError("boom");
        entity.setNextRetryAt(NEXT_RETRY_AT);
        entity.setProcessedAt(PROCESSED_AT);

        assertThat(entity.getStatus()).isEqualTo(OutboxStatus.COMPLETED);
        assertThat(entity.getAttempts()).isEqualTo(3);
        assertThat(entity.getLastError()).isEqualTo("boom");
        assertThat(entity.getNextRetryAt()).isEqualTo(NEXT_RETRY_AT);
        assertThat(entity.getProcessedAt()).isEqualTo(PROCESSED_AT);
    }
}
