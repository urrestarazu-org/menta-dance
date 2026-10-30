package com.menta.android.data.remote

import kotlinx.serialization.Serializable

/**
 * Wire-level DTOs of `GET /api/v1/physical/attendance/me`. They never leave the data layer. The
 * aggregates (`attended`, `attendanceRate`, ...) and per-row extras (`status`, `courseId`, ...) are
 * skipped through `ignoreUnknownKeys`; `kind` stays a string so a new server value cannot break parsing.
 */
@Serializable
internal data class AttendanceHistoryResponseDto(val sessions: List<AttendanceSessionDto>)

@Serializable
internal data class AttendanceSessionDto(
    val sessionId: String,
    val courseName: String,
    val instructorName: String,
    val scheduledAt: String,
    val recordedAt: String? = null,
    val kind: String? = null,
)
