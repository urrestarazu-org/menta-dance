package com.menta.physical.infrastructure.web.dto;

import com.menta.physical.application.dto.AttendanceSessionView;
import java.time.Instant;
import java.util.UUID;

/** #39, US-PHYSICAL-002, design C6 — one shared shape for both controllers. */
public record AttendanceSessionResponse(
    UUID sessionId, Instant scheduledAt, String courseName, String instructorName,
    String status, Instant recordedAt, String kind
) {

    /**
     * Maps one session view, rendering its status and, when present, its check-in origin by enum
     * name. {@code kind} stays an explicit {@code null} for absent rows, like {@code recordedAt}.
     */
    public static AttendanceSessionResponse from(AttendanceSessionView view) {
        return new AttendanceSessionResponse(
            view.sessionId(), view.scheduledAt(), view.courseName(), view.instructorName(),
            view.status().name(), view.recordedAt(), view.kind() == null ? null : view.kind().name()
        );
    }
}
