package com.menta.physical.infrastructure.web.dto;

import com.menta.physical.application.dto.AttendanceSessionView;
import java.time.Instant;
import java.util.UUID;

/** #39, US-PHYSICAL-002, design C6 — one shared shape for both controllers. */
public record AttendanceSessionResponse(
    UUID sessionId, Instant scheduledAt, String courseName, String instructorName,
    String status, Instant recordedAt
) {

    public static AttendanceSessionResponse from(AttendanceSessionView view) {
        return new AttendanceSessionResponse(
            view.sessionId(), view.scheduledAt(), view.courseName(), view.instructorName(),
            view.status().name(), view.recordedAt()
        );
    }
}
