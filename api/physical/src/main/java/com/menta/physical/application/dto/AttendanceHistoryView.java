package com.menta.physical.application.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * The full response of {@code GetPhysicalAttendanceHistoryUseCase#history} for one calendar
 * month (#39, US-PHYSICAL-002, design C6). {@code scheduledSessionCount}, {@code attended},
 * {@code absent} and {@code attendanceRate} are always computed over the FULL assignment set —
 * {@code includeAbsent} only filters {@code sessions()} (D4), never these aggregates.
 */
public record AttendanceHistoryView(
    String period, int scheduledSessionCount, int attended, int absent,
    BigDecimal attendanceRate, List<AttendanceSessionView> sessions
) { }
