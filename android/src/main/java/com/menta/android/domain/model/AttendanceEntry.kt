package com.menta.android.domain.model

import java.time.Instant

/** A class the student attended; [recordedAt] is the check-in instant. */
data class AttendanceEntry(
    val sessionId: String,
    val courseName: String,
    val instructorName: String,
    val scheduledAt: Instant,
    val recordedAt: Instant,
    val origin: AttendanceOrigin,
)
