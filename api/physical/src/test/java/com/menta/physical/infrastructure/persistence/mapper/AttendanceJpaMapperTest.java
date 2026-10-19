package com.menta.physical.infrastructure.persistence.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.menta.physical.domain.model.Attendance;
import com.menta.physical.domain.model.AttendanceKind;
import com.menta.physical.domain.model.SessionId;
import com.menta.physical.infrastructure.persistence.entity.AttendanceJpaEntity;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AttendanceJpaMapperTest {

    @Test
    void maps_entity_to_domain() {
        UUID id = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Instant recordedAt = Instant.parse("2026-08-25T22:00:00Z");
        AttendanceJpaEntity entity = new AttendanceJpaEntity(
            id, sessionId, userId, recordedAt, "reader-01", "QR"
        );

        Attendance attendance = AttendanceJpaMapper.toDomain(entity);

        assertThat(attendance.getId().value()).isEqualTo(id);
        assertThat(attendance.getSessionId().getValue()).isEqualTo(sessionId);
        assertThat(attendance.getUserId()).isEqualTo(userId);
        assertThat(attendance.getRecordedAt()).isEqualTo(recordedAt);
        assertThat(attendance.getDeviceId()).isEqualTo("reader-01");
        assertThat(attendance.getKind()).isEqualTo(AttendanceKind.QR);
    }

    @Test
    void round_trips_domain_to_entity() {
        Attendance attendance = Attendance.record(
            SessionId.generate(), UUID.randomUUID(), Instant.parse("2026-08-25T22:00:00Z"),
            "reader-02", AttendanceKind.QR
        );

        AttendanceJpaEntity entity = AttendanceJpaMapper.toEntity(attendance);

        assertThat(entity.getId()).isEqualTo(attendance.getId().value());
        assertThat(entity.getSessionId()).isEqualTo(attendance.getSessionId().getValue());
        assertThat(entity.getUserId()).isEqualTo(attendance.getUserId());
        assertThat(entity.getRecordedAt()).isEqualTo(attendance.getRecordedAt());
        assertThat(entity.getDeviceId()).isEqualTo("reader-02");
        assertThat(entity.getKind()).isEqualTo("QR");
    }
}
