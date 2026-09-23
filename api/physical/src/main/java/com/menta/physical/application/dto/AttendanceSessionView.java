package com.menta.physical.application.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * One session row in {@code AttendanceHistoryView#sessions()} (#39, US-PHYSICAL-002, design C6).
 * {@code recordedAt} is {@code null} for {@link AttendanceStatus#ABSENT} rows (D5).
 */
public record AttendanceSessionView(
    UUID sessionId, Instant scheduledAt, String courseName, String instructorName,
    AttendanceStatus status, Instant recordedAt
) { }
