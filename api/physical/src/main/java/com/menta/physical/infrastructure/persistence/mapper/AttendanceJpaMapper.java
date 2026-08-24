package com.menta.physical.infrastructure.persistence.mapper;

import com.menta.physical.domain.model.Attendance;
import com.menta.physical.domain.model.AttendanceId;
import com.menta.physical.domain.model.AttendanceKind;
import com.menta.physical.domain.model.SessionId;
import com.menta.physical.infrastructure.persistence.entity.AttendanceJpaEntity;

/** Manual mapper JPA ↔ domain — no MapStruct (unused in this project, see #96). */
public final class AttendanceJpaMapper {

    private AttendanceJpaMapper() {
    }

    public static Attendance toDomain(AttendanceJpaEntity entity) {
        return new Attendance(
            AttendanceId.of(entity.getId()),
            SessionId.of(entity.getSessionId()),
            entity.getUserId(),
            entity.getRecordedAt(),
            entity.getDeviceId(),
            AttendanceKind.valueOf(entity.getKind())
        );
    }

    public static AttendanceJpaEntity toEntity(Attendance attendance) {
        return new AttendanceJpaEntity(
            attendance.getId().value(),
            attendance.getSessionId().getValue(),
            attendance.getUserId(),
            attendance.getRecordedAt(),
            attendance.getDeviceId(),
            attendance.getKind().name()
        );
    }
}
