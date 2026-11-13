package com.menta.physical.application.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * The full response of {@code GetPhysicalAttendanceHistoryUseCase#history} for the half-open instant
 * range {@code [from, to)} it echoes (#39, #729, US-PHYSICAL-002, design C6).
 * {@code scheduledSessionCount}, {@code attended}, {@code absent} and {@code attendanceRate} are
 * always computed over the FULL assignment set —
 * {@code includeAbsent} only filters {@code sessions()} (D4), never these aggregates.
 */
public record AttendanceHistoryView(
    Instant from, Instant to, int scheduledSessionCount, int attended, int absent,
    BigDecimal attendanceRate, List<AttendanceSessionView> sessions
) {}
