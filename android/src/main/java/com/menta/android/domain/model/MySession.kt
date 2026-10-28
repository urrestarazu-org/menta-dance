package com.menta.android.domain.model

import java.time.Instant

/** An upcoming in-person session of the student; [checkInOpen] is a snapshot of the query instant. */
data class MySession(
    val sessionId: String,
    val courseName: String,
    val instructorName: String,
    val scheduledAt: Instant,
    val checkInOpen: Boolean,
)
