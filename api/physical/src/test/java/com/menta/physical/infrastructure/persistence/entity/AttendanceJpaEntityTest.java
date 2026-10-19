package com.menta.physical.infrastructure.persistence.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AttendanceJpaEntityTest {

    @Test
    void exposes_every_field_through_its_getters() {
        UUID id = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Instant recordedAt = Instant.parse("2026-08-25T22:00:00Z");

        AttendanceJpaEntity entity = new AttendanceJpaEntity(
            id, sessionId, userId, recordedAt, "reader-01", "QR"
        );

        assertThat(entity.getId()).isEqualTo(id);
        assertThat(entity.getSessionId()).isEqualTo(sessionId);
        assertThat(entity.getUserId()).isEqualTo(userId);
        assertThat(entity.getRecordedAt()).isEqualTo(recordedAt);
        assertThat(entity.getDeviceId()).isEqualTo("reader-01");
        assertThat(entity.getKind()).isEqualTo("QR");
    }
}
