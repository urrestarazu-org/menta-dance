package com.menta.physical.application.usecase;

import com.menta.physical.application.dto.AttendanceView;
import com.menta.physical.domain.model.Attendance;

final class AttendanceViewMapper {

    private AttendanceViewMapper() {
    }

    static AttendanceView toView(Attendance attendance) {
        return new AttendanceView(
            attendance.getId(),
            attendance.getSessionId(),
            attendance.getUserId(),
            attendance.getRecordedAt(),
            attendance.getDeviceId(),
            attendance.getKind()
        );
    }
}
